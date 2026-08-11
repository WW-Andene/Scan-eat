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
    val included: Boolean = true,
)

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
                val knownKeys = priceRepo.observeAll(activeProfileId.value).first()
                    .map { normalizeKey(it.productName) }.toSet()
                parsed.map { line ->
                    ReceiptReviewLine(
                        id = UUID.randomUUID().toString(),
                        name = line.rawName,
                        priceEuros = line.priceEuros,
                        matched = normalizeKey(line.rawName) in knownKeys,
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

    /** Logs every included line as its own price_log purchase - same shape addEntry()/logPrice() elsewhere already write. */
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
            }.onSuccess { _state.value = ReceiptScanState.Done }
                .onFailure { e -> if (e is CancellationException) throw e; _state.value = ReceiptScanState.Error }
        }
    }
}
