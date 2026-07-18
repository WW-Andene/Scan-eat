package fr.scanneat.presentation.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.scan.ProductNotFoundException
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.medication.MedicationDbEntry
import fr.scanneat.domain.engine.medication.findMedicationByBarcode
import fr.scanneat.domain.engine.medication.findMedicationByName
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByBarcode
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByName
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Reaches the user verbatim in the scan error banner — needs to actually say what to do. */
private fun invalidApiKeyMessage(lang: String) =
    if (lang == "en") "Groq rejected this API key — check it in Settings"
    else "Clé API Groq refusée — vérifiez-la dans Réglages"

/**
 * Groq model names get deprecated/retired periodically (the pinned
 * DEFAULT_MODEL/FALLBACK_MODEL are compile-time literals) - when that
 * happens Groq returns a 400/404 for the model, not the more common
 * 401/403/429/5xx this app already has friendly messages for, and it'd
 * otherwise surface as a bare HTTP error with no indication that the fix
 * is just picking a current model in Settings.
 */
private fun invalidModelMessage(lang: String) =
    if (lang == "en") "This AI model is no longer available — pick a current one in Settings"
    else "Ce modèle IA n'est plus disponible — choisissez-en un à jour dans Réglages"

/**
 * OcrParser already retries a 429 internally (see isRetryable), so reaching
 * this branch means every retry was also rate-limited — a transient-but-
 * persistent state distinct from the other HTTP error branches, which this
 * file previously had no message for despite invalidModelMessage's own
 * comment claiming 429 already had a friendly message.
 */
private fun rateLimitedMessage(lang: String) =
    if (lang == "en") "Groq is rate-limiting requests right now — wait a moment and try again"
    else "Groq limite les requêtes en ce moment — patientez un instant puis réessayez"

private fun noInputMessage(lang: String) =
    if (lang == "en") "Scan a barcode or take a photo"
    else "Scannez un code-barres ou prenez une photo"

/**
 * identifyFromPhotos() previously reused noInputMessage() for its offline branch
 * even though photos are guaranteed present there (the function early-returns on
 * an empty queue before this check) - a user with photos already queued, offline,
 * trying to identify unlabeled produce, got told to "scan a barcode or take a
 * photo" instead of the real, actionable problem. Mirrors ScanRepository's own
 * private offlineMessage(), which every barcode/label-parsing path already uses.
 */
private fun offlineMessage(lang: String) =
    if (lang == "en") "No internet connection" else "Pas de connexion internet"

/**
 * Fallback for the `else` branch of the scan-failure `when` — every sibling
 * branch (invalidApiKeyMessage/invalidModelMessage/rateLimitedMessage/
 * noInputMessage) routes through lang, but this default case used to hardcode
 * the bare French literal "Erreur inconnue", so English-language users hit a
 * French message whenever an unrecognized exception surfaced.
 */
private fun genericErrorMessage(lang: String) =
    if (lang == "en") "Unknown error"
    else "Erreur inconnue"

sealed class ScanUiState {
    data object Idle     : ScanUiState()
    data object Scanning : ScanUiState()
    data class  Success(val result: ScanResult, val persistedId: Long) : ScanUiState()
    data class  Error(val message: String, val needsPhoto: Boolean = false) : ScanUiState()
    /** Barcode matched the medication lookup DB instead of a food product - offer to save it to Traitement rather than running it through food scoring. */
    data class  MedicationFound(val entry: MedicationDbEntry) : ScanUiState()
    /** Barcode matched a household/chemical product - not something to run through food scoring, and never something to imply is safe to consume. */
    data class  NonConsumableFound(val entry: NonConsumableDbEntry) : ScanUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,       // Fix 15/21: read language from preferences
    private val connectivityManager: ConnectivityManager,
    private val medicationRepo: MedicationRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // Needed so the MedicationFound/NonConsumableFound dialogs can render their
    // hint text (see MedicationSubstanceDb/NonConsumableHints) in the user's
    // in-app language rather than always defaulting to French.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Lets the MedicationFound dialog cross-reference the drug's class against
    // e.g. pregnancy/kidney_disease the same way PersonalScoreEngine already
    // personalizes the food score for those same profile.healthConditions.
    val healthConditions: StateFlow<Set<String>> = prefs.profile
        .map { it.healthConditions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _images = MutableStateFlow<List<ImagePayload>>(emptyList())
    val images: StateFlow<List<ImagePayload>> = _images.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    /**
     * "Already scanned this" preview — ScanRepository.getCachedByBarcode already
     * exists and is indexed (scoreBarcode() uses it internally to skip the
     * network on a rescan), but nothing surfaced that "already known" fact to
     * the user before this. Lets ScanScreen show a grade/score badge the
     * instant a familiar barcode enters frame, ahead of the user tapping the
     * score FAB at all.
     */
    val cachedPreview: StateFlow<ScanResult?> = _scannedBarcode
        .flatMapLatest { barcode -> flow { emit(barcode?.let { scanRepo.getCachedByBarcode(it) }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _recentBarcodes = MutableStateFlow<List<String>>(emptyList())
    val recentBarcodes: StateFlow<List<String>> = _recentBarcodes.asStateFlow()

    private val scoreMutex = Mutex()

    // New: how many products scanned today — drives the session counter badge in the
    // scan header. Backed by a live Room query so it updates immediately after each
    // successful scan without any manual increment in the ViewModel.
    val todayScanCount: StateFlow<Int> = scanRepo.observeTodayScanCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _instantMode = MutableStateFlow(false)
    val instantMode: StateFlow<Boolean> = _instantMode.asStateFlow()

    fun toggleInstantMode() { _instantMode.value = !_instantMode.value }

    fun onBarcodeDetected(barcode: String) {
        // Previously only guarded Scanning — while a MedicationFound/NonConsumableFound/
        // Success dialog was still up (state hadn't been reset yet), instant mode kept
        // calling score() for every frame underneath it, racing the visible dialog and
        // eating the next real detection. Any non-Idle/Error state means a result is
        // already being shown or produced, so new detections must wait.
        if (_state.value !is ScanUiState.Idle && _state.value !is ScanUiState.Error) return
        // Once the user has started building a photo queue, a *new* barcode
        // appearing in frame is almost always incidental (background clutter,
        // a neighboring product swept past while framing the next shot) rather
        // than a deliberate re-aim - score() prefers a held barcode over the
        // photo queue whenever one is set (so a barcode detected first can be
        // *augmented* with follow-up photos when OFF's entry for it is sparse),
        // so silently adopting a new incidental one here hijacked the eventual
        // Score tap into a barcode lookup for a product the user never meant to
        // scan, completely ignoring the photos they'd just taken. A barcode
        // already held before any photo was taken (the deliberate combo flow)
        // is untouched - this only blocks picking up a *new* one afterward.
        if (_images.value.isNotEmpty()) return
        if (_scannedBarcode.value == barcode) return
        _scannedBarcode.value = barcode
        if (_instantMode.value) score()
    }

    /**
     * Called when the camera frame no longer contains any barcode (analyzeFrame's
     * onBoundsCleared). Without this, _scannedBarcode stuck around forever after the
     * first detection — leaving the frame and pointing at a *different* product just
     * showed the old code's label/name until the new code happened to differ from it,
     * and switching tabs and back kept the stale code alive too. Only clears the
     * held code, never touches an in-flight/completed scan (Scanning/Success/error
     * dialogs), so it can't interrupt an active lookup.
     */
    fun onBarcodeLost() {
        if (_state.value is ScanUiState.Idle) _scannedBarcode.value = null
    }

    /**
     * Called once ScanScreen has consumed a Success state (navigated to the result
     * screen). Previously _state stayed Success forever — simply switching tabs away
     * from Scan and back re-triggered ScanScreen's `LaunchedEffect(state.value)` with
     * the same Success value still current, firing onResultReady again and stacking
     * another result screen on the back stack every time.
     */
    fun resultConsumed() {
        _scannedBarcode.value = null
        _state.value = ScanUiState.Idle
    }

    /**
     * Capped at MAX_QUEUED_PHOTOS — previously unbounded, unlike the server's own
     * RouteHelpers.normalizeImages() (MAX_IMAGES=8, added round 30). A user tapping
     * the capture FAB repeatedly grew this list forever: in SERVER mode the extra
     * images were uploaded over the network only to be silently dropped server-side,
     * and in DIRECT mode (straight to Groq/Cerebras, no server-side cap at all) every
     * queued photo's full base64 payload was sent to the vision LLM with no limit.
     * Silently ignored past the cap rather than surfaced as an error, matching this
     * screen's other silent caps (e.g. recentBarcodes.takeLast(5)).
     */
    fun addPhoto(bitmap: Bitmap) {
        if (_images.value.size >= MAX_QUEUED_PHOTOS) return
        _images.value = _images.value + bitmap.toPayload()
    }

    fun removePhoto(index: Int) {
        _images.value = _images.value.toMutableList().also { it.removeAt(index) }
    }

    fun clearQueue() {
        _images.value = emptyList()
        _scannedBarcode.value = null
        _state.value = ScanUiState.Idle
    }

    /**
     * Identifies whatever is in photo(s) with no barcode/DataMatrix/QR to
     * scan — a medication or household product without any machine-readable
     * code, or fresh produce / a plated dish. Previously this always assumed
     * food (scoreFromImages' identifyMode), which would score a medication box
     * as if it were something to eat. Checks the identified product's name
     * against the medication/non-consumable name-lookup DBs before treating
     * it as food, same priority order as the barcode path in score() below -
     * but via a single vision-LLM call (identifyOrScoreFromImages), not a
     * separate identifyProductName call followed by a second, near-identical
     * identifyFood call for the same photos whenever neither DB matched (the
     * common case: fresh produce, plated dishes never match either lookup).
     */
    fun identifyFromPhotos() {
        val imgs = _images.value
        if (imgs.isEmpty()) return
        if (!scoreMutex.tryLock()) return
        viewModelScope.launch {
            try {
                _state.value = ScanUiState.Scanning
                val lang   = prefs.language.first()
                val online = isOnline()
                if (!online) {
                    _state.value = ScanUiState.Error(offlineMessage(lang))
                    return@launch
                }
                val identified = scanRepo.identifyOrScoreFromImages(imgs, lang, online, identifyMode = true)
                identified.fold(
                    onSuccess = { scanResult ->
                        val name = scanResult.product.name
                        val medication = withContext(Dispatchers.IO) { findMedicationByName(appContext, name) }
                        val nonConsumable = if (medication == null) {
                            withContext(Dispatchers.IO) { findNonConsumableByName(appContext, name) }
                        } else null
                        when {
                            medication != null -> _state.value = ScanUiState.MedicationFound(medication)
                            nonConsumable != null -> _state.value = ScanUiState.NonConsumableFound(nonConsumable)
                            else -> {
                                val id = scanRepo.persist(scanResult)
                                _state.value = ScanUiState.Success(scanResult, id)
                            }
                        }
                    },
                    onFailure = { e -> _state.value = ScanUiState.Error(e.message ?: genericErrorMessage(lang)) },
                )
            } finally {
                scoreMutex.unlock()
            }
        }
    }

    fun score() {
        val barcode = _scannedBarcode.value
        val imgs    = _images.value
        if (barcode == null && imgs.isEmpty()) {
            // Every other branch in this file threads lang through - this one was a
            // bare French literal, so English-language users hit it in French.
            viewModelScope.launch { _state.value = ScanUiState.Error(noInputMessage(prefs.language.first())) }
            return
        }
        if (!scoreMutex.tryLock()) return   // already scoring — ignore double-tap
        viewModelScope.launch {
            try {
                // A medication barcode run through food scoring produces a meaningless
                // nutrition-based grade - check the (asset-backed, ~12k-entry) medication
                // lookup DB first and short-circuit into a distinct "save to Traitement?"
                // path. Parsing the backing CSV is real file IO, so it runs off the main
                // thread rather than blocking the tap that triggered score().
                if (barcode != null) {
                    withContext(Dispatchers.IO) { findMedicationByBarcode(appContext, barcode) }?.let { entry ->
                        _state.value = ScanUiState.MedicationFound(entry)
                        return@launch
                    }
                    withContext(Dispatchers.IO) { findNonConsumableByBarcode(appContext, barcode) }?.let { entry ->
                        _state.value = ScanUiState.NonConsumableFound(entry)
                        return@launch
                    }
                }
                _state.value = ScanUiState.Scanning
                val lang   = prefs.language.first()    // Fix 15/21: thread language into scan
                val online = isOnline()
                // A barcode already scanned before is served from the local cache
                // and needs no connection — only a real lookup requires one, so
                // the connectivity check happens inside the repo, after the cache
                // read, instead of blocking every scan up front.
                val result = if (barcode != null) {
                    scanRepo.scoreBarcode(barcode, imgs, lang, online)
                } else {
                    scanRepo.scoreFromImages(imgs, lang, online)
                }
                result.fold(
                    onSuccess = { (scanResult, id) ->
                        _state.value = ScanUiState.Success(scanResult, id)
                        if (barcode != null) {
                            _recentBarcodes.value = (_recentBarcodes.value - barcode + barcode)
                                .distinct().takeLast(5)
                        }
                    },
                    onFailure = { e ->
                        _state.value = when {
                            e is ProductNotFoundException ->
                                ScanUiState.Error(e.message ?: "Produit introuvable", needsPhoto = true)
                            // A rejected API key (invalid/revoked, not just missing — that
                            // case is already caught earlier as a friendly message) would
                            // otherwise surface as a bare "HTTP 401 " to the user with no
                            // indication of what to actually do about it.
                            e is HttpException && (e.code() == 401 || e.code() == 403) ->
                                ScanUiState.Error(invalidApiKeyMessage(lang))
                            e is HttpException && (e.code() == 400 || e.code() == 404) ->
                                ScanUiState.Error(invalidModelMessage(lang))
                            e is HttpException && e.code() == 429 ->
                                ScanUiState.Error(rateLimitedMessage(lang))
                            else -> ScanUiState.Error(e.message ?: genericErrorMessage(lang))
                        }
                    },
                )
            } finally {
                scoreMutex.unlock()
            }
        }
    }

    fun quickScan(barcode: String) {
        if (_state.value is ScanUiState.Scanning) return
        _scannedBarcode.value = barcode
        score()
    }

    fun dismissError() { _state.value = ScanUiState.Idle }

    /**
     * Dismisses a MedicationFound/NonConsumableFound dialog without saving it -
     * distinct from dismissError() because it also clears the photo queue and any
     * held barcode, matching saveDetectedMedication()'s confirm path below.
     * dismissError() deliberately leaves the queue alone (needsPhoto errors rely
     * on the queued photos surviving so the user can add one and retry), but a
     * rejected medication/non-consumable match means "this isn't the thing to
     * score as food" - without this, the queued photos/barcode from that match
     * silently carried over into the user's next, unrelated scan attempt.
     */
    fun dismissFound() { clearQueue() }

    /** Confirms saving a detected medication (ScanUiState.MedicationFound) into Traitement. */
    fun saveDetectedMedication(entry: MedicationDbEntry) {
        viewModelScope.launch {
            medicationRepo.save(name = entry.name, dosage = entry.form, barcode = entry.barcode)
            clearQueue()
        }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Photo-queue thumbnails render in a 64.dp box, but the capture itself is
     * 1600×1200 — holding that full bitmap per queued photo (~7.7MB each,
     * ARGB_8888) risks real memory pressure with more than one or two photos
     * queued. Scale down (aspect-preserving) for display.
     *
     * The upload bytes are also capped, separately from the thumbnail - the
     * vision model needs enough resolution to read label text, not the raw
     * capture resolution, and every uncapped photo was previously shipped to
     * Groq (or proxied through server mode) at full 1600×1200 for no OCR
     * accuracy benefit, just wasted bandwidth/tokens.
     */
    private fun Bitmap.toPayload(): ImagePayload {
        val uploadScale = UPLOAD_MAX_PX.toFloat() / maxOf(width, height)
        val uploadBitmap = if (uploadScale < 1f) {
            Bitmap.createScaledBitmap(this, (width * uploadScale).toInt(), (height * uploadScale).toInt(), true)
        } else this

        val out = ByteArrayOutputStream()
        uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)

        val thumbScale = THUMBNAIL_MAX_PX.toFloat() / maxOf(width, height)
        val thumb = if (thumbScale < 1f) {
            Bitmap.createScaledBitmap(this, (width * thumbScale).toInt(), (height * thumbScale).toInt(), true)
        } else this

        if (uploadBitmap !== this) uploadBitmap.recycle()
        if (thumb !== this) recycle()

        return ImagePayload(
            base64    = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            thumbnail = thumb,
        )
    }

    private companion object {
        const val THUMBNAIL_MAX_PX = 160
        const val UPLOAD_MAX_PX = 1024   // ample for label OCR - well under the raw 1600x1200 capture
        /** Mirrors the server's RouteHelpers.MAX_IMAGES - see addPhoto()'s doc comment. */
        const val MAX_QUEUED_PHOTOS = 8
    }
}
