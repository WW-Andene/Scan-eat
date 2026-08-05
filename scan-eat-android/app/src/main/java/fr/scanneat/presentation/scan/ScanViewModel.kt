package fr.scanneat.presentation.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.scan.NonFoodProductException
import fr.scanneat.data.repository.scan.ProductNotFoundException
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.medication.MedicationDbEntry
import fr.scanneat.domain.engine.medication.findMedicationByBarcode
import fr.scanneat.domain.engine.nonconsumable.NonConsumableCategory
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByBarcode
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.util.extractPriceFromText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScanViewModel @Inject constructor(
    internal val scanRepo: ScanRepository,
    internal val prefs: UserPreferences,       // Fix 15/21: read language from preferences
    private val connectivityManager: ConnectivityManager,
    internal val medicationRepo: MedicationRepository,
    private val priceRepo: PriceRepository,
    @ApplicationContext internal val appContext: Context,
) : ViewModel() {

    internal val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
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

    internal val _images = MutableStateFlow<List<ImagePayload>>(emptyList())
    val images: StateFlow<List<ImagePayload>> = _images.asStateFlow()

    // Disk-backed cache of the photo queue - a ViewModel is a fresh instance after
    // process death (only config-change survives it, not this), so the in-memory
    // _images StateFlow above was previously the only copy: backgrounding mid
    // multi-photo shelf/scan capture under memory pressure silently discarded every
    // queued photo with no way back, forcing the whole capture sequence to be redone.
    // filesDir (not cacheDir) - this represents real, deliberately-captured user
    // input the OS shouldn't be free to opportunistically wipe under storage
    // pressure the way it can with cacheDir, even though it's still transient data
    // this ViewModel itself deletes once consumed (see persistQueue()'s callers).
    // One newline-delimited base64 string per photo - base64 (NO_WRAP) never
    // contains a newline itself, so no escaping/JSON parsing is needed.
    init {
        // Best-effort restore, off Main - see queueFile's own doc comment (ScanPhotoQueue.kt).
        // Wrapped in runCatching (not just relying on individual restoreImagePayload() null
        // returns) so a corrupt/unreadable cache file can never crash the app on
        // launch, only silently lose the recovery opportunity.
        restorePhotoQueue()
    }

    internal val _scannedBarcode = MutableStateFlow<String?>(null)
    // Debounce state for onBarcodeDetected() - see its own doc comment for why
    // this exists (two barcodes simultaneously in frame).
    internal var pendingBarcode: String? = null
    internal var pendingBarcodeStreak = 0
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    // User-requested: a price displayed right next to the barcode, read via
    // on-device OCR at the same moment the barcode itself is scanned (see
    // CameraPreview's onPriceTextDetected) - first hit wins per barcode
    // session (reset alongside _scannedBarcode below) rather than the last
    // one, since a shaky hand sweeping the price tag out of frame after a
    // good read shouldn't clobber it with a garbled partial one. Auto-logged
    // into PriceRepository once score() succeeds (see its own doc comment) -
    // shows up in PriceEntryCard/Expenses exactly like a manual entry, so
    // it's edited/deleted the same way if wrong, never a silent, unfixable value.
    private val _detectedPriceEuros = MutableStateFlow<Double?>(null)
    val detectedPriceEuros: StateFlow<Double?> = _detectedPriceEuros.asStateFlow()

    fun onPriceTextDetected(text: String) {
        if (_detectedPriceEuros.value != null) return
        if (_scannedBarcode.value == null) return
        extractPriceFromText(text)?.let { _detectedPriceEuros.value = it }
    }

    /**
     * "Already scanned this" preview — ScanRepository.getCachedByBarcode already
     * exists and is indexed (scoreBarcode() uses it internally to skip the
     * network on a rescan), but nothing surfaced that "already known" fact to
     * the user before this. Lets ScanScreen show a grade/score badge the
     * instant a familiar barcode enters frame, ahead of the user tapping the
     * score FAB at all.
     */
    val cachedPreview: StateFlow<ScanResult?> = _scannedBarcode
        .flatMapLatest { barcode -> flow { emit(barcode?.let { scanRepo.getCachedByBarcode(it, lang = language.value) }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Same allergen/diet warning already surfaced on History/Dashboard/Diary/
    // MealPlan/Grocery/Recipes/Templates (see e.g. ScanHistoryViewModel.historyWarnings) -
    // this "already scanned" preview chip was the one remaining screen showing a
    // familiar product's score with no hint that it conflicts with the user's own
    // allergens/diet, before they've even tapped Score to reach the full Result
    // screen that *does* check it.
    val cachedPreviewWarning: StateFlow<String?> = combine(cachedPreview, prefs.profile, language) { cached, profile, lang ->
        val product = cached?.product ?: return@combine null
        val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
        val dietResult = checkDiet(product, profile.diet, lang)
        val parts = mutableListOf<String>()
        allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
        dietResult.reason?.let { parts += it }
        if (parts.isEmpty()) null else parts.joinToString(" · ")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _recentBarcodes = MutableStateFlow<List<String>>(emptyList())
    val recentBarcodes: StateFlow<List<String>> = _recentBarcodes.asStateFlow()

    private val _visibleBarcodes = MutableStateFlow<List<String>>(emptyList())

    /**
     * Cached "already scanned" result for every barcode currently decoded in the
     * live camera frame — not just the single debounced [scannedBarcode] scoring
     * target. [cachedPreview] above only ever covers one code at a time (the one
     * onBarcodeDetected's stability debounce has committed to), so pointing the
     * camera at two familiar products side by side only ever surfaced a preview
     * for whichever one won that debounce, with the other showing a bare
     * bounding box and no hint it was already known. This lets ScanScreen show
     * an auto-appearing (no tap needed) AR-style mini panel above EACH familiar
     * barcode simultaneously — an unfamiliar one still only gets its bounding
     * box, since there's nothing cached to show for it without actually
     * scanning it first.
     */
    val visibleBarcodeCachedPreviews: StateFlow<Map<String, ScanResult>> = _visibleBarcodes
        .flatMapLatest { codes ->
            if (codes.isEmpty()) flowOf(emptyMap())
            else flow { emit(codes.distinct().mapNotNull { code -> scanRepo.getCachedByBarcode(code, lang = language.value)?.let { code to it } }.toMap()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Called every frame with every barcode currently decoded in view — see [visibleBarcodeCachedPreviews]. */
    fun onBarcodesVisible(barcodes: List<String>) {
        // Sorted so frame-to-frame detector reordering of the same barcode set
        // doesn't change list identity and re-trigger flatMapLatest's re-query.
        _visibleBarcodes.value = barcodes.sorted()
    }

    internal val scoreMutex = Mutex()

    // New: how many products scanned today — drives the session counter badge in the
    // scan header. Backed by a live Room query so it updates immediately after each
    // successful scan without any manual increment in the ViewModel.
    val todayScanCount: StateFlow<Int> = scanRepo.observeTodayScanCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Freemium gate - see UserPreferences.isPremium's own doc comment. Instant
     *  mode and multi-food identify are Premium; toggleInstantMode()/
     *  identifyMultiFromPhotos() below both no-op for a non-Premium user, so a
     *  stale/bypassed UI state can never actually trigger either. */
    val isPremium = prefs.isPremium.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _instantMode = MutableStateFlow(false)
    val instantMode: StateFlow<Boolean> = _instantMode.asStateFlow()

    fun toggleInstantMode() {
        if (!isPremium.value) return
        _instantMode.value = !_instantMode.value
    }

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
        if (_scannedBarcode.value == barcode) {
            pendingBarcode = null; pendingBarcodeStreak = 0
            return
        }
        // Debounce before adopting a *different* barcode than the one currently held
        // (or none at all yet). With two products' barcodes simultaneously in frame,
        // ML Kit's per-frame detection order isn't stable across frames — analyzeFrame
        // reports whichever one happens to decode first that frame, so naively adopting
        // it every time flickered the held target back and forth between both barcodes
        // at camera frame rate, which is exactly what made both a single scan and the
        // scan-several-in-a-row (instant mode) flow unreliable around cluttered shelves.
        // Requiring the same candidate to win BARCODE_STABILITY_FRAMES consecutive
        // detections before committing rejects that flicker outright — a single, genuinely
        // isolated barcode still decodes identically every frame, so it "wins" almost
        // instantly, while two alternating candidates never reach the threshold until
        // the user isolates one in frame.
        if (pendingBarcode == barcode) {
            pendingBarcodeStreak++
        } else {
            pendingBarcode = barcode
            pendingBarcodeStreak = 1
        }
        if (pendingBarcodeStreak < BARCODE_STABILITY_FRAMES) return
        pendingBarcode = null
        pendingBarcodeStreak = 0
        _scannedBarcode.value = barcode
        _detectedPriceEuros.value = null
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
        if (_state.value is ScanUiState.Idle) {
            _scannedBarcode.value = null
            _detectedPriceEuros.value = null
        }
        pendingBarcode = null
        pendingBarcodeStreak = 0
    }

    /**
     * Called once ScanScreen has consumed a Success state (navigated to the result
     * screen). Previously _state stayed Success forever — simply switching tabs away
     * from Scan and back re-triggered ScanScreen's `LaunchedEffect(state.value)` with
     * the same Success value still current, firing onResultReady again and stacking
     * another result screen on the back stack every time.
     *
     * Also clears the photo queue — previously left untouched here (and in score()'s/
     * identifyFromPhotos()'s own success paths), so the photos used for a completed
     * scan silently persisted into the *next* one. Coming back to Scan and photographing
     * or scanning a different product resubmitted the prior product's leftover photos
     * alongside (or instead of) the new ones. Combined with onBarcodeDetected()'s
     * images-queue guard above, a leftover non-empty queue after any photo-involving
     * scan would permanently block every subsequent barcode from ever being adopted
     * again, not just risk a stale-photo mixup.
     */
    fun resultConsumed() {
        _scannedBarcode.value = null
        _detectedPriceEuros.value = null
        _images.value = emptyList()
        _state.value = ScanUiState.Idle
        // onBarcodeDetected() bails out before touching these two whenever
        // _images is non-empty (its images-queue guard above), so a debounce
        // streak in progress before photos were queued was left stale rather
        // than reset - the same barcode reappearing after the queue clears
        // resumed from that stale count instead of a fresh debounce, weakening
        // the anti-flicker protection onBarcodeDetected's own doc comment
        // describes (BARCODE_STABILITY_FRAMES exists precisely so a genuinely
        // isolated barcode still needs its own full streak, not a partial one
        // left over from an unrelated candidate).
        pendingBarcode = null
        pendingBarcodeStreak = 0
        persistQueue()
    }

    // addPhoto()/removePhoto()/clearQueue() live in ScanViewModelPhotoQueue.kt as
    // extension functions, alongside the disk-backed queue persistence they share.

    // identifyFromPhotos()/identifyMultiFromPhotos() live in ScanViewModelIdentify.kt
    // as extension functions.

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
                        // Auto-logs the price detected alongside the barcode (see
                        // onPriceTextDetected's own doc comment) - lands in
                        // PriceRepository exactly like a manual PriceEntryCard entry,
                        // so it shows up in Expenses immediately and is edited/deleted
                        // the same way if wrong, never a silent, unfixable value.
                        _detectedPriceEuros.value?.let { price ->
                            viewModelScope.launch {
                                runCatching {
                                    priceRepo.log(
                                        date = java.time.LocalDate.now(),
                                        productName = scanResult.product.name,
                                        barcode = barcode,
                                        category = scanResult.product.category,
                                        priceEuros = price,
                                        weightG = scanResult.product.weightG,
                                    )
                                }
                            }
                        }
                    },
                    onFailure = { e ->
                        _state.value = when {
                            // Same UI as the static-CSV NonConsumableLookupDb match above,
                            // reached instead via a live signal from the barcode's own OFF
                            // category tags (see ScanRepository.scoreDirectBarcode/
                            // scoreViaServer's NonFoodProductException) - covers whatever
                            // that point-in-time asset snapshot doesn't (yet).
                            e is NonFoodProductException ->
                                ScanUiState.NonConsumableFound(
                                    NonConsumableDbEntry(
                                        barcode = barcode ?: "",
                                        name    = e.productName,
                                        brand   = e.brand,
                                        category = runCatching { NonConsumableCategory.valueOf(e.category) }
                                            .getOrDefault(NonConsumableCategory.OTHER),
                                    ),
                                )
                            // Neither OFF (food-only) nor the bundled NonConsumableLookupDb
                            // snapshot (frozen 2026-07-13) had this barcode - before giving up,
                            // try OPF's own live API (the same public DB the static CSV was
                            // built from, just not frozen in time). Reported case: a mouthwash
                            // barcode fell through both of the above to a dead-end "not found"
                            // error even though OPF has a live record of it. Best-effort only -
                            // findNonConsumableViaOpf already swallows its own network errors,
                            // and this never runs offline (no point trying a network call we
                            // already know will fail).
                            e is ProductNotFoundException && barcode != null && online -> {
                                val opfEntry = scanRepo.findNonConsumableViaOpf(barcode)
                                if (opfEntry != null) ScanUiState.NonConsumableFound(opfEntry)
                                // e.message is always lang-aware in practice (ScanOffLookup
                                // already threads lang into ProductNotFoundException), so this
                                // fallback is currently dead - but a hardcoded French literal
                                // here was a landmine: any future throw site that omits a
                                // message would silently show French to an English user,
                                // unlike every other branch in this function which threads lang.
                                else ScanUiState.Error(e.message ?: productNotFoundMessage(lang), needsPhoto = true)
                            }
                            e is ProductNotFoundException ->
                                ScanUiState.Error(e.message ?: productNotFoundMessage(lang), needsPhoto = true)
                            // A rejected API key/retired model/rate limit would otherwise
                            // surface as a bare "HTTP 401 " with no indication of what to
                            // actually do about it - same mapping identifyFromPhotos()/
                            // identifyMultiFromPhotos() use for these same HTTP codes.
                            else -> ScanUiState.Error(httpFriendlyMessage(e, lang))
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
        _detectedPriceEuros.value = null
        score()
    }

    fun dismissError() { _state.value = ScanUiState.Idle }

    /**
     * Reports a camera-capture failure (bind failure, buffer/driver fault, storage
     * pressure - see CameraPreview's own onCaptureError doc comment). Previously
     * shown via a bare Toast, unlike every other error in this screen (ScanUiState.Error,
     * rendered as an announced, TalkBack-visible banner via ScanStateOverlay) - a
     * screen-reader user had no reliable way to learn the shutter tap silently failed.
     * Only surfaces when nothing else is already showing (Scanning/a Found dialog/an
     * existing error) - a capture failure this transient shouldn't interrupt something
     * already in progress.
     */
    fun reportCaptureError(message: String) {
        if (_state.value is ScanUiState.Idle) _state.value = ScanUiState.Error(message)
    }

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

    /**
     * Confirms saving a detected medication (ScanUiState.MedicationFound) into Traitement.
     * medicationRepo.save() previously ran completely unguarded here - unlike every other
     * Room/DataStore write this ViewModel performs (all wrapped and surfaced via
     * ScanUiState.Error above), so a save failure (e.g. disk full) crashed the app instead
     * of leaving the confirm dialog up with a visible error.
     *
     * dosage is left blank (not entry.form) - form is the pharmaceutical form (e.g.
     * "comprimé pelliculé", "solution buvable"), not a strength/quantity. The
     * dosage field's own UI label/hint (medication_field_dosage/_hint, "ex. 500 mg,
     * 2 comprimés") sets a strength expectation that entry.form doesn't meet - BDPM's
     * MedicationDbEntry carries no per-unit-mg field at all, so silently putting form
     * there looked like a parsed dosage the app got wrong rather than what it was:
     * unrelated data in the wrong field. Left for the user to fill in themselves,
     * same as the manual-add flow already does.
     */
    fun saveDetectedMedication(entry: MedicationDbEntry) {
        viewModelScope.launch {
            runCatching { medicationRepo.save(name = entry.name, barcode = entry.barcode) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    val lang = prefs.language.first()
                    _state.value = ScanUiState.Error(e.message ?: genericErrorMessage(lang))
                    return@launch
                }
            clearQueue()
        }
    }

    /**
     * Tap-to-identify for one cropped region from the shelf-scan live-box
     * overlay (see ScanShelfObjectOverlay). Deliberately independent of the
     * main score()/identifyFromPhotos() state machine and its scoreMutex -
     * several of these run concurrently as the user taps different boxes
     * while panning across a shelf, and none of them is "the" scan this
     * screen shows full UI for. Unlike identifyFromPhotos(), this skips the
     * medication/non-consumable name-DB cross-check: that lookup exists for
     * "what am I holding" (could genuinely be a pill box), not a glance at a
     * grocery shelf, so a medication caught in a shelf tap will just be
     * scored as food in this first version rather than gaining its own
     * dedicated dialog treatment here too.
     */
    // Takes an already-built ImagePayload - see addPhoto()'s doc comment above for why
    // this shape (caller builds the payload, off Main) replaced accepting a raw Bitmap.
    suspend fun identifyShelfBox(payload: ImagePayload): Result<Pair<ScanResult, Long>> {
        val lang = prefs.language.first()
        if (!isOnline()) return Result.failure(Exception(offlineMessage(lang)))
        return scanRepo.identifyOrScoreFromImages(listOf(payload), lang, true, identifyMode = true)
            .mapCatching { scanResult -> scanResult to scanRepo.persist(scanResult) }
    }

    internal fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        // NET_CAPABILITY_INTERNET alone only means the network is *designed* to
        // provide internet - a captive portal (hotel/airport Wi-Fi requiring a
        // login page) reports this too, before the user has actually authenticated.
        // Previously that case passed this check, proceeded to Scanning, burned
        // through several retry-with-backoff attempts against a network that
        // can't actually reach anything, and only then surfaced as a generic
        // "unknown error" - confusing for a common real-world scenario. Also
        // requiring NET_CAPABILITY_VALIDATED (the system's own captive-portal/
        // connectivity probe result) fails fast with the existing, accurate
        // offlineMessage() instead.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    internal companion object {
        /** Mirrors the server's RouteHelpers.MAX_IMAGES - see addPhoto()'s doc comment (ScanViewModelPhotoQueue.kt). */
        const val MAX_QUEUED_PHOTOS = 8
        /** See onBarcodeDetected()'s own doc comment. */
        const val BARCODE_STABILITY_FRAMES = 3
    }
}
