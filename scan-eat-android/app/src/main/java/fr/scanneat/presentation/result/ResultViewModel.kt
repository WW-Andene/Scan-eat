package fr.scanneat.presentation.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.scan.ComparisonRepository
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
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
)

// Only worth suggesting an alternative below this grade — A/B/A+ are already a
// good choice, and surfacing one for every single scan would just be noise.
private val ALTERNATIVE_ELIGIBLE_GRADES = setOf(Grade.C, Grade.D, Grade.F)

// Branded/manufactured categories whose name is a flavor or product label, not
// a raw ingredient - "pairs well with" suggestions don't make sense for them.
private val NON_PAIRABLE_CATEGORIES = setOf(
    ProductCategory.BEVERAGE_SOFT, ProductCategory.BEVERAGE_JUICE, ProductCategory.BEVERAGE_WATER,
    ProductCategory.SNACK_SWEET, ProductCategory.SNACK_SALTY, ProductCategory.CONDIMENT,
)

sealed class LogState {
    data object Idle    : LogState()
    data object Loading : LogState()
    data object Done    : LogState()
    data class  Error(val message: String) : LogState()
}

// Internal sealed type — avoids Pair<Triple<...>> type complexity and null-unsafety
private sealed class ScanLoad {
    data object Empty : ScanLoad()
    data class  Loaded(
        val scan: ScanResult,
        val personal: PersonalScoreResult?,
        val comparison: ComparisonResult?,
        val pairings: List<String>,
        val alternative: ScanResult?,
        val scoreDelta: Int?,
        val scoreHistory: List<Int>,
    ) : ScanLoad()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ResultViewModel @Inject constructor(
    private val scanRepo: ScanRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val comparisonRepo: ComparisonRepository,
    private val prefs: UserPreferences,
    private val customFoodRepo: CustomFoodRepository,
    private val recipeRepo: RecipeRepository,
    private val manualGroceryRepo: ManualGroceryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

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

    // Needed so the hint panel can cross-reference health conditions (pregnancy,
    // etc.) the same way PersonalScoreEngine already does for the score itself —
    // the hint panel is a separate UI surface and previously ignored the profile.
    val profile: StateFlow<Profile> = prefs.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    private val _logState = MutableStateFlow<LogState>(LogState.Idle)

    // getById() is a one-shot suspend read, not a Flow, so toggling the DB row
    // via setFavorite() wouldn't otherwise be reflected until the screen fully
    // reloads. This local override lets the star flip instantly; null means
    // "no override yet, use whatever was loaded".
    private val favoriteOverride = MutableStateFlow<Boolean?>(null)

    // arm()/compare() disarm shared comparison state as a side effect, so they must run
    // at most once per scan — WhileSubscribed(5000) can cancel and restart this flow
    // (e.g. backgrounding the screen briefly) which would otherwise re-run the side
    // effect and silently re-arm the current scan as its own comparison baseline.
    private var comparisonResolved = false
    private var cachedComparison: ComparisonResult? = null

    // Fix 2: Use a typed sealed class instead of Pair<Triple<...>> — clean and null-safe
    private val scanLoad: Flow<ScanLoad> = combine(prefs.profile, prefs.language) { profile, lang -> profile to lang }.flatMapLatest { (profile, lang) ->
        flow {
            val scan = if (scanId > 0L) scanRepo.getById(scanId)
                       else scanRepo.observeHistory(limit = 1).first().firstOrNull()

            if (scan == null) { emit(ScanLoad.Empty); return@flow }

            // Was previously called with the default lang="fr", so an
            // English-language user still got all personal-score adjustment
            // reasons (diet/allergen/health-condition call-outs) in French.
            val personal   = computePersonalScore(scan.audit, scan.product, profile, lang)
            if (!comparisonResolved && isFreshScan) {
                comparisonResolved = true
                cachedComparison = if (comparisonRepo.isArmed.first()) comparisonRepo.compare(scan)
                                   else { comparisonRepo.arm(scan); null }
            }
            // Pairing suggestions are meant for a whole/raw ingredient ("tomate",
            // "boeuf") - a branded beverage or sweet snack's name often carries a
            // flavor descriptor ("Vanille", "Fraise") that happens to key an
            // ingredient in the pairings database, producing baking-ingredient
            // suggestions for a soda that has nothing to do with them.
            val pairs      = if (scan.product.category in NON_PAIRABLE_CATEGORIES) emptyList()
                              else findPairings(scan.product.name, limit = 5)
            val alternative = if (scan.audit.grade in ALTERNATIVE_ELIGIBLE_GRADES)
                scanRepo.findBetterAlternative(scan) else null

            // Prior scans of the same product (matched by barcode when present, else
            // case-insensitive name) — used for the score delta badge and history
            // mini-sparkline. Previously filtered the last 200 (all-product) scan_
            // history rows client-side, which was both wasteful (deserializing 200
            // full product/audit JSON blobs on every reload) and, for any barcoded
            // product, entirely broken: scan_history upserts by barcode (a rescan
            // REPLACEs the existing row in place, see ScanRepository.persist), so
            // there is never more than one row per barcode to find a "prior" one
            // among - the barcode branch of that filter could never match anything.
            // scan_score_history is a separate append-only log written on every
            // persist() specifically so this feature has real data to query.
            val priorScores  = scanRepo.priorScores(scan.barcode, scan.product.name, beforeMillis = scan.scannedAt)
            val scoreDelta   = priorScores.firstOrNull()?.let { scan.audit.score - it }
            val scoreHistory = priorScores.take(5).reversed()  // oldest → newest for the timeline

            emit(ScanLoad.Loaded(scan, personal, cachedComparison, pairs, alternative, scoreDelta, scoreHistory))
        }
    }

    val state: StateFlow<ResultUiState> = combine(scanLoad, _logState, favoriteOverride) { load, logState, favOverride ->
        when (load) {
            is ScanLoad.Empty  -> ResultUiState(logState = logState)
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
                    )
                )
            }.fold(
                onSuccess = { _logState.value = LogState.Done },
                onFailure = { e -> _logState.value = LogState.Error(e.message ?: "Erreur") },
            )
        }
    }

    fun clearLogState() { _logState.value = LogState.Idle }

    fun toggleFavorite() {
        val scan = state.value.scanResult ?: return
        if (scan.dbId <= 0) return
        val newValue = !scan.favorite
        favoriteOverride.value = newValue
        viewModelScope.launch { scanRepo.setFavorite(scan.dbId, newValue) }
    }

    /**
     * Multi-destination save from the scanned product's "Save to..." popup —
     * each destination is independent (a product can go to several at once)
     * and none of them requires the others to be reachable first.
     */
    fun saveToDestinations(destinations: Set<SaveDestination>) {
        val scan = state.value.scanResult ?: return
        viewModelScope.launch {
            // Symmetric with the check: unchecking Favoris and tapping Save must
            // actually unfavorite, not just skip re-favoriting — this popup is
            // the only way to change favorite status for an already-favorited
            // scan (the star icon opens it pre-selected rather than toggling).
            if (scan.dbId > 0) {
                val wantFavorite = SaveDestination.FAVORIS in destinations
                favoriteOverride.value = wantFavorite
                scanRepo.setFavorite(scan.dbId, wantFavorite)
            }
            if (SaveDestination.MES_ALIMENTS in destinations) {
                val n = scan.product.nutrition
                customFoodRepo.save(
                    name     = scan.product.name,
                    kcal     = n.energyKcal,
                    proteinG = n.proteinG,
                    carbsG   = n.carbsG,
                    fatG     = n.fatG,
                    fiberG   = n.fiberG,
                    saltG    = n.saltG,
                )
            }
            if (SaveDestination.COURSES in destinations) {
                // Previously hardcoded to 100g regardless of the actual product — a
                // 1.5kg bag of rice and a 30g snack both landed on the grocery list
                // as "100 g", actively corrupting the "how much do I need to buy"
                // math aggregateGroceryList() exists to compute. weightG is the
                // label's own stated package weight when the scan captured one.
                manualGroceryRepo.add(scan.product.name, scan.product.weightG ?: 100.0)
            }
            if (SaveDestination.REPAS in destinations) {
                val n = scan.product.nutrition
                recipeRepo.save(
                    name = scan.product.name,
                    components = listOf(
                        RecipeComponent(
                            productName = scan.product.name, grams = 100.0,
                            kcal = n.energyKcal, proteinG = n.proteinG, carbsG = n.carbsG,
                            fatG = n.fatG, saltG = n.saltG, fiberG = n.fiberG,
                        ),
                    ),
                )
            }
        }
    }
}

enum class SaveDestination { COURSES, MES_ALIMENTS, REPAS, FAVORIS }
