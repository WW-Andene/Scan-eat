package fr.scanneat.presentation.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.scan.NonFoodProductException
import fr.scanneat.data.repository.scan.ProductNotFoundException
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.medication.MedicationDbEntry
import fr.scanneat.domain.engine.medication.findMedicationByBarcode
import fr.scanneat.domain.engine.medication.findMedicationByName
import fr.scanneat.domain.engine.nonconsumable.NonConsumableCategory
import fr.scanneat.domain.engine.nonconsumable.NonConsumableDbEntry
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByBarcode
import fr.scanneat.domain.engine.nonconsumable.findNonConsumableByName
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.model.ScanResult
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

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
    // Debounce state for onBarcodeDetected() - see its own doc comment for why
    // this exists (two barcodes simultaneously in frame).
    private var pendingBarcode: String? = null
    private var pendingBarcodeStreak = 0
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
        _images.value = emptyList()
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

    /**
     * Server-only counterpart to identifyFromPhotos() for a plate holding several
     * distinct foods - ScanRepository.identifyMultiFromImages() (wired to the
     * server's POST /api/identify-multi, previously unreachable from the app)
     * returns one ScanResult per detected item instead of collapsing the whole
     * plate into a single one. Every returned result is persisted immediately,
     * same as the single-item path's success branch, so MultiFoodFoundDialog can
     * navigate straight to the existing Result screen for whichever item the
     * user taps without a second network round-trip. Each item is still
     * cross-checked against the medication/non-consumable name DBs first,
     * same as identifyFromPhotos - "a plate is never going to contain a pill
     * box" doesn't rule out the vision model misidentifying something else in
     * frame as one of the plate's "foods," and unlike the single-item path,
     * nothing here would otherwise stop that misidentification from being
     * persisted straight into scan_history with a nutrition-based score.
     */
    fun identifyMultiFromPhotos() {
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
                val identified = scanRepo.identifyMultiFromImages(imgs, lang, online)
                identified.fold(
                    onSuccess = { results ->
                        val nonEdibleNames = withContext(Dispatchers.IO) {
                            results.filter { r ->
                                findMedicationByName(appContext, r.product.name) != null ||
                                    findNonConsumableByName(appContext, r.product.name) != null
                            }.map { it.product.name }.toSet()
                        }
                        val edibleResults = results.filterNot { it.product.name in nonEdibleNames }
                        _state.value = if (edibleResults.isEmpty()) {
                            ScanUiState.Error(noFoodsDetectedMessage(lang))
                        } else {
                            ScanUiState.MultiFoodFound(items = edibleResults.map { it to scanRepo.persist(it) })
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
    suspend fun identifyShelfBox(bitmap: Bitmap): Result<Pair<ScanResult, Long>> {
        val lang = prefs.language.first()
        if (!isOnline()) return Result.failure(Exception(offlineMessage(lang)))
        return scanRepo.identifyOrScoreFromImages(listOf(bitmap.toPayload()), lang, true, identifyMode = true)
            .mapCatching { scanResult -> scanResult to scanRepo.persist(scanResult) }
    }

    private fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        /** Mirrors the server's RouteHelpers.MAX_IMAGES - see addPhoto()'s doc comment. */
        const val MAX_QUEUED_PHOTOS = 8
        /** See onBarcodeDetected()'s own doc comment. */
        const val BARCODE_STABILITY_FRAMES = 3
    }
}
