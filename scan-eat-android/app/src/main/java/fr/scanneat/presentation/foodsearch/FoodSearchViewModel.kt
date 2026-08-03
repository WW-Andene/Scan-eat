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

// Explicit per-item classification, built once at class-init (not per search) -
// unlike the old per-source-file assoc, this actually reflects each item's real
// food group rather than which of the four FoodDb*.kt files it happened to be
// declared in.
private val FOOD_DB_CATEGORY_BY_NAME: Map<String, FoodSearchCategory> = buildMap {
    listOf(
        "pomme", "banane", "orange", "fraise", "myrtille", "avocat", "kiwi", "raisin",
        "pêche", "poire", "ananas", "mangue", "pastèque", "melon", "cerise", "framboise",
        "mûre", "abricot", "prune", "pamplemousse", "citron", "clémentine", "figue",
        "datte", "noix de coco",
        "fruit de la passion", "litchi", "nectarine", "rhubarbe", "groseille", "goyave",
        "papaye", "fruit du dragon",
    ).forEach { put(it, FoodSearchCategory.FRUITS) }

    listOf(
        "tomate", "carotte", "brocoli", "épinard", "concombre", "courgette", "poivron",
        "oignon", "salade verte", "pomme de terre", "chou-fleur", "chou",
        "chou de bruxelles", "aubergine", "haricot vert", "petit pois", "asperge",
        "champignon", "betterave", "radis", "céleri", "poireau", "artichaut",
        "patate douce", "maïs", "ail",
        "fenouil", "panais", "navet", "endive", "roquette", "courge butternut",
        "potiron", "germe de soja", "topinambour",
    ).forEach { put(it, FoodSearchCategory.VEGETABLES) }

    listOf(
        "riz blanc cuit", "pâtes cuites", "pain blanc", "pain complet", "baguette",
        "croissant", "avoine", "quinoa cuit", "riz complet cuit", "semoule cuite",
        "boulgour cuit", "sarrasin cuit", "pain de mie", "tortilla de blé",
        "riz basmati cuit", "pain au levain", "galette de sarrasin", "muesli",
        "céréales petit-déjeuner", "pain pita", "polenta cuite", "millet cuit",
    ).forEach { put(it, FoodSearchCategory.GRAINS_STARCHES) }

    listOf(
        "poulet rôti", "boeuf haché 5%", "boeuf haché 15%", "saumon", "thon", "oeuf",
        "jambon blanc", "dinde", "porc", "agneau", "canard", "crevette", "moules",
        "cabillaud", "maquereau", "sardine", "tofu", "jambon cru", "saucisse", "bacon",
        "steak de boeuf", "escalope de veau", "foie de veau", "lapin", "pintade",
        "lieu noir", "truite", "merlan", "calamar", "poulpe", "seitan", "oeuf de caille",
    ).forEach { put(it, FoodSearchCategory.PROTEINS) }

    listOf(
        "lentille cuite", "pois chiche cuit", "amandes", "noix", "haricot rouge cuit",
        "haricot blanc cuit", "edamame", "noisette", "noix de cajou", "pistache",
        "graine de chia", "graine de lin", "beurre de cacahuète", "cacahuète",
        "fève cuite", "soja cuit", "noix du brésil", "noix de pécan",
        "graine de tournesol", "graine de courge", "beurre d'amande",
    ).forEach { put(it, FoodSearchCategory.LEGUMES_NUTS_SEEDS) }

    listOf(
        "lait demi-écrémé", "yaourt nature", "skyr", "fromage blanc 0%", "emmental",
        "camembert", "fromage de chèvre", "mozzarella", "feta", "parmesan",
        "lait entier", "crème fraîche", "lait de soja", "lait d'amande",
        "fromage cottage", "ricotta", "fromage à raclette", "kéfir", "lait d'avoine",
        "yaourt grec",
    ).forEach { put(it, FoodSearchCategory.DAIRY) }

    listOf(
        "huile d'olive", "beurre", "huile de colza", "huile de coco", "margarine", "mayonnaise",
        "huile de tournesol", "huile de sésame", "huile de lin", "saindoux", "beurre demi-sel",
    ).forEach { put(it, FoodSearchCategory.FATS_OILS) }

    listOf(
        "chocolat noir 70%", "chocolat au lait", "biscuit", "miel", "pâte à tartiner",
        "confiture", "chips", "pop-corn", "glace", "crêpe nature",
        "barre chocolatée", "bonbon", "sirop d'érable", "sucre blanc", "gaufre",
        "madeleine", "pain d'épices", "fruits secs mélangés",
    ).forEach { put(it, FoodSearchCategory.SWEETS_SNACKS) }

    listOf(
        "café noir", "thé", "jus d'orange", "coca-cola", "bière", "vin rouge",
        "jus de pomme", "eau gazeuse", "lait chocolaté",
        "kombucha", "jus de raisin", "smoothie fruits", "boisson énergisante",
        "champagne", "whisky", "lait de riz",
    ).forEach { put(it, FoodSearchCategory.BEVERAGES) }

    listOf(
        "pizza margherita", "hamburger", "frites", "sushi saumon", "houmous",
        "falafel", "quiche lorraine", "lasagne",
        "ratatouille", "couscous royal", "chili con carne", "curry de poulet",
        "risotto", "paella", "gratin dauphinois", "soupe de légumes",
        "sandwich jambon-beurre", "burrito", "ramen", "pad thaï", "kebab",
        "taboulé", "gaspacho",
    ).forEach { put(it, FoodSearchCategory.PREPARED_MEALS) }
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

private fun FoodEntry.toItem(isCustom: Boolean) = FoodSearchItem(
    name = name, kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
    fiberG = fiberG, saltG = saltG, ironMg = ironMg, calciumMg = calciumMg, vitDUg = vitDUg, b12Ug = b12Ug,
    category = if (isCustom) FoodSearchCategory.CUSTOM
               else FOOD_DB_CATEGORY_BY_NAME[name] ?: FoodSearchCategory.OTHER,
)

private fun ScanResult.toItem(): FoodSearchItem {
    val n = product.nutrition
    return FoodSearchItem(
        name = product.name, kcal = n.energyKcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG,
        fiberG = n.fiberG, saltG = n.saltG,
        ironMg = n.ironMg ?: 0.0, calciumMg = n.calciumMg ?: 0.0, vitDUg = n.vitDUg ?: 0.0, b12Ug = n.b12Ug ?: 0.0,
        category = FoodSearchCategory.SCANNED, grade = audit.grade, scanId = dbId,
    )
}

private fun FoodSearchItem.matches(filter: FoodSearchFilter): Boolean = when (filter) {
    FoodSearchFilter.ALL            -> true
    FoodSearchFilter.HIGH_PROTEIN   -> proteinG >= 15.0
    FoodSearchFilter.LOW_CARB       -> carbsG <= 10.0
    FoodSearchFilter.HIGH_FIBER     -> fiberG >= 3.0
    FoodSearchFilter.IRON_SOURCE    -> ironMg >= 2.0
    FoodSearchFilter.CALCIUM_SOURCE -> calciumMg >= 100.0
}

/**
 * One nutrient's alias words (French/English) plus its accessor on
 * [FoodSearchItem] and, when meaningful, a "high X" default threshold used
 * when the nutrient is typed bare with no comparison ("protéine" alone means
 * "high protein", same as tapping the HIGH_PROTEIN chip). Nutrients with no
 * sensible bare-word meaning (kcal, carbs, vitamins) require an explicit
 * operator+number instead (see [NUTRIENT_QUERY_REGEX]).
 */
private class NutrientAlias(val getter: (FoodSearchItem) -> Double, val highThreshold: Double?)

private val NUTRIENT_ALIASES: Map<String, NutrientAlias> = buildMap {
    fun reg(names: List<String>, highThreshold: Double?, getter: (FoodSearchItem) -> Double) {
        val alias = NutrientAlias(getter, highThreshold)
        names.forEach { put(it, alias) }
    }
    reg(listOf("kcal", "calorie", "calories", "energie", "énergie"), null) { it.kcal }
    reg(listOf("protein", "proteine", "proteines", "protéine", "protéines"), 15.0) { it.proteinG }
    reg(listOf("carb", "carbs", "glucide", "glucides"), null) { it.carbsG }
    reg(listOf("fat", "gras", "lipide", "lipides", "matieres grasses", "matières grasses"), 17.5) { it.fatG }
    reg(listOf("fiber", "fibre", "fibres"), 3.0) { it.fiberG }
    reg(listOf("salt", "sel", "sodium"), 1.5) { it.saltG }
    reg(listOf("iron", "fer"), 2.0) { it.ironMg }
    reg(listOf("calcium"), 100.0) { it.calciumMg }
    reg(listOf("vitamine d", "vitamined", "vitd"), null) { it.vitDUg }
    reg(listOf("b12", "vitamine b12", "vitaminb12"), null) { it.b12Ug }
}

// Recognizes "<nutrient> <operator> <number>", e.g. "sucre<5", "sodium >= 200",
// "protéine > 20" - a real threshold query over ANY nutrient FoodSearchItem
// exposes, not just the 5 fixed filter chips above. Falls back, for a bare
// nutrient word with no operator, to that nutrient's own "high X" default
// (matching the old keyword-only behavior for fiber/protein/iron/calcium, now
// extended to fat/salt too).
private val NUTRIENT_QUERY_REGEX =
    Regex("""^\s*([a-zàâäéèêëïîôöùûüç ]+?)\s*(<=|>=|<|>)\s*(\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)

private fun predicateFor(query: String): ((FoodSearchItem) -> Boolean)? {
    val trimmed = query.trim().lowercase()
    NUTRIENT_QUERY_REGEX.matchEntire(trimmed)?.let { m ->
        val (rawName, op, rawNum) = m.destructured
        val alias = NUTRIENT_ALIASES[rawName.trim()] ?: return null
        val num = rawNum.replace(',', '.').toDoubleOrNull() ?: return null
        return { item: FoodSearchItem ->
            val v = alias.getter(item)
            when (op) { "<" -> v < num; "<=" -> v <= num; ">" -> v > num; else -> v >= num }
        }
    }
    val alias = NUTRIENT_ALIASES[trimmed] ?: return null
    val threshold = alias.highThreshold ?: return null
    return { item: FoodSearchItem -> alias.getter(item) >= threshold }
}

/** One external reference link offered for the current query - opened in the
 *  device's browser, never fetched/scraped in-app. */
data class SourceLink(val label: String, val url: String)

/** Which of Products/Links/Both the "Recherche" screen currently shows -
 *  cycled by a single button (see FoodSearchViewModel.cycleDisplayMode). */
enum class SearchDisplayMode { PRODUCTS, LINKS, BOTH }

/**
 * A handful of well-known, query-parameterized reference sites - not a scraped
 * or fabricated result list, just the same "search on X" links a user could
 * type into their browser by hand, offered up front for whatever they typed
 * here (a food name, an additive code like "E330", a molecule). Wikipedia's
 * domain follows the app's own language so results come back in the right
 * language rather than always French.
 */
private fun buildSourceLinks(query: String, lang: String): List<SourceLink> {
    if (query.isBlank()) return emptyList()
    val q = java.net.URLEncoder.encode(query, "UTF-8")
    val wikiDomain = if (lang == "en") "en.wikipedia.org" else "fr.wikipedia.org"
    return listOf(
        SourceLink("Wikipédia", "https://$wikiDomain/wiki/Special:Search?search=$q"),
        SourceLink("Open Food Facts", "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q"),
        SourceLink("PubChem", "https://pubchem.ncbi.nlm.nih.gov/#query=$q"),
        SourceLink("ANSES – Table CIQUAL", "https://ciqual.anses.fr/#/aliments?query=$q"),
    )
}

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
            onlineRaw = results
            // toItem() sets scanId = dbId, which defaults to 0 (not null) for a
            // ScanResult that was never persisted - left as-is, FoodSearchRow's
            // `item.scanId != null` check would treat 0 as "already in this
            // user's history" and call onOpenResult(0) instead of the
            // online-persist path below. Forced back to null here since these
            // results are never actually in scan_history yet.
            _onlineResults.value = results.map { it.toItem().copy(scanId = null, barcode = it.barcode) }
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
        viewModelScope.launch { onOpened(scanRepo.persist(raw)) }
    }
}

enum class OnlineSearchState { IDLE, LOADING, SUCCESS, EMPTY, ERROR }
