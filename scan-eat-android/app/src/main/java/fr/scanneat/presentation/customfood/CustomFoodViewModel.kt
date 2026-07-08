package fr.scanneat.presentation.customfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.CustomFoodRepository
import fr.scanneat.domain.engine.FoodEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomFoodViewModel @Inject constructor(
    private val repo: CustomFoodRepository,
) : ViewModel() {

    val foods: StateFlow<List<FoodEntry>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<FoodEntry>>(emptyList())
    val searchResults: StateFlow<List<FoodEntry>> = _searchResults.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = repo.search(q, limit = 10)
        }
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
}
