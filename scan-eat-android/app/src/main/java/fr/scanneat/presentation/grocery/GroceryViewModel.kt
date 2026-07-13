package fr.scanneat.presentation.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.ManualGroceryItem
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.planning.MealPlanSlot
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.planning.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckableGroceryItem(val item: GroceryItem, val checked: Boolean)

private fun Recipe.toGroceryInput(): GroceryRecipeInput =
    GroceryRecipeInput(name = name, components = components.map { c -> GroceryComponent(c.productName, c.grams) })

private val PLAN_MEALS = listOf("breakfast", "lunch", "dinner", "snack")

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val recipeRepo: RecipeRepository,
    private val checkedRepo: GroceryCheckedRepository,
    private val mealPlanRepo: MealPlanRepository,
    private val manualGroceryRepo: ManualGroceryRepository,
) : ViewModel() {

    // "Planned this week only" scope: when on, the grocery list is built from the
    // recipes actually assigned to a day in the current 7-day meal plan window
    // (MealPlanRepository/MealPlanScreen), not from every recipe ever saved. A
    // recipe planned on multiple days contributes its ingredients that many times,
    // so quantities reflect how much is really needed to cook the week, not just
    // one serving of everything in the recipe book.
    private val _scopeToPlanned = MutableStateFlow(false)
    val scopeToPlanned: StateFlow<Boolean> = _scopeToPlanned.asStateFlow()

    fun setScopeToPlanned(enabled: Boolean) {
        _scopeToPlanned.value = enabled
    }

    private val recipes: Flow<List<Recipe>> = recipeRepo.observeAll()

    /** recipeId -> number of slots it occupies within the current week's plan. */
    private val plannedRecipeCounts: Flow<Map<String, Int>> = mealPlanRepo.weekPlan.map { plan ->
        val weekDates = mealPlanRepo.weekDates().toSet()
        val counts = mutableMapOf<String, Int>()
        for ((date, day) in plan) {
            if (date !in weekDates) continue
            for (meal in PLAN_MEALS) {
                val slot = day[meal]
                if (slot is MealPlanSlot.RecipeSlot) counts[slot.id] = (counts[slot.id] ?: 0) + 1
            }
        }
        counts
    }

    // Unscoped aggregation over every saved recipe — kept around purely as the
    // superset used to validate persisted checked-item keys (see init below), so
    // toggling scopeToPlanned on/off never discards check-off state for an
    // ingredient that's just temporarily out of view, only for one that's truly
    // gone (recipe deleted/edited).
    private val allRecipeItems: StateFlow<List<GroceryItem>> = combine(recipes, manualGroceryRepo.asRecipeInputs) { list, manual ->
        aggregateGroceryList(list.map { it.toGroceryInput() } + manual)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A shared StateFlow so groceryItems and checkableItems don't each trigger their
    // own Room query + aggregateGroceryList() run for the same recipe change. Manual
    // items (added ad-hoc, e.g. from a scanned product's "Save to..." popup) always
    // show regardless of the planned-week scope - they were never tied to a recipe
    // or meal-plan slot in the first place.
    private val rawItems: StateFlow<List<GroceryItem>> = combine(recipes, plannedRecipeCounts, _scopeToPlanned, manualGroceryRepo.asRecipeInputs) { list, counts, scoped, manual ->
        val inputs = if (!scoped) {
            list.map { it.toGroceryInput() }
        } else {
            list.flatMap { r -> List(counts[r.id] ?: 0) { r.toGroceryInput() } }
        }
        aggregateGroceryList(inputs + manual)
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

    // Self-healing: whenever the *unscoped* aggregated grocery list changes (recipe
    // added/edited/removed), drop any persisted checked key that no longer
    // corresponds to a real item in any recipe, so stale keys don't accumulate in
    // DataStore forever. Deliberately validated against allRecipeItems (every saved
    // recipe) rather than the possibly-scoped rawItems, so switching scopeToPlanned
    // on doesn't itself get treated as "these ingredients no longer exist" and wipe
    // their checked state.
    init {
        viewModelScope.launch {
            // drop(1): allRecipeItems is a StateFlow seeded with emptyList() before the
            // underlying Room query has actually run - acting on that placeholder
            // would prune every persisted checked key away the instant this
            // ViewModel is created, before the real recipe list even loads.
            allRecipeItems.drop(1).collect { items -> checkedRepo.pruneToKeys(items.map { it.key }.toSet()) }
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

    // ManualGroceryRepository.remove() previously had zero callers anywhere in the
    // app — an ad-hoc item (e.g. "Save to grocery" from a scanned product) could
    // only ever be hidden by checking it off, never actually removed, so it
    // stayed in the list forever. GroceryItem.key uses the same normalized-name
    // identity aggregateGroceryList() builds it from, so a manual item can be
    // matched back to (and deleted from) the aggregated row it contributes to
    // without needing the aggregation itself to carry per-source ids.
    val manualItemKeys: StateFlow<Set<String>> = combine(manualGroceryRepo.items, rawItems) { list, items ->
        val existingKeys = items.map { it.key }.toSet()
        list.map { canonicalGroceryKey(it.name, existingKeys) }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Removes every manual entry that contributed to the aggregated row keyed [groceryKey] — a recipe-sourced contribution to the same row, if any, is untouched. */
    fun deleteManualContribution(groceryKey: String) {
        viewModelScope.launch {
            val existingKeys = rawItems.value.map { it.key }.toSet()
            manualGroceryRepo.items.first()
                .filter { canonicalGroceryKey(it.name, existingKeys) == groceryKey }
                .forEach { manualGroceryRepo.remove(it.id) }
        }
    }
}
