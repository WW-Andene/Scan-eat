package fr.scanneat.presentation.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.model.MealSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val consumptionRepo: ConsumptionRepository,
) : ViewModel() {
    val recipes: StateFlow<List<Recipe>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(name: String, components: List<RecipeComponent>, servings: Int = 1) {
        viewModelScope.launch { repo.save(name, components, servings) }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun log(recipe: Recipe, mealSlot: MealSlot, portionFraction: Double = 1.0) {
        viewModelScope.launch {
            consumptionRepo.log(repo.collapse(recipe, LocalDate.now(), mealSlot, portionFraction))
        }
    }
}
