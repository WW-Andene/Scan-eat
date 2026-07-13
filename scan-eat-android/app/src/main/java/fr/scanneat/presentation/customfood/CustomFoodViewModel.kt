package fr.scanneat.presentation.customfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    private val repo: CustomFoodRepository,
) : ViewModel() {

    val foods: StateFlow<List<FoodEntry>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** id-keyed, for the rename action — FoodEntry itself carries no stable id. */
    val foodsWithId: StateFlow<List<Pair<String, FoodEntry>>> = repo.observeAllWithId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Derived from the same live `foods` flow (not a separate repo.search() call), so
    // results can never go stale after a save/delete the way a one-shot search snapshot
    // could — and debounced + recomputed via combine instead of launching an unbounded,
    // uncancelled coroutine per keystroke.
    val searchResults: StateFlow<List<FoodEntry>> =
        combine(_query.debounce(200), foods) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 10, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun save(
        name: String,
        kcal: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double = 0.0,
        saltG: Double = 0.0,
    ) {
        viewModelScope.launch {
            repo.save(name = name, kcal = kcal, proteinG = proteinG,
                      carbsG = carbsG, fatG = fatG, fiberG = fiberG, saltG = saltG)
        }
    }

    fun delete(name: String) {
        viewModelScope.launch { repo.deleteByName(name) }
    }

    fun rename(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.rename(id, newName) }
    }
}
