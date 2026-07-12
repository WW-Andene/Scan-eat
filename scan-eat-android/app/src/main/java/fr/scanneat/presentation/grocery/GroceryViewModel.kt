package fr.scanneat.presentation.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.planning.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckableGroceryItem(val item: GroceryItem, val checked: Boolean)

@HiltViewModel
class GroceryViewModel @Inject constructor(
    repo: RecipeRepository,
    private val checkedRepo: GroceryCheckedRepository,
) : ViewModel() {

    // A shared StateFlow so groceryItems and checkableItems don't each trigger their
    // own Room query + aggregateGroceryList() run for the same recipe change.
    private val rawItems: StateFlow<List<GroceryItem>> = repo.observeAll()
        .map { recipes ->
            aggregateGroceryList(recipes.map { r ->
                GroceryRecipeInput(
                    name       = r.name,
                    components = r.components.map { c -> GroceryComponent(c.productName, c.grams) },
                )
            })
        }
        // Room's Flow reuses whatever dispatcher is already active in the collecting
        // coroutine rather than always using its background query executor - without
        // this, both the query and aggregateGroceryList()'s normalize/sort work ran on
        // Main, since viewModelScope defaults to Dispatchers.Main.immediate.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groceryItems: StateFlow<List<GroceryItem>> = rawItems

    /** Same list, annotated with persisted check-off state so the UI can tick items while shopping. */
    val checkableItems: StateFlow<List<CheckableGroceryItem>> = combine(rawItems, checkedRepo.checkedKeys) { items, checked ->
        items.map { CheckableGroceryItem(it, checked = it.key in checked) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * "checked / total" counts for a shopping-progress readout (e.g. "3/12 checked").
     * Intersected against the *current* item keys rather than using checkedRepo's
     * raw checkedCount directly - that count previously included every key ever
     * checked, even ones for ingredients no longer in any recipe (recipe edited/
     * deleted since), so the readout could show more checked items than the list
     * actually has (e.g. "5/3 checked") instead of capping at the list size.
     */
    val checkedProgress: StateFlow<Pair<Int, Int>> = combine(rawItems, checkedRepo.checkedKeys) { items, checked ->
        val validKeys = items.map { it.key }.toSet()
        checked.count { it in validKeys } to items.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to 0)

    // Self-healing: whenever the aggregated grocery list changes (recipe added/
    // edited/removed), drop any persisted checked key that no longer corresponds
    // to a real item, so stale keys don't accumulate in DataStore forever and the
    // checked/total readout above never needs a defensive intersect to look right.
    init {
        viewModelScope.launch {
            // drop(1): rawItems is a StateFlow seeded with emptyList() before the
            // underlying Room query has actually run - acting on that placeholder
            // would prune every persisted checked key away the instant this
            // ViewModel is created, before the real recipe list even loads.
            rawItems.drop(1).collect { items -> checkedRepo.pruneToKeys(items.map { it.key }.toSet()) }
        }
    }

    fun toggleChecked(item: GroceryItem, checked: Boolean) {
        // Keyed by GroceryItem.key (the aggregation's stable normalized identity),
        // not the display name - two differently-spelled recipe ingredients that
        // aggregate to the same item, or a renamed recipe changing which spelling
        // gets picked as `name`, would otherwise silently orphan the persisted
        // checked-state key and un-check a previously-checked item.
        viewModelScope.launch { checkedRepo.setChecked(item.key, checked) }
    }

    fun clearAllChecked() {
        viewModelScope.launch { checkedRepo.clearAll() }
    }
}
