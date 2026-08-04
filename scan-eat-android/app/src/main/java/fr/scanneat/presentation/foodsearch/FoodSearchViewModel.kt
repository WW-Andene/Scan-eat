package fr.scanneat.presentation.foodsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.ScanResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Simple threshold-based filters over the unified fields both sources below map into. */
enum class FoodSearchFilter { ALL, HIGH_PROTEIN, LOW_CARB, HIGH_FIBER, IRON_SOURCE, CALCIUM_SOURCE }

/**
 * Which accordion section a result groups under. NOT ProductCategory
 * (SANDWICH/YOGURT/CHEESE/...) - that enum is retail-product-oriented and would
 * misclassify most whole foods (an apple, a lentil) as OTHER.
 *
 * Previously this reused FOOD_DB's four *source-file* buckets directly
 * (FRUITS_VEGETABLES, GRAINS_PROTEINS, DAIRY_LEGUMES, FATS_SWEETS_BEVERAGES) -
 * those groupings exist purely to split one large Kotlin file into four smaller
 * ones and were never meant to be nutritionally coherent categories (e.g.
 * "grains and proteins" lumping bread in with chicken and beef, "fats, sweets,
 * and beverages" lumping olive oil in with soda and pizza). Reported as
 * confusing - reworked into the actual food-group taxonomy below, built via an
 * explicit per-item map ([FOOD_DB_CATEGORY_BY_NAME]) rather than reusing the
 * source-file split.
 */
enum class FoodSearchCategory {
    SCANNED, CUSTOM,
    FRUITS, VEGETABLES, GRAINS_STARCHES, PROTEINS, LEGUMES_NUTS_SEEDS,
    DAIRY, FATS_OILS, SWEETS_SNACKS, BEVERAGES, PREPARED_MEALS,
    // Catch-all: every FOOD_DB entry is explicitly classified above, so this
    // should never actually be hit - only exists so a future FOOD_DB addition
    // that's forgotten in the classification lists above still lands somewhere
    // visible instead of silently defaulting into an unrelated real category.
    OTHER,
}

/**
 * Unified row shown by FoodSearchScreen, regardless of whether it came from the
 * curated database or the user's own scan history - [scanId]/[grade] are only set
 * for the latter, letting the UI open the full Result screen (real score, audit,
 * warnings) instead of the bare macro accordion a generic FOOD_DB entry gets.
 */
data class FoodSearchItem(
    val name: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val saltG: Double,
    val ironMg: Double,
    val calciumMg: Double,
    val vitDUg: Double,
    val b12Ug: Double,
    val category: FoodSearchCategory,
    val grade: Grade? = null,
    val scanId: Long? = null,
    // Only set for an online (Open Food Facts search) result not yet saved to
    // this user's own scan history - lets tapping the row persist it on demand
    // (see FoodSearchViewModel.openOnlineItem) so it opens the real Result
    // screen exactly like tapping a product they'd scanned themselves.
    val barcode: String? = null,
)

/** Which of Products/Links/Both the "Recherche" screen currently shows -
 *  cycled by a single button (see FoodSearchViewModel.cycleDisplayMode). */
enum class SearchDisplayMode { PRODUCTS, LINKS, BOTH }

/**
 * "Recherche" — a full browse/search engine over EVERY product this app actually
 * knows about, not just the ~130-entry curated FOOD_DB (a user's reasonable first
 * reaction to that number alone: "only 130?"). Three sources, merged and grouped
 * into category accordions (FoodSearchScreen) rather than one long flat list:
 *   1. FOOD_DB — ~130 CIQUAL-based generic references (e.g. "beef", "banana"),
 *      already curated into 4 real categories reused as-is here
 *   2. The user's own custom foods
 *   3. The user's own scan history — every real product they've ever scanned
 * A name collision prefers the scanned item (real, specific data with an actual
 * score) over the generic curated one.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val customFoodRepo: CustomFoodRepository,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    private val customFoods: StateFlow<List<FoodEntry>> = customFoodRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _filter = MutableStateFlow(FoodSearchFilter.ALL)
    val filter: StateFlow<FoodSearchFilter> = _filter.asStateFlow()
    fun setFilter(f: FoodSearchFilter) { _filter.value = f }

    private val _displayMode = MutableStateFlow(SearchDisplayMode.PRODUCTS)
    val displayMode: StateFlow<SearchDisplayMode> = _displayMode.asStateFlow()

    /** Single button, three states, cycling forward - Produits -> Liens -> Produits et liens -> Produits. */
    fun cycleDisplayMode() {
        _displayMode.value = when (_displayMode.value) {
            SearchDisplayMode.PRODUCTS -> SearchDisplayMode.LINKS
            SearchDisplayMode.LINKS    -> SearchDisplayMode.BOTH
            SearchDisplayMode.BOTH     -> SearchDisplayMode.PRODUCTS
        }
    }

    private val debouncedQuery = _query.debounce(150)

    /** Recomputed on every query change, not gated behind a button - unlike
     *  [searchOnline], this never leaves the device (URL construction only,
     *  no network call), so there's no rate limit to protect. */
    val sourceLinks: StateFlow<List<SourceLink>> = debouncedQuery
        .map { q -> buildSourceLinks(q, prefs.language.first()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // If the typed query is itself a nutrient query ("fiber", "protéine>20",
    // "sel<0.3"...), search by name for it would always come back empty - browse
    // the full dataset instead and let the nutrient predicate below do the
    // matching, same as if the user had tapped the corresponding filter chip.
    private val effectiveSearchQuery: Flow<String> = debouncedQuery.map { q -> if (predicateFor(q) != null) "" else q }

    // searchByName(query="") still matches every row (SQL LIKE '%%') and is already
    // ordered most-recent-first, capped at 300 - a real, DB-level search, not a
    // client-side filter over some already-loaded "recent" window.
    private val scannedItems: Flow<List<FoodSearchItem>> = effectiveSearchQuery
        .flatMapLatest { q -> scanRepo.searchHistory(q) }
        .map { results -> results.map { it.toItem() }.distinctBy { it.name.lowercase() } }

    private val localItems: Flow<List<FoodSearchItem>> = combine(effectiveSearchQuery, customFoods) { q, customs ->
        if (q.isBlank()) {
            val customNames = customs.map { it.name }.toSet()
            customs.map { it.toItem(isCustom = true) } +
                FOOD_DB.filterNot { it.name in customNames }.map { it.toItem(isCustom = false) }
        } else {
            val customNames = customs.map { it.name }.toSet()
            searchFoodDB(q, limit = 200, extraFoods = customs)
                .map { it.toItem(isCustom = it.name in customNames) }
        }
    }

    /** Grouped by [FoodSearchCategory] (accordion sections), each sorted alphabetically. */
    val groupedResults: StateFlow<Map<FoodSearchCategory, List<FoodSearchItem>>> =
        combine(scannedItems, localItems, _filter, debouncedQuery) { scanned, local, f, q ->
            // An explicitly-tapped filter chip always wins; a typed nutrient query
            // only kicks in while the chip row is still on its default ALL state.
            val typedPredicate = if (f == FoodSearchFilter.ALL) predicateFor(q) else null
            val effectivePredicate: (FoodSearchItem) -> Boolean = typedPredicate ?: { it.matches(f) }
            val scannedNames = scanned.map { it.name.lowercase() }.toSet()
            (scanned + local.filterNot { it.name.lowercase() in scannedNames })
                .filter(effectivePredicate)
                .groupBy { it.category }
                .mapValues { (_, items) -> items.sortedBy { it.name } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Online (Open Food Facts search) results below are explicit-only
    // ("Rechercher en ligne" button), never auto-triggered on typing - OFF is a
    // public, rate-limited API, unlike the two local sources above which can
    // safely re-query on every keystroke. Covers ingredient/additive/molecule
    // search, which only scanned OFF products (not FOOD_DB/custom foods) carry
    // data for - see ScanOffLookup.searchOffProducts.
    //
    // onlineRaw is kept alongside the flattened FoodSearchItem list so a tap on
    // an online result can find its full ScanResult back (needed to persist it
    // - see openOnlineItem) without threading the barcode through a lookup
    // elsewhere.
    private var onlineRaw: List<ScanResult> = emptyList()

    private val _onlineResults = MutableStateFlow<List<FoodSearchItem>>(emptyList())
    val onlineResults: StateFlow<List<FoodSearchItem>> = _onlineResults.asStateFlow()

    private val _onlineSearchState = MutableStateFlow(OnlineSearchState.IDLE)
    val onlineSearchState: StateFlow<OnlineSearchState> = _onlineSearchState.asStateFlow()

    // openOnlineItem()'s scanRepo.persist() call was previously unguarded, unlike
    // every sibling screen's writes (Weight/Activity/Recipes/etc. all wrap theirs in
    // runCatching + this exact _actionFailed pattern) - a Room write failure here
    // (disk full, corrupt row) threw uncaught inside the coroutine instead of
    // surfacing as a one-shot snackbar, and this screen had no failure-feedback
    // plumbing at all (no _actionFailed StateFlow, no SnackbarHostState in
    // FoodSearchScreen) to catch it even if it had been guarded.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun searchOnline() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        _onlineSearchState.value = OnlineSearchState.LOADING
        viewModelScope.launch {
            val lang = prefs.language.first()
            val results = try {
                scanRepo.searchOffProducts(q, lang)
            } catch (e: Exception) {
                _onlineSearchState.value = OnlineSearchState.ERROR
                return@launch
            }
            // distinctBy barcode - OFF's own search results can repeat the same barcode
            // (e.g. regional variants indexed separately but sharing a code); without this,
            // openOnlineItem's onlineRaw.firstOrNull { it.barcode == item.barcode } lookup
            // is ambiguous and every duplicate row would silently resolve to the same one.
            onlineRaw = results.distinctBy { it.barcode }
            // toItem() sets scanId = dbId, which defaults to 0 (not null) for a
            // ScanResult that was never persisted - left as-is, FoodSearchRow's
            // `item.scanId != null` check would treat 0 as "already in this
            // user's history" and call onOpenResult(0) instead of the
            // online-persist path below. Forced back to null here since these
            // results are never actually in scan_history yet.
            _onlineResults.value = onlineRaw.map { it.toItem().copy(scanId = null, barcode = it.barcode) }
            _onlineSearchState.value = if (results.isEmpty()) OnlineSearchState.EMPTY else OnlineSearchState.SUCCESS
        }
    }

    fun clearOnlineResults() {
        onlineRaw = emptyList()
        _onlineResults.value = emptyList()
        _onlineSearchState.value = OnlineSearchState.IDLE
    }

    /**
     * An online result isn't in this user's scan history yet - tapping it saves
     * it first (same [ScanRepository.persist] every real scan goes through) so
     * it opens the real, full Result screen exactly like any other product they
     * scanned themselves, rather than a bare read-only macro preview.
     */
    fun openOnlineItem(item: FoodSearchItem, onOpened: (Long) -> Unit) {
        val raw = onlineRaw.firstOrNull { it.barcode == item.barcode } ?: return
        viewModelScope.launch {
            runCatching { scanRepo.persist(raw) }
                .onSuccess { onOpened(it) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}

enum class OnlineSearchState { IDLE, LOADING, SUCCESS, EMPTY, ERROR }
