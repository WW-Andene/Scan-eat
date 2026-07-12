package fr.scanneat.presentation.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.planning.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckableGroceryItem(val item: GroceryItem, val checked: Boolean)

@HiltViewModel
class GroceryViewModel @Inject constructor(
    repo: RecipeRepository,
    private val checkedRepo: GroceryCheckedRepository,
) : ViewModel() {

    private val rawItems: Flow<List<GroceryItem>> = repo.observeAll()
        .map { recipes ->
            aggregateGroceryList(recipes.map { r ->
                GroceryRecipeInput(
                    name       = r.name,
                    components = r.components.map { c -> GroceryComponent(c.productName, c.grams) },
                )
            })
        }

    val groceryItems: StateFlow<List<GroceryItem>> = rawItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Same list, annotated with persisted check-off state so the UI can tick items while shopping. */
    val checkableItems: StateFlow<List<CheckableGroceryItem>> = combine(rawItems, checkedRepo.checkedKeys) { items, checked ->
        items.map { CheckableGroceryItem(it, checked = it.name in checked) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleChecked(item: GroceryItem, checked: Boolean) {
        viewModelScope.launch { checkedRepo.setChecked(item.name, checked) }
    }

    fun clearAllChecked() {
        viewModelScope.launch { checkedRepo.clearAll() }
    }
}
