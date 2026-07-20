package fr.scanneat.presentation.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.planning.GroceryCheckedRepository
import fr.scanneat.data.repository.planning.ManualGroceryItem
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.planning.MealPlanSlot
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.data.repository.planning.MealTemplateRepository
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class CheckableGroceryItem(val item: GroceryItem, val checked: Boolean)

private fun Recipe.toGroceryInput(): GroceryRecipeInput =
    GroceryRecipeInput(name = name, components = components.map { c -> GroceryComponent(c.productName, c.grams) })

// Meal Templates were fully symmetric with Recipes everywhere else (AssignSlotDialog,
// dayCalories/weeklyTotalKcal, logging) but never contributed to the grocery list at
// all - a user who plans mainly via Templates got a shopping list silently missing
// everything from those meals. Same GroceryRecipeInput shape a Recipe already maps to.
private fun MealTemplate.toGroceryInput(): GroceryRecipeInput =
    GroceryRecipeInput(name = name, components = items.map { i -> GroceryComponent(i.productName, i.grams) })

private val PLAN_MEALS = listOf("breakfast", "lunch", "dinner", "snack")

@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val recipeRepo: RecipeRepository,
    private val templateRepo: MealTemplateRepository,
    private val checkedRepo: GroceryCheckedRepository,
    private val mealPlanRepo: MealPlanRepository,
    private val manualGroceryRepo: ManualGroceryRepository,
    private val prefs: UserPreferences,
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
    private val templates: Flow<List<MealTemplate>> = templateRepo.observeAll()

    // weekDates() defaults its startDate to LocalDate.now() evaluated only when this
    // flow's map block actually re-executes - which previously happened only when
    // mealPlanRepo.weekPlan itself emitted (a plan edit), not when time passed. A
    // screen left open across midnight with no plan edit kept scoring against the
    // same 7-day window forever. Same poll+distinctUntilChanged fix already applied
    // to MealPlanViewModel's own weekDates.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /** recipeId -> number of slots it occupies within the current week's plan. */
    private val plannedRecipeCounts: Flow<Map<String, Int>> = combine(mealPlanRepo.weekPlan, today) { plan, date ->
        val weekDates = mealPlanRepo.weekDates(date).toSet()
        val counts = mutableMapOf<String, Int>()
        for ((d, day) in plan) {
            if (d !in weekDates) continue
            for (meal in PLAN_MEALS) {
                val slot = day[meal]
                if (slot is MealPlanSlot.RecipeSlot) counts[slot.id] = (counts[slot.id] ?: 0) + 1
            }
        }
        counts
    }

    /** Same as [plannedRecipeCounts] but for MealPlanSlot.TemplateSlot. */
    private val plannedTemplateCounts: Flow<Map<String, Int>> = combine(mealPlanRepo.weekPlan, today) { plan, date ->
        val weekDates = mealPlanRepo.weekDates(date).toSet()
        val counts = mutableMapOf<String, Int>()
        for ((d, day) in plan) {
            if (d !in weekDates) continue
            for (meal in PLAN_MEALS) {
                val slot = day[meal]
                if (slot is MealPlanSlot.TemplateSlot) counts[slot.id] = (counts[slot.id] ?: 0) + 1
            }
        }
        counts
    }

    // Unscoped aggregation over every saved recipe/template — kept around purely as
    // the superset used to validate persisted checked-item keys (see init below), so
    // toggling scopeToPlanned on/off never discards check-off state for an
    // ingredient that's just temporarily out of view, only for one that's truly
    // gone (recipe/template deleted/edited).
    private val allRecipeItems: StateFlow<List<GroceryItem>> = combine(recipes, templates, manualGroceryRepo.asRecipeInputs) { recipeList, templateList, manual ->
        aggregateGroceryList(recipeList.map { it.toGroceryInput() } + templateList.map { it.toGroceryInput() } + manual)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A shared StateFlow so groceryItems and checkableItems don't each trigger their
    // own Room query + aggregateGroceryList() run for the same recipe/template change.
    // Manual items (added ad-hoc, e.g. from a scanned product's "Save to..." popup)
    // always show regardless of the planned-week scope - they were never tied to a
    // recipe/template or meal-plan slot in the first place.
    private val rawItems: StateFlow<List<GroceryItem>> = combine(
        combine(recipes, plannedRecipeCounts, ::Pair),
        combine(templates, plannedTemplateCounts, ::Pair),
        _scopeToPlanned,
        manualGroceryRepo.asRecipeInputs,
    ) { (recipeList, recipeCounts), (templateList, templateCounts), scoped, manual ->
        val inputs = if (!scoped) {
            recipeList.map { it.toGroceryInput() } + templateList.map { it.toGroceryInput() }
        } else {
            recipeList.flatMap { r -> List(recipeCounts[r.id] ?: 0) { r.toGroceryInput() } } +
                templateList.flatMap { t -> List(templateCounts[t.id] ?: 0) { t.toGroceryInput() } }
        }
        aggregateGroceryList(inputs + manual)
    }
        // Room's Flow reuses whatever dispatcher is already active in the collecting
        // coroutine rather than always using its background query executor - without
        // this, both the query and aggregateGroceryList()'s normalize/sort work ran on
        // Main, since viewModelScope defaults to Dispatchers.Main.immediate.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortAlpha = MutableStateFlow(false)
    val sortAlpha: StateFlow<Boolean> = _sortAlpha.asStateFlow()
    fun toggleSortAlpha() { _sortAlpha.value = !_sortAlpha.value }

    // aggregateGroceryList() only ever produced one flat list with no aisle
    // sectioning at all - groceryCategoryFor() is a pure classifier the screen
    // applies client-side when this is on, so no new query/state shape needed.
    private val _groupByAisle = MutableStateFlow(false)
    val groupByAisle: StateFlow<Boolean> = _groupByAisle.asStateFlow()
    fun toggleGroupByAisle() { _groupByAisle.value = !_groupByAisle.value }

    val groceryItems: StateFlow<List<GroceryItem>> = combine(rawItems, _sortAlpha) { items, alpha ->
        if (alpha) items.sortedBy { it.name.lowercase() } else items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * item key -> short warning, same checkDiet()/checkUserAllergens() reused
     * from Recipes' identical fix - the grocery list previously ran no diet/
     * allergen check at all, so a vegan or allergic user's shopping list could
     * silently include an ingredient their own profile forbids.
     */
    val itemWarnings: StateFlow<Map<String, String>> = combine(rawItems, prefs.profile, prefs.language) { items, profile, lang ->
        items.mapNotNull { item ->
            val product = item.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else item.key to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Same list, annotated with persisted check-off state so the UI can tick items
     * while shopping. Built from [groceryItems] (which already applies the Sort A-Z
     * toggle), not raw [rawItems] directly - GroceryScreen renders this list, and it
     * previously ignored [_sortAlpha] entirely, so toggling "Sort A-Z" changed the
     * icon's tint but never actually reordered the on-screen checklist.
     */
    val checkableItems: StateFlow<List<CheckableGroceryItem>> = combine(groceryItems, checkedRepo.checkedKeys) { items, checked ->
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
    // Every write below previously called checkedRepo's/manualGroceryRepo's DataStore/
    // Room writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a write
    // failure here wasn't just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    init {
        viewModelScope.launch {
            // drop(1): allRecipeItems is a StateFlow seeded with emptyList() before the
            // underlying Room query has actually run - acting on that placeholder
            // would prune every persisted checked key away the instant this
            // ViewModel is created, before the real recipe list even loads.
            allRecipeItems.drop(1).collect { items ->
                runCatching { checkedRepo.pruneToKeys(items.map { it.key }.toSet()) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
            }
        }
    }

    fun toggleChecked(item: GroceryItem, checked: Boolean) {
        // Keyed by GroceryItem.key (the aggregation's stable normalized identity),
        // not the display name - two differently-spelled recipe ingredients that
        // aggregate to the same item, or a renamed recipe changing which spelling
        // gets picked as `name`, would otherwise silently orphan the persisted
        // checked-state key and un-check a previously-checked item.
        viewModelScope.launch { runCatching { checkedRepo.setChecked(item.key, checked) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun clearAllChecked() {
        viewModelScope.launch { runCatching { checkedRepo.clearAll() }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
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
    /** Add a free-text item directly from the grocery screen's inline input row. */
    fun quickAdd(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { runCatching { manualGroceryRepo.add(name.trim(), 0.0) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    fun deleteManualContribution(groceryKey: String) {
        viewModelScope.launch {
            runCatching {
                val existingKeys = rawItems.value.map { it.key }.toSet()
                manualGroceryRepo.items.first()
                    .filter { canonicalGroceryKey(it.name, existingKeys) == groceryKey }
                    .forEach { manualGroceryRepo.remove(it.id) }
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
