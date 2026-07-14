package fr.scanneat.presentation.mealplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.planning.DayPlan
import fr.scanneat.data.repository.planning.MealPlanRepository
import fr.scanneat.data.repository.planning.MealPlanSlot
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.data.repository.planning.MealTemplateRepository
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.model.MealSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val repo: MealPlanRepository,
    private val prefs: UserPreferences,
    private val recipeRepo: RecipeRepository,
    private val templateRepo: MealTemplateRepository,
    private val consumptionRepo: ConsumptionRepository,
) : ViewModel() {
    // A fixed `val` captured LocalDate.now() once at construction - a ViewModel
    // that outlives midnight would keep the same 7 dates forever, so "today"
    // stops being highlighted and the week silently goes a day stale. Polling
    // + distinctUntilChanged only recomputes when the start date actually rolls
    // over, same fix HydrationViewModel's `intake` applies.
    val weekDates: StateFlow<List<LocalDate>> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()
        .map { repo.weekDates(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.weekDates())

    val weekPlan: StateFlow<Map<LocalDate, DayPlan>> = repo.weekPlan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Saved recipes/templates, for the "assign to this slot" picker below — the
    // repository has always been able to store a RecipeSlot/TemplateSlot
    // (see MealPlanSlot, MealPlanRepository.serialize/deserialize), but until
    // now nothing in the UI ever constructed one: MealPlanScreen only offered
    // free-text notes, so planning "chicken curry (recipe)" onto Tuesday
    // dinner was impossible even though the data model and the day-view
    // rendering (icons for RecipeSlot/TemplateSlot) both already assumed it
    // existed.
    val recipes: StateFlow<List<Recipe>> = recipeRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val templates: StateFlow<List<MealTemplate>> = templateRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Improvement: per-day calorie totals derived from assigned recipe/template slots.
    // Previously the day header showed only the date — a user had no way to see at a
    // glance whether a planned day meets their calorie target without opening the diary.
    // weekPlan holds up to ~13 days (MealPlanRepository only prunes entries older than
    // KEEP_DAYS_PAST=7, it isn't limited to the displayed forward week), so this must be
    // filtered to weekDates before aggregating - same guard GroceryViewModel already
    // applies for the identical reason.
    val dayCalories: StateFlow<Map<LocalDate, Int>> = combine(weekPlan, recipes, templates, weekDates) { plan, recipeList, templateList, dates ->
        plan.filterKeys { it in dates }.mapValues { (_, dayPlan) ->
            listOf("breakfast", "lunch", "dinner", "snack").sumOf { meal ->
                when (val slot = dayPlan[meal]) {
                    is MealPlanSlot.RecipeSlot   -> recipeList.find { it.id == slot.id }?.totalKcal?.toInt() ?: 0
                    is MealPlanSlot.TemplateSlot -> templateList.find { it.id == slot.id }?.totalKcal ?: 0
                    else -> 0
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Sum of planned kcal across all days in the current week — derived from dayCalories, no extra query. */
    val weeklyTotalKcal: StateFlow<Int> = dayCalories.map { it.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // New: for each day that still has at least two empty meal slots, suggest the
    // highest-protein recipe not yet assigned anywhere on that day — so a user
    // building a weekly plan gets a concrete fill recommendation instead of a blank.
    val gapSuggestions: StateFlow<Map<LocalDate, Recipe?>> = combine(weekPlan, recipes) { plan, recipeList ->
        if (recipeList.isEmpty()) return@combine emptyMap()
        plan.mapValues { (_, dayPlan) ->
            val assignedIds = listOf("breakfast", "lunch", "dinner", "snack")
                .mapNotNull { (dayPlan[it] as? MealPlanSlot.RecipeSlot)?.id }.toSet()
            val emptySlots = listOf("breakfast", "lunch", "dinner", "snack").count { dayPlan[it] == null }
            if (emptySlots < 2) null
            else recipeList.filter { it.id !in assignedIds }.maxByOrNull { it.totalProteinG }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Data-consistency safeguard: whenever the live recipe/template lists change
     * (an item is added, edited, or — the case this actually protects against —
     * deleted), drop any planned RecipeSlot/TemplateSlot whose id no longer
     * resolves to a real recipe/template. Without this, deleting a recipe left
     * every day it was ever planned on silently pointing at nothing forever —
     * MealPlanRepository stores a denormalized id+name snapshot with no foreign
     * key, so nothing else ever invalidated it. Runs automatically; exposed as
     * a count so the UI can optionally surface "N stale plan entries removed"
     * instead of the cleanup happening invisibly.
     */
    private val _lastPrunedCount = MutableStateFlow(0)
    val lastPrunedOrphanCount: StateFlow<Int> = _lastPrunedCount.asStateFlow()

    init {
        viewModelScope.launch {
            // Room's Flow queries and emits the real current rows on first collection
            // (unlike a stateIn-wrapped flow, there's no synthetic emptyList() placeholder
            // emitted first), so reacting to every emission - including the first - is
            // safe and is what actually catches entries orphaned before this ViewModel
            // was ever created, not just ones orphaned after.
            combine(
                recipeRepo.observeAll(),
                templateRepo.observeAll(),
            ) { recipes, templates -> recipes.map { it.id }.toSet() to templates.map { it.id }.toSet() }
                .collect { (recipeIds, templateIds) ->
                    _lastPrunedCount.value = repo.pruneOrphanedSlots(recipeIds, templateIds)
                }
        }
    }

    fun setNote(date: LocalDate, meal: String, text: String) {
        // Entries are newline-delimited in storage (see MealPlanRepository.serialize) —
        // strip any embedded newline (e.g. from pasted clipboard text) so a note can't
        // split across "lines" and corrupt the following entry.
        val sanitized = text.replace("\n", " ")
        viewModelScope.launch { repo.setSlot(date, meal, if (sanitized.isBlank()) null else MealPlanSlot.NoteSlot(sanitized)) }
    }

    fun clear(date: LocalDate, meal: String) {
        viewModelScope.launch { repo.setSlot(date, meal, null) }
    }

    /** Clear every slot for a day in one action instead of one tap per meal. */
    fun clearDay(date: LocalDate) {
        viewModelScope.launch { repo.clearDay(date) }
    }

    fun setRecipe(date: LocalDate, meal: String, recipe: Recipe) {
        viewModelScope.launch { repo.setSlot(date, meal, MealPlanSlot.RecipeSlot(recipe.id, recipe.name)) }
    }

    fun setTemplate(date: LocalDate, meal: String, template: MealTemplate) {
        viewModelScope.launch { repo.setSlot(date, meal, MealPlanSlot.TemplateSlot(template.id, template.name)) }
    }

    // A planned RecipeSlot/TemplateSlot only ever persisted the plan itself -
    // nothing connected it to ConsumptionRepository, so the day arrived and the
    // plan stayed purely decorative: it never auto-filled or even offered to
    // log the diary entry a user would expect from having "planned" a meal.
    // Looks the slot's id up against the already-loaded recipes/templates lists
    // rather than re-querying, since both are cheap StateFlows already held here.
    fun logSlot(date: LocalDate, meal: String, slot: MealPlanSlot) {
        val mealSlot = MealSlot.valueOf(meal.uppercase())
        viewModelScope.launch {
            when (slot) {
                is MealPlanSlot.RecipeSlot -> recipes.value.find { it.id == slot.id }?.let {
                    consumptionRepo.log(recipeRepo.collapse(it, date, mealSlot))
                }
                is MealPlanSlot.TemplateSlot -> templates.value.find { it.id == slot.id }?.let {
                    consumptionRepo.logAll(templateRepo.expand(it, date, mealSlot))
                }
                is MealPlanSlot.NoteSlot -> Unit
            }
        }
    }
}
