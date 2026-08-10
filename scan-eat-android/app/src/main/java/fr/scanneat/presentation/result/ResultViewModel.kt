package fr.scanneat.presentation.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.presentation.common.ActionFailureViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.data.repository.scan.ComparisonRepository
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ResultUiState(
    val scanResult: ScanResult? = null,
    val personalScore: PersonalScoreResult? = null,
    val comparisonResult: ComparisonResult? = null,
    val pairings: List<String> = emptyList(),
    val betterAlternative: ScanResult? = null,
    val logState: LogState = LogState.Idle,
    /** Score delta vs most recent prior scan of the same product (null = no prior scan). */
    val scoreDelta: Int? = null,
    /** Up to 5 most-recent prior scores for this product, oldest first (excludes current scan). */
    val scoreHistory: List<Int> = emptyList(),
    /**
     * True only once scanLoad has resolved to ScanLoad.Empty (scanId genuinely has no row -
     * stale deep link, deleted history entry). Distinguishes "confirmed not found" from
     * the initial pre-load state, which also has scanResult == null but should keep
     * showing the loading spinner rather than an error - without this flag the two were
     * indistinguishable and a missing scan just spun forever.
     */
    val notFound: Boolean = false,
)

sealed class LogState {
    data object Idle    : LogState()
    data object Loading : LogState()
    // R&D audit finding: Fasting and the Diary had zero cross-reference -
    // logging food mid-fast never surfaced any signal. loggedDuringFast lets
    // ResultScreen show a non-blocking informational snackbar rather than
    // silently accepting the entry with no acknowledgment that a fast was
    // running (see ConsumptionRepository.log's own doc comment on why this
    // is informational, not a veto).
    data class  Done(val loggedDuringFast: Boolean = false) : LogState()
    data class  Error(val message: String) : LogState()
}

// ScanLoad (the flow's internal sealed result type) now lives in
// ResultScanLoader.kt (same package) alongside the loader that builds it.

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResultViewModel @Inject constructor(
    internal val scanRepo: ScanRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val comparisonRepo: ComparisonRepository,
    private val prefs: UserPreferences,
    private val biolismRepo: BiolismRepository,
    internal val customFoodRepo: CustomFoodRepository,
    internal val recipeRepo: RecipeRepository,
    internal val manualGroceryRepo: ManualGroceryRepository,
    private val priceRepo: PriceRepository,
    savedStateHandle: SavedStateHandle,
) : ActionFailureViewModel() {

    private val scanId: Long = savedStateHandle.get<Long>("scanId") ?: 0L

    // True only when navigated here straight from a just-completed scan
    // (ScanScreen.onResultReady, see AppRoutes.result's fresh param) - every
    // other entry point (History/Favorites/Dashboard's "top scanned" tile)
    // routes to this same screen to view an old entry, defaulting to false.
    // Gates the comparisonRepo arm()/compare() side effect below: previously
    // it ran unconditionally for whatever scan this screen loaded, so idly
    // browsing two unrelated History entries back-to-back could silently
    // consume the arm slot meant for "scan A then scan B" (eating the real
    // comparison) or pop a misleading score-delta/flag-diff banner between
    // two products the user was never actually comparing.
    private val isFreshScan: Boolean = savedStateHandle.get<Boolean>("fresh") ?: false

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Settings > Devise - PriceEntryCard previously hardcoded "€".
    val currencySymbol: StateFlow<String> = prefs.currencySymbol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

    // Needed so the hint panel can cross-reference health conditions (pregnancy,
    // etc.) the same way PersonalScoreEngine already does for the score itself —
    // the hint panel is a separate UI surface and previously ignored the profile.
    val profile: StateFlow<Profile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    internal val _logState = MutableStateFlow<LogState>(LogState.Idle)

    // getById() is a one-shot suspend read, not a Flow, so toggling the DB row
    // via setFavorite() wouldn't otherwise be reflected until the screen fully
    // reloads. This local override lets the star flip instantly; null means
    // "no override yet, use whatever was loaded".
    internal val favoriteOverride = MutableStateFlow<Boolean?>(null)

    // Builds each scan's ScanLoad — holds its own comparisonResolved/cachedComparison
    // re-entrancy state (see ResultScanLoader.kt, same package).
    private val scanLoader = ResultScanLoader(scanId, isFreshScan, scanRepo, comparisonRepo)

    // Fix 2: Use a typed sealed class instead of Pair<Triple<...>> — clean and null-safe
    private val scanLoad: Flow<ScanLoad> = combine(prefs.profile, prefs.language, biolismRepo.profile, prefs.isPremium) { profile, lang, bioProfile, isPremium -> Quadruple(profile, lang, bioProfile, isPremium) }.flatMapLatest { (profile, lang, bioProfile, isPremium) ->
        // Biolism is Premium-gated (see UserPreferences.isPremium) - a non-Premium
        // user's personal-score kcal-budget adjustments must come from the plain
        // profile estimate only, never a stored bioProfile from before downgrading
        // or before this gate existed.
        scanLoader.build(profile, lang, if (isPremium) bioProfile else BiolismProfile())
    }

    val state: StateFlow<ResultUiState> = combine(scanLoad, _logState, favoriteOverride) { load, logState, favOverride ->
        when (load) {
            is ScanLoad.Empty  -> ResultUiState(logState = logState, notFound = true)
            is ScanLoad.Loaded -> ResultUiState(
                scanResult        = favOverride?.let { load.scan.copy(favorite = it) } ?: load.scan,
                personalScore     = load.personal,
                comparisonResult  = load.comparison,
                pairings          = load.pairings,
                betterAlternative = load.alternative,
                logState          = logState,
                scoreDelta        = load.scoreDelta,
                scoreHistory      = load.scoreHistory,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultUiState())

    fun log(portionG: Double, mealSlot: MealSlot) {
        if (_logState.value is LogState.Loading) return   // guard against double-tap double-logging
        val scan = state.value.scanResult ?: return
        viewModelScope.launch {
            _logState.value = LogState.Loading
            runCatching {
                consumptionRepo.log(
                    DiaryEntry(
                        date        = LocalDate.now(),
                        mealSlot    = mealSlot,
                        productName = scan.product.name,
                        barcode     = scan.barcode,
                        portionG    = portionG,
                        nutrition   = scan.product.nutrition,
                        source      = scan.source,
                        ingredients = scan.product.ingredients,
                        category    = scan.product.category,
                        // R&D audit finding, phase 2: profileId was dead
                        // scaffolding until multi-profile support made it real.
                        profileId   = profile.value.id,
                    )
                )
            }.fold(
                onSuccess = { loggedDuringFast -> _logState.value = LogState.Done(loggedDuringFast) },
                // e.message ?: "Erreur" ignored `lang` and always fell back to
                // French even for an English-language user, unlike every other
                // error string in this file.
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    _logState.value = LogState.Error(
                        e.message ?: if (language.value == "en") "Error" else "Erreur"
                    )
                },
            )
        }
    }

    fun clearLogState() { _logState.value = LogState.Idle }

    // Price history for this exact product — matched by barcode when the scan has
    // one, else falls back to matching by name (LLM-identified/no-barcode scans
    // still worth tracking a price against). Empty until a scan is loaded.
    val priceEntries: StateFlow<List<PriceEntry>> = combine(state, profile.map { it.id }.flatMapLatest { id -> priceRepo.observeAll(id) }) { s, all ->
        val scan = s.scanResult ?: return@combine emptyList()
        if (scan.barcode != null) all.filter { it.barcode == scan.barcode }
        else all.filter { it.barcode == null && it.productName == scan.product.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User-requested/audit-found: these two previously ran their write inside a bare
    // runCatching that discarded the result entirely - unlike log() just above, a
    // failed price save/delete (disk full, Room error) left the UI showing exactly
    // as if it had succeeded, with no snackbar and no retry path. guardedLaunch
    // (ActionFailureViewModel) is the same guard every other write-heavy ViewModel
    // in the app already uses for this.
    fun savePrice(priceEuros: Double, weightG: Double?) {
        val scan = state.value.scanResult ?: return
        guardedLaunch {
            priceRepo.log(
                date = LocalDate.now(),
                productName = scan.product.name,
                barcode = scan.barcode,
                category = scan.product.category,
                priceEuros = priceEuros,
                weightG = weightG,
                profileId = profile.value.id,
            )
        }
    }

    fun deletePrice(id: String) {
        guardedLaunch { priceRepo.delete(id) }
    }

    // saveToDestinations (the "Save to..." popup's multi-destination write) is
    // implemented as an internal extension function in ResultSaveDestinations.kt
    // (same package) — was already a public member, external callers unaffected.
}

enum class SaveDestination { COURSES, MES_ALIMENTS, REPAS, FAVORIS }
