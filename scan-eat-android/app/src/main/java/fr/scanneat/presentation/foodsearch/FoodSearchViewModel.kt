package fr.scanneat.presentation.foodsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

// Typing a nutrient word ("fiber"/"fibre", "protein"/"protéine", "iron"/"fer",
// "calcium") into the search box previously did nothing but a literal name
// search - no product in FOOD_DB or scan history is actually named "fiber",
// so the user got an empty result with no indication the HIGH_FIBER filter
// chip above the list was what they actually wanted. Recognizing these
// keywords and OR-ing in the matching nutrient threshold (on top of, not
// instead of, the normal name search) makes typing the nutrient work the
// same as tapping its filter chip.
private val NUTRIENT_KEYWORD_FILTER: Map<String, FoodSearchFilter> = mapOf(
    "fiber" to FoodSearchFilter.HIGH_FIBER, "fibre" to FoodSearchFilter.HIGH_FIBER,
    "fibres" to FoodSearchFilter.HIGH_FIBER,
    "protein" to FoodSearchFilter.HIGH_PROTEIN, "protéine" to FoodSearchFilter.HIGH_PROTEIN,
    "proteine" to FoodSearchFilter.HIGH_PROTEIN, "protéines" to FoodSearchFilter.HIGH_PROTEIN,
    "proteines" to FoodSearchFilter.HIGH_PROTEIN,
    "iron" to FoodSearchFilter.IRON_SOURCE, "fer" to FoodSearchFilter.IRON_SOURCE,
    "calcium" to FoodSearchFilter.CALCIUM_SOURCE,
)

private fun keywordFilterFor(query: String): FoodSearchFilter? =
    NUTRIENT_KEYWORD_FILTER[query.trim().lowercase()]

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
) : ViewModel() {

    private val customFoods: StateFlow<List<FoodEntry>> = customFoodRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _filter = MutableStateFlow(FoodSearchFilter.ALL)
    val filter: StateFlow<FoodSearchFilter> = _filter.asStateFlow()
    fun setFilter(f: FoodSearchFilter) { _filter.value = f }

    private val debouncedQuery = _query.debounce(150)

    // If the typed query is itself a nutrient keyword ("fiber", "protein", "iron",
    // "calcium"...), search by name for it would always come back empty - browse
    // the full dataset instead and let the nutrient-threshold filter below do the
    // matching, same as if the user had tapped the corresponding filter chip.
    private val effectiveSearchQuery: Flow<String> = debouncedQuery.map { q -> if (keywordFilterFor(q) != null) "" else q }

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
            // An explicitly-tapped filter chip always wins; a typed nutrient keyword
            // only kicks in while the chip row is still on its default ALL state.
            val effectiveFilter = if (f == FoodSearchFilter.ALL) keywordFilterFor(q) ?: f else f
            val scannedNames = scanned.map { it.name.lowercase() }.toSet()
            (scanned + local.filterNot { it.name.lowercase() in scannedNames })
                .filter { it.matches(effectiveFilter) }
                .groupBy { it.category }
                .mapValues { (_, items) -> items.sortedBy { it.name } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
