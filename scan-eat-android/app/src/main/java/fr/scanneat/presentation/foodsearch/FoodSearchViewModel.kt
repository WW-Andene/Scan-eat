package fr.scanneat.presentation.foodsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.model.Product
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/** Simple threshold-based filters over FoodEntry's own fields — FoodEntry carries
 * no category of its own (see CustomFoodRepository.toProduct()'s own comment), so
 * "filters" here are nutrient-based rather than category-based. */
enum class FoodSearchFilter { ALL, HIGH_PROTEIN, LOW_CARB, HIGH_FIBER, IRON_SOURCE, CALCIUM_SOURCE }

private fun FoodEntry.matches(filter: FoodSearchFilter): Boolean = when (filter) {
    FoodSearchFilter.ALL            -> true
    FoodSearchFilter.HIGH_PROTEIN   -> proteinG >= 15.0
    FoodSearchFilter.LOW_CARB       -> carbsG <= 10.0
    FoodSearchFilter.HIGH_FIBER     -> fiberG >= 3.0
    FoodSearchFilter.IRON_SOURCE    -> ironMg >= 2.0
    FoodSearchFilter.CALCIUM_SOURCE -> calciumMg >= 100.0
}

/**
 * "Recherche" — a full browse/search engine over the app's own food database
 * (the ~130-entry curated FOOD_DB used elsewhere only as a 6-10-result Quick Add
 * autocomplete, plus the user's own custom foods), not just a narrow autocomplete
 * dropdown. Empty query browses the whole (filtered) database, alphabetically;
 * typing narrows it via the same searchFoodDB ranking Quick Add uses, just with a
 * much higher result cap.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodSearchViewModel @Inject constructor(
    private val customFoodRepo: CustomFoodRepository,
) : ViewModel() {

    private val customFoods: StateFlow<List<FoodEntry>> = customFoodRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    private val _filter = MutableStateFlow(FoodSearchFilter.ALL)
    val filter: StateFlow<FoodSearchFilter> = _filter.asStateFlow()
    fun setFilter(f: FoodSearchFilter) { _filter.value = f }

    // No result cap worth mentioning (200) - this is meant to be an actual browse/
    // search tool over the whole database, not a 6-result autocomplete dropdown.
    val results: StateFlow<List<FoodEntry>> =
        combine(_query.debounce(150), _filter, customFoods) { q, f, customs -> Triple(q, f, customs) }
            .map { (q, f, customs) ->
                val base = if (q.isBlank()) {
                    (customs + FOOD_DB).distinctBy { it.name }.sortedBy { it.name }
                } else {
                    searchFoodDB(q, limit = 200, extraFoods = customs)
                }
                base.filter { it.matches(f) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toProduct(entry: FoodEntry): Product = customFoodRepo.toProduct(entry)
}
