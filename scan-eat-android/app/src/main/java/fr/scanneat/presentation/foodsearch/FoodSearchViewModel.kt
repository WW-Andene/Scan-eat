package fr.scanneat.presentation.foodsearch

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.common.ActionFailureViewModel
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.nutrition.toProduct
import fr.scanneat.domain.engine.scoring.scoreProduct
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Simple threshold-based filters over the unified fields both sources below map into. */
enum class FoodSearchFilter {
    ALL, HIGH_PROTEIN, HIGH_CARB, HIGH_FAT, HIGH_FIBER, HIGH_VITAMIN, HIGH_MINERAL,
    LOW_CARB, IRON_SOURCE, CALCIUM_SOURCE,
}

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
    // User-reported: the newly-covered micronutrients (vitCMg first, then
    // magnesium/potassium/zinc/vitA/folate/vitE/vitK/B6) reached the diary and
    // dashboard totals but never this row's own "fiche technique" (the expandable
    // detail panel in FoodSearchRow) - FoodSearchItem simply had no fields for
    // them, same class of gap FoodEntry itself had before those fixes.
    val vitCMg: Double,
    val magnesiumMg: Double,
    val potassiumMg: Double,
    val zincMg: Double,
    val vitAUg: Double,
    val b9Ug: Double,
    val vitEMg: Double,
    val vitKUg: Double,
    val b6Mg: Double,
    val category: FoodSearchCategory,
    val grade: Grade? = null,
    val scanId: Long? = null,
    // Only set for an online (Open Food Facts search) result not yet saved to
    // this user's own scan history - lets tapping the row persist it on demand
    // (see FoodSearchViewModel.openOnlineItem) so it opens the real Result
    // screen exactly like tapping a product they'd scanned themselves.
    val barcode: String? = null,
    // Only meaningful for a scanned row (scanId != null) - FOOD_DB/custom rows
    // have no favorite state of their own until favorited for the first time
    // (see FoodSearchViewModel.toggleFavorite, which persists them into scan
    // history at that point, same as tapping an online result already does).
    val favorite: Boolean = false,
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
    private val consumptionRepo: ConsumptionRepository,
    private val pantryRepo: fr.scanneat.data.repository.pantry.PantryRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    private val customFoods: StateFlow<List<FoodEntry>> = activeProfileId.flatMapLatest { id -> customFoodRepo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User-requested: "what have I never tried" - product categories this
    // profile has never once scanned, so a user stuck on the same handful of
    // categories can see what's missing from their own history instead of
    // only ever comparing one product's score in isolation.
    val neverTriedCategories: StateFlow<List<fr.scanneat.domain.model.ProductCategory>> = activeProfileId
        .flatMapLatest { id -> scanRepo.observeDistinctCategories(id) }
        .map { scanned ->
            fr.scanneat.domain.model.ProductCategory.entries.filter {
                it != fr.scanneat.domain.model.ProductCategory.OTHER && it !in scanned
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _filter = MutableStateFlow(FoodSearchFilter.ALL)
    val filter: StateFlow<FoodSearchFilter> = _filter.asStateFlow()
    fun setFilter(f: FoodSearchFilter) { _filter.value = f }

    // "Note" (Nutri-Score-style grade) filter, independent of and AND-combined
    // with [filter] above - a user browsing for e.g. high-fiber food may still
    // want to exclude the D/E/F results within that set rather than pick one
    // axis or the other.
    private val _gradeFilter = MutableStateFlow<Grade?>(null)
    val gradeFilter: StateFlow<Grade?> = _gradeFilter.asStateFlow()
    fun setGradeFilter(g: Grade?) { _gradeFilter.value = g }

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
    private val scannedItems: Flow<List<FoodSearchItem>> = combine(effectiveSearchQuery, activeProfileId) { q, id -> q to id }
        .flatMapLatest { (q, id) -> scanRepo.searchHistory(q, id) }
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
        combine(scannedItems, localItems, _filter, _gradeFilter, debouncedQuery) { scanned, local, f, grade, q ->
            // An explicitly-tapped filter chip always wins; a typed nutrient query
            // only kicks in while the chip row is still on its default ALL state.
            val typedPredicate = if (f == FoodSearchFilter.ALL) predicateFor(q) else null
            val nutrientPredicate: (FoodSearchItem) -> Boolean = typedPredicate ?: { it.matches(f) }
            val effectivePredicate: (FoodSearchItem) -> Boolean =
                if (grade == null) nutrientPredicate else { item -> nutrientPredicate(item) && item.grade == grade }
            val scannedNames = scanned.map { it.name.lowercase() }.toSet()
            (scanned + local.filterNot { it.name.lowercase() in scannedNames })
                .filter(effectivePredicate)
                .groupBy { it.category }
                .mapValues { (_, items) -> items.sortedBy { it.name } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // User-requested: online (Open Food Facts) search now also fires
    // automatically while typing, not just via the "Rechercher en ligne"
    // button below (kept as a visible manual fallback - useful to force a
    // search under 2 characters, or retry after ERROR/EMPTY). OFF's own
    // published guideline is ~10 search requests/minute (vs ~100/min for
    // barcode lookups), well below the two local sources above which can
    // safely re-query on every keystroke - a 700ms debounce plus a 2-char
    // minimum keeps normal typing comfortably under that, unlike the local
    // sources' 150ms debounce above. Covers ingredient/additive/molecule
    // search, which only scanned OFF products (not FOOD_DB/custom foods) carry
    // data for - see ScanOffLookup.searchOffProducts.
    //
    // User-requested: a "typing cache" - every online result ever fetched this
    // session, kept keyed by barcode (never cleared/reset the way the old
    // per-query onlineRaw list was) so a tap on any previously-seen online
    // result can still find its full ScanResult back (needed to persist it -
    // see openOnlineItem) even after the query has since moved on, and so
    // instantCacheMatches below has a growing pool to filter instantly.
    private val onlineRawCache: MutableMap<String, ScanResult> = mutableMapOf()
    private val onlineItemCache: MutableMap<String, FoodSearchItem> = mutableMapOf()
    // Safety valve, same pattern as AdditivesDb.kt's additiveLookupCache /
    // ScanOffLookup's resultCache - the two maps above are otherwise never
    // cleared for the ViewModel's lifetime (see the comment above), so a very
    // heavy single-session search habit could grow them without bound. Bounds
    // both together since every write below inserts into both maps for the
    // same barcode key.
    private val maxOnlineCacheEntries = 20_000

    /** Inserts [raw]/its FoodSearchItem into both online caches for [barcode],
     *  evicting (clearing) both together once either would grow past the cap -
     *  keeps the two maps' key sets identical, matching every reader's
     *  assumption that a barcode present in one is present in the other. */
    private fun cacheOnlineResult(barcode: String, raw: ScanResult, item: FoodSearchItem) {
        if (onlineRawCache.size >= maxOnlineCacheEntries || onlineItemCache.size >= maxOnlineCacheEntries) {
            onlineRawCache.clear()
            onlineItemCache.clear()
        }
        onlineRawCache[barcode] = raw
        onlineItemCache[barcode] = item
    }

    /** Every cached online item whose name matches [q] - recomputed straight
     *  from the in-memory cache, no debounce/network, so it can run on every
     *  keystroke and show *something* related immediately, before the real
     *  debounced OFF search below has even fired yet (let alone returned). */
    private fun instantCacheMatches(q: String): List<FoodSearchItem> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return onlineItemCache.values.filter { it.name.lowercase().contains(needle) }.sortedBy { it.name }
    }

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
    // R&D audit finding: Fasting and the Diary had zero cross-reference - see
    // ConsumptionRepository.log's own doc comment.
    private val _loggedDuringFast = MutableStateFlow(false)
    val loggedDuringFast: StateFlow<Boolean> = _loggedDuringFast.asStateFlow()
    fun clearLoggedDuringFast() { _loggedDuringFast.value = false }

    // Auto-fires online search while typing - flatMapLatest so a new keystroke
    // (after the 700ms debounce settles again) cancels whatever OFF request
    // was still in flight for the previous, now-stale query instead of both
    // racing to write _onlineResults/_onlineSearchState. A manual searchOnline()
    // tap shares the same MutableSharedFlow trigger below so the two can never
    // run two concurrent requests against each other either.
    private val manualOnlineTrigger = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        // User-requested: persist the typing cache across app restarts, not
        // just in-memory for the current process - loaded once here from
        // online_search_cache (see ScanRepository.loadOnlineSearchCache) into
        // the same maps the live search below also writes into, so a query
        // typed in an earlier session shows instant results again today
        // without re-hitting OFF's rate-limited search endpoint at all.
        viewModelScope.launch {
            scanRepo.loadOnlineSearchCache().forEach { raw ->
                val barcode = raw.barcode ?: return@forEach
                cacheOnlineResult(barcode, raw, raw.toItem().copy(scanId = null, barcode = barcode))
            }
            _onlineResults.value = instantCacheMatches(_query.value)
        }

        // Instant path: recomputed straight from the cache on every single
        // keystroke, completely unthrottled - this is what actually answers
        // "show me something related even before the real call fires".
        _query.onEach { q -> _onlineResults.value = instantCacheMatches(q) }.launchIn(viewModelScope)

        // Found on review: without this, onlineSearchState was sticky across a
        // query change - e.g. a query that ended in ERROR/EMPTY left that same
        // state showing (wrong error banner / "no results for X" text quoting
        // the *new* query) after typing something else entirely, even though
        // onlineResults above had already correctly moved on to the new
        // query's cache matches. Reset back to IDLE on every query change; the
        // debounced search below overwrites it again once (if) it actually
        // fires for the settled query.
        _query.onEach { _onlineSearchState.value = OnlineSearchState.IDLE }.launchIn(viewModelScope)

        merge(
            _query.debounce(700).map { it.trim() }.distinctUntilChanged().filter { it.length >= 2 },
            manualOnlineTrigger,
        )
            .flatMapLatest { q -> flow { emit(runOnlineSearch(q)) } }
            .launchIn(viewModelScope)
    }

    private suspend fun runOnlineSearch(q: String) {
        if (q.isBlank()) return
        _onlineSearchState.value = OnlineSearchState.LOADING
        val lang = prefs.language.first()
        val results = try {
            scanRepo.searchOffProducts(q, lang)
        } catch (e: Exception) {
            _onlineSearchState.value = OnlineSearchState.ERROR
            return
        }
        // distinctBy barcode - OFF's own search results can repeat the same barcode
        // (e.g. regional variants indexed separately but sharing a code); without this,
        // the cache below would just overwrite itself harmlessly, but openOnlineItem's
        // barcode lookup being ambiguous is the real reason this stays.
        val deduped = results.distinctBy { it.barcode }
        // Merged into the persistent cache (not replacing it) - a query typed
        // earlier this session whose results scrolled out of view is still
        // instantly re-findable if the user retypes toward it, and a barcode
        // seen under one query stays resolvable (for openOnlineItem/
        // toggleFavorite/resolveFood) even after a later query's results
        // would otherwise have pushed it out.
        deduped.forEach { raw ->
            val barcode = raw.barcode ?: return@forEach
            // toItem() sets scanId = dbId, which defaults to 0 (not null) for a
            // ScanResult that was never persisted - left as-is, FoodSearchRow's
            // `item.scanId != null` check would treat 0 as "already in this
            // user's history" and call onOpenResult(0) instead of the
            // online-persist path below. Forced back to null here since these
            // results are never actually in scan_history yet.
            cacheOnlineResult(barcode, raw, raw.toItem().copy(scanId = null, barcode = barcode))
        }
        // User-requested: persist to online_search_cache too, not just the
        // in-memory maps above - fire-and-forget, doesn't block the UI update
        // below on a disk write, and a failure here just means this batch
        // isn't in tomorrow's cache, not a user-visible error (this session's
        // in-memory cache already has it regardless).
        viewModelScope.launch { runCatching { scanRepo.cacheOnlineSearchResults(deduped) } }
        // Re-filter from the now-updated cache against whatever the query box
        // currently holds rather than [q] itself - harmless when they match
        // (the common case), and correct on the rare case this callback still
        // ran to completion after the query moved on despite flatMapLatest
        // above cancelling the request.
        _onlineResults.value = instantCacheMatches(_query.value)
        _onlineSearchState.value = if (deduped.isEmpty()) OnlineSearchState.EMPTY else OnlineSearchState.SUCCESS
    }

    /** Manual "Rechercher en ligne" button - still useful under the 2-char
     *  auto-search floor, or to retry immediately after ERROR/EMPTY without
     *  waiting for the debounce to re-settle on an unchanged query. */
    fun searchOnline() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        manualOnlineTrigger.tryEmit(q)
    }

    /**
     * An online result isn't in this user's scan history yet - tapping it saves
     * it first (same [ScanRepository.persist] every real scan goes through) so
     * it opens the real, full Result screen exactly like any other product they
     * scanned themselves, rather than a bare read-only macro preview.
     */
    fun openOnlineItem(item: FoodSearchItem, onOpened: (Long) -> Unit) {
        val raw = item.barcode?.let { onlineRawCache[it] } ?: return
        guardedLaunch {
            val id = scanRepo.persist(raw, activeProfileId.value)
            onOpened(id)
        }
    }

    /**
     * User-requested: favorite a product directly from search instead of only
     * from the full Result screen. A scanned row already has a real dbId to
     * favorite directly. A FOOD_DB/custom row has neither a scan_history row
     * nor a favorite concept of its own - favoriting one persists it first
     * (same scanRepo.persist() openOnlineItem already uses for an online
     * result), so it becomes a real, favoritable scan_history row exactly like
     * any product the user actually scanned. groupedResults' own "scanned wins
     * on name collision" rule then naturally promotes it to the SCANNED
     * section going forward.
     */
    fun toggleFavorite(item: FoodSearchItem) {
        guardedLaunch {
            when {
                item.scanId != null -> scanRepo.setFavorite(item.scanId, !item.favorite)
                item.barcode != null -> {
                    val raw = onlineRawCache[item.barcode] ?: return@guardedLaunch
                    scanRepo.setFavorite(scanRepo.persist(raw, activeProfileId.value), true)
                }
                else -> {
                    val entry = customFoods.value.firstOrNull { it.name == item.name }
                        ?: FOOD_DB.firstOrNull { it.name == item.name } ?: return@guardedLaunch
                    val product = customFoodRepo.toProduct(entry)
                    val audit = scoreProduct(product, prefs.language.first())
                    val id = scanRepo.persist(ScanResult(product = product, audit = audit, warnings = emptyList(), source = ScanSource.MANUAL), activeProfileId.value)
                    scanRepo.setFavorite(id, true)
                }
            }
        }
    }

    /** What [openLogSheet] resolved for the currently-open LogSheet - the real
     *  product/source/barcode when known, not a lossy reconstruction, mirroring
     *  DiaryViewModel.addEntryFromScan's own preference for real scan data over
     *  a FoodEntry-shaped guess whenever one is available. */
    private data class ResolvedFood(val product: Product, val source: ScanSource, val barcode: String?)

    private val _logTarget = MutableStateFlow<ResolvedFood?>(null)
    /** Non-null while LogSheet should be shown for a tapped row's resolved product. */
    val logSheetProduct: StateFlow<Product?> = _logTarget.map { it?.product }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private suspend fun resolveFood(item: FoodSearchItem): ResolvedFood? = when {
        item.scanId != null -> scanRepo.getById(item.scanId)?.let { ResolvedFood(it.product, it.source, it.barcode) }
        item.barcode != null -> onlineRawCache[item.barcode]?.let { ResolvedFood(it.product, it.source, it.barcode) }
        else -> (customFoods.value.firstOrNull { it.name == item.name } ?: FOOD_DB.firstOrNull { it.name == item.name })
            ?.let { ResolvedFood(customFoodRepo.toProduct(it), ScanSource.MANUAL, null) }
    }

    /** User-requested: log a search result straight to the diary without first
     *  navigating to the full Result screen. */
    fun openLogSheet(item: FoodSearchItem) {
        viewModelScope.launch {
            _logTarget.value = resolveFood(item) ?: run { flagActionFailed(); null }
        }
    }

    fun dismissLogSheet() { _logTarget.value = null }

    fun confirmLog(portionG: Double, mealSlot: MealSlot) {
        val resolved = _logTarget.value ?: return
        guardedLaunch {
            val loggedDuringFast = consumptionRepo.log(
                DiaryEntry(
                    date        = LocalDate.now(),
                    mealSlot    = mealSlot,
                    productName = resolved.product.name,
                    barcode     = resolved.barcode,
                    portionG    = portionG,
                    nutrition   = resolved.product.nutrition,
                    source      = resolved.source,
                    ingredients = resolved.product.ingredients,
                    category    = resolved.product.category,
                    profileId   = activeProfileId.value,
                )
            )
            _logTarget.value = null
            if (loggedDuringFast) _loggedDuringFast.value = true
        }
    }

    /** Same "Repas"/"Garde-manger" destination split as ResultScreen's own LogSheet
     *  call - lets a search result be stocked in the pantry without it counting as
     *  eaten today, previously reachable only from a live barcode scan. */
    fun confirmLogWithDestinations(portionG: Double, mealSlot: MealSlot, destinations: Set<fr.scanneat.presentation.result.LogDestination>) {
        val resolved = _logTarget.value ?: return
        guardedLaunch {
            var loggedDuringFast = false
            if (fr.scanneat.presentation.result.LogDestination.REPAS in destinations) {
                loggedDuringFast = consumptionRepo.log(
                    DiaryEntry(
                        date        = LocalDate.now(),
                        mealSlot    = mealSlot,
                        productName = resolved.product.name,
                        barcode     = resolved.barcode,
                        portionG    = portionG,
                        nutrition   = resolved.product.nutrition,
                        source      = resolved.source,
                        ingredients = resolved.product.ingredients,
                        category    = resolved.product.category,
                        profileId   = activeProfileId.value,
                    )
                )
            }
            if (fr.scanneat.presentation.result.LogDestination.GARDE_MANGER in destinations) {
                pantryRepo.addOrUpdate(
                    name = resolved.product.name, barcode = resolved.barcode, category = resolved.product.category,
                    quantity = resolved.product.weightG ?: 100.0, unit = fr.scanneat.data.repository.pantry.PantryUnit.GRAMS,
                    expiryDate = null, profileId = activeProfileId.value,
                )
            }
            _logTarget.value = null
            if (loggedDuringFast) _loggedDuringFast.value = true
        }
    }
}

enum class OnlineSearchState { IDLE, LOADING, SUCCESS, EMPTY, ERROR }
