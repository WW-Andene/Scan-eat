package fr.scanneat.presentation.foodsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FOOD_DB_DAIRY_AND_LEGUMES
import fr.scanneat.domain.engine.nutrition.FOOD_DB_FATS_SWEETS_AND_MEALS
import fr.scanneat.domain.engine.nutrition.FOOD_DB_FRUITS_AND_VEGETABLES
import fr.scanneat.domain.engine.nutrition.FOOD_DB_GRAINS_AND_PROTEINS
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
 * Which accordion section a result groups under. Deliberately NOT ProductCategory
 * (SANDWICH/YOGURT/CHEESE/...) - that enum is retail-product-oriented and would
 * misclassify most whole foods (an apple, a lentil) as OTHER. FOOD_DB is already
 * hand-organized into exactly these four buckets across its own source files
 * (FoodDbFruitsAndVegetables.kt etc.) - reusing that real curation instead of
 * inventing a second, worse one via name-sniffing.
 */
enum class FoodSearchCategory { SCANNED, CUSTOM, FRUITS_VEGETABLES, GRAINS_PROTEINS, DAIRY_LEGUMES, FATS_SWEETS_BEVERAGES }

// Built once at class-init, not per search - same precompute-don't-repeat strategy
// FoodDb.kt's own NORMALIZED_FOOD_DB already uses for this exact list.
private val FOOD_DB_CATEGORY_BY_NAME: Map<String, FoodSearchCategory> =
    FOOD_DB_FRUITS_AND_VEGETABLES.associate { it.name to FoodSearchCategory.FRUITS_VEGETABLES } +
        FOOD_DB_GRAINS_AND_PROTEINS.associate { it.name to FoodSearchCategory.GRAINS_PROTEINS } +
        FOOD_DB_DAIRY_AND_LEGUMES.associate { it.name to FoodSearchCategory.DAIRY_LEGUMES } +
        FOOD_DB_FATS_SWEETS_AND_MEALS.associate { it.name to FoodSearchCategory.FATS_SWEETS_BEVERAGES }

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
               else FOOD_DB_CATEGORY_BY_NAME[name] ?: FoodSearchCategory.FATS_SWEETS_BEVERAGES,
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

    // searchByName(query="") still matches every row (SQL LIKE '%%') and is already
    // ordered most-recent-first, capped at 300 - a real, DB-level search, not a
    // client-side filter over some already-loaded "recent" window.
    private val scannedItems: Flow<List<FoodSearchItem>> = debouncedQuery
        .flatMapLatest { q -> scanRepo.searchHistory(q) }
        .map { results -> results.map { it.toItem() }.distinctBy { it.name.lowercase() } }

    private val localItems: Flow<List<FoodSearchItem>> = combine(debouncedQuery, customFoods) { q, customs ->
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
        combine(scannedItems, localItems, _filter) { scanned, local, f ->
            val scannedNames = scanned.map { it.name.lowercase() }.toSet()
            (scanned + local.filterNot { it.name.lowercase() in scannedNames })
                .filter { it.matches(f) }
                .groupBy { it.category }
                .mapValues { (_, items) -> items.sortedBy { it.name } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}
