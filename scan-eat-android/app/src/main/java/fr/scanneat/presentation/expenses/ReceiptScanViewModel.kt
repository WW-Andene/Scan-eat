package fr.scanneat.presentation.expenses

import android.util.Base64
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.domain.engine.expense.parseReceiptLines
import fr.scanneat.domain.engine.planning.normalizeKey
import fr.scanneat.domain.model.ProductCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One reviewable line in the post-OCR receipt review list. */
data class ReceiptReviewLine(
    val id: String,
    val name: String,
    val priceEuros: Double,
    // True only when [name] (normalized) exactly matches a product name
    // already in this profile's own price history - user-requested "100%
    // match only", no fuzzy/substring matching. Purely informational (a
    // small badge in the review UI) - matching or not, the line still logs
    // under its own OCR'd name either way.
    val matched: Boolean,
    // User-requested: when [matched] is false, up to 3 product names from
    // this profile's own price history that could plausibly be the same
    // product under a different spelling (whole-word overlap either
    // direction, see suggestionsFor()) - a supposition offered to pick from,
    // never applied automatically. Empty when matched (nothing to suggest)
    // or when nothing plausible was found.
    val suggestions: List<String> = emptyList(),
    val included: Boolean = true,
)

/** Whole-word containment on normalizeKey()'d strings - same guard GroceryViewModel's
 *  containsWholeWord uses so "riz" doesn't match inside "chorizo". */
private fun containsWholeWord(haystack: String, needle: String): Boolean {
    if (needle.isBlank()) return false
    return Regex("(?<![a-z0-9])${Regex.escape(needle)}(?![a-z0-9])").containsMatchIn(haystack)
}

/**
 * Up to 3 known product names that share at least one whole word with
 * [ocrName] (either direction - a receipt line "PETIT NAVIRE THON" should
 * surface a logged "Thon albacore Petit Navire", and vice versa), ranked by
 * the length of the longest shared word (a longer overlap is a stronger
 * signal than a short common word like "lait" alone). This is a supposition
 * shown for the user to confirm or dismiss - never applied automatically.
 */
private fun suggestionsFor(ocrName: String, knownNames: List<String>): List<String> {
    val ocrKey = normalizeKey(ocrName)
    val ocrWords = ocrKey.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
    if (ocrWords.isEmpty()) return emptyList()
    return knownNames
        .mapNotNull { known ->
            val knownKey = normalizeKey(known)
            val bestSharedWordLen = ocrWords.filter { containsWholeWord(knownKey, it) }.maxOfOrNull { it.length } ?: 0
            if (bestSharedWordLen == 0) null else known to bestSharedWordLen
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .take(3)
}

sealed interface ReceiptScanState {
    data object Idle : ReceiptScanState
    data object Processing : ReceiptScanState
    data class Review(val lines: List<ReceiptReviewLine>) : ReceiptScanState
    data object NoLinesFound : ReceiptScanState
    data object Error : ReceiptScanState
    data object Done : ReceiptScanState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiptScanViewModel @Inject constructor(
    private val priceRepo: PriceRepository,
    private val prefs: UserPreferences,
    private val manualGroceryRepo: ManualGroceryRepository,
    private val checkedRepo: GroceryCheckedRepository,
) : ViewModel() {
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    private val _state = MutableStateFlow<ReceiptScanState>(ReceiptScanState.Idle)
    val state: StateFlow<ReceiptScanState> = _state.asStateFlow()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** [base64] is the JPEG CameraPreview's shutter capture already produced (see ImagePayload). */
    fun processImage(base64: String) {
        _state.value = ReceiptScanState.Processing
        viewModelScope.launch {
            runCatching {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: error("undecodable image")
                val visionText = suspendCancellableCoroutine<String> { cont ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { cont.resume(it.text) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                val parsed = parseReceiptLines(visionText)
                // Distinct raw names (not just keys) - suggestions need the original
                // display spelling to show/apply, the normalized key is only used to compare.
                val knownNames = priceRepo.observeAll(activeProfileId.value).first()
                    .map { it.productName }.distinctBy { normalizeKey(it) }
                val knownKeys = knownNames.map { normalizeKey(it) }.toSet()
                parsed.map { line ->
                    val exactMatch = normalizeKey(line.rawName) in knownKeys
                    ReceiptReviewLine(
                        id = UUID.randomUUID().toString(),
                        name = line.rawName,
                        priceEuros = line.priceEuros,
                        matched = exactMatch,
                        suggestions = if (exactMatch) emptyList() else suggestionsFor(line.rawName, knownNames),
                    )
                }
            }.onSuccess { lines ->
                _state.value = if (lines.isEmpty()) ReceiptScanState.NoLinesFound else ReceiptScanState.Review(lines)
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.value = ReceiptScanState.Error
            }
        }
    }

    fun applySuggestion(id: String, suggestion: String) {
        val current = _state.value as? ReceiptScanState.Review ?: return
        _state.value = current.copy(lines = current.lines.map {
            if (it.id == id) it.copy(name = suggestion, matched = true, suggestions = emptyList()) else it
        })
    }

    fun updateLine(id: String, name: String, priceEuros: Double) {
        val current = _state.value as? ReceiptScanState.Review ?: return
        _state.value = current.copy(lines = current.lines.map {
            if (it.id == id) it.copy(name = name, priceEuros = priceEuros) else it
        })
    }

    fun toggleIncluded(id: String) {
        val current = _state.value as? ReceiptScanState.Review ?: return
        _state.value = current.copy(lines = current.lines.map {
            if (it.id == id) it.copy(included = !it.included) else it
        })
    }

    fun retry() { _state.value = ReceiptScanState.Idle }

    /**
     * Logs every included line as its own price_log purchase - same shape
     * addEntry()/logPrice() elsewhere already write. User-requested: also
     * checks off any Courses manual item (any list, this profile) whose
     * normalized name shares a whole word with a confirmed line - a best-
     * effort link, not a guaranteed match (same containsWholeWord heuristic
     * ReceiptScanViewModel's own suggestionsFor uses), so a receipt scan
     * actually finishes the "buy this" loop instead of only recording the
     * price with the shopping list left untouched.
     */
    fun confirmAll() {
        val current = _state.value as? ReceiptScanState.Review ?: return
        val toLog = current.lines.filter { it.included && it.name.isNotBlank() && it.priceEuros > 0.0 }
        viewModelScope.launch {
            runCatching {
                toLog.forEach { line ->
                    priceRepo.log(
                        date = LocalDate.now(),
                        productName = line.name,
                        barcode = null,
                        category = ProductCategory.OTHER,
                        priceEuros = line.priceEuros,
                        weightG = null,
                        profileId = activeProfileId.value,
                    )
                }
                val ocrKeys = toLog.map { normalizeKey(it.name) }
                manualGroceryRepo.items(activeProfileId.value).first().forEach { item ->
                    val itemKey = normalizeKey(item.name)
                    if (ocrKeys.any { containsWholeWord(itemKey, it) || containsWholeWord(it, itemKey) }) {
                        checkedRepo.setChecked(itemKey, true, activeProfileId.value)
                    }
                }
            }.onSuccess { _state.value = ReceiptScanState.Done }
                .onFailure { e -> if (e is CancellationException) throw e; _state.value = ReceiptScanState.Error }
        }
    }
}
