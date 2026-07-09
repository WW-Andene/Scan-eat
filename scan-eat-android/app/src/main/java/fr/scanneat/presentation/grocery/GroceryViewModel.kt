package fr.scanneat.presentation.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.planning.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class GroceryViewModel @Inject constructor(repo: RecipeRepository) : ViewModel() {

    val groceryItems: StateFlow<List<GroceryItem>> = repo.observeAll()
        .map { recipes ->
            aggregateGroceryList(recipes.map { r ->
                GroceryRecipeInput(
                    name       = r.name,
                    components = r.components.map { c -> GroceryComponent(c.productName, c.grams) },
                )
            })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
