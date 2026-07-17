package fr.scanneat.presentation.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.data.repository.planning.MealTemplateRepository
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.data.repository.planning.TemplateItem
import fr.scanneat.data.repository.planning.toRecipeComponents
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.model.MealSlot
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repo: MealTemplateRepository,
    private val recipeRepo: RecipeRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    private val _allTemplates: StateFlow<List<MealTemplate>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Templates had zero list-level filtering despite MealTemplate.meal being the
    // exact same ready-made filter dimension Recipes already uses for its own
    // GoalFilter chips - null means "all meal slots".
    private val _mealFilter = MutableStateFlow<MealSlot?>(null)
    val mealFilter: StateFlow<MealSlot?> = _mealFilter.asStateFlow()
    fun setMealFilter(m: MealSlot?) { _mealFilter.value = m }

    val templates: StateFlow<List<MealTemplate>> = combine(_allTemplates, _mealFilter) { list, filter ->
        val filtered = if (filter == null) list else list.filter { it.meal == filter }
        // Favorites first - same pattern as RecipesViewModel.recipes; sortedByDescending
        // is stable, so observeAll()'s createdAt-DESC ordering is preserved within each group.
        filtered.sortedByDescending { it.favorite }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleFavorite(template: MealTemplate) = viewModelScope.launch {
        repo.setFavorite(template.id, !template.favorite)
    }

    /** Sum of all items' kcal across every template — shown as a library-level stat, unaffected by the meal-slot filter. */
    val libraryTotalKcal: StateFlow<Int> = _allTemplates.map { list ->
        list.sumOf { it.totalKcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    /**
     * template id -> short warning, same checkDiet()/checkUserAllergens() reused
     * from Recipes'/Grocery's identical fix - a "Saved Meal" template is exactly
     * as likely to contain a forbidden ingredient as a Recipe, but previously ran
     * no check at all, so a vegan or allergic user got no warning for the one
     * meal type they log repeatedly.
     */
    val templateWarnings: StateFlow<Map<String, String>> = combine(templates, prefs.profile, language) { list, profile, lang ->
        list.mapNotNull { template ->
            val product = template.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else template.id to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * template id -> ProductHints, same combine-into-map pattern as
     * [templateWarnings] above - the "💡 Bon à savoir" hint panel was previously
     * reachable only from ResultScreen's scanned-product flow, despite a "Saved
     * Meal" template now carrying real per-100g nutrition via toCheckProduct()
     * (see MealTemplateRepository.nutritionPer100g).
     */
    val templateHints: StateFlow<Map<String, ProductHints>> = combine(templates, prefs.profile, language) { list, profile, lang ->
        list.associate { it.id to generateProductHints(it.toCheckProduct(), profile, lang) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Ingredient search for "manage items" ─────────────────────────────────
    // Same FOOD_DB/custom-food search RecipesViewModel already wires for its own
    // "add ingredient" dialog - EditTemplateItemsDialog previously had no lookup
    // at all, so every item's protein/carbs/fat/fiber defaulted to 0 even when
    // the food was already known, and TemplatesScreen's macro summary chips
    // (added specifically to show a template's P/G/L) were permanently 0g/0g/0g.
    private val _ingredientQuery = MutableStateFlow("")
    val ingredientQuery: StateFlow<String> = _ingredientQuery.asStateFlow()

    val ingredientSearchResults: StateFlow<List<FoodEntry>> =
        combine(_ingredientQuery.debounce(200), customFoodRepo.observeAll()) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 6, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setIngredientQuery(q: String) { _ingredientQuery.value = q }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    /** Templates and Recipes carry near-identical component shapes but had no
     *  way to convert between the two - a "quick weeknight combo" built as a
     *  template couldn't become a proper named Recipe without re-entering
     *  every ingredient by hand, and vice versa. */
    fun saveAsRecipe(template: MealTemplate) = viewModelScope.launch {
        recipeRepo.save(template.name, template.toRecipeComponents(), servings = 1)
    }

    // logTemplate's runCatching below prevented a crash on Room write failure but had
    // no failure path at all - a user tapping a template's log icon got zero visible
    // effect if the write failed, unable to tell whether it had actually logged.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed log, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun create(name: String, meal: MealSlot) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.save(name, meal, items = emptyList<TemplateItem>()) }
    }

    fun rename(template: MealTemplate, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.save(newName, template.meal, template.items, id = template.id) }
    }

    fun logTemplate(template: MealTemplate, date: LocalDate = LocalDate.now(), mealOverride: MealSlot? = null, portionScale: Double = 1.0) {
        viewModelScope.launch {
            val entries = repo.expand(template, date, mealOverride)
            val scaled = if (portionScale == 1.0) entries
            else entries.map { it.copy(portionG = it.portionG * portionScale) }
            // Guarded like DashboardViewModel.logGapSuggestion/ResultViewModel.log -
            // a Room insert failure (disk-full, constraint violation) here previously
            // crashed the app instead of just failing to log this template.
            runCatching { consumptionRepo.logAll(scaled) }.onFailure { _actionFailed.value = true }
        }
    }

    // create() always built a template with items = emptyList() and there was
    // no UI anywhere to add one afterward — TemplateItem(...) was constructed
    // nowhere in the whole presentation layer. Every template a user created
    // stayed a permanent 0-item, 0-kcal template; "Log" just planted nothing.
    // Mirrors Recipes' own add-ingredient pattern (RecipeComponent).
    fun addItem(template: MealTemplate, item: TemplateItem) {
        viewModelScope.launch { repo.save(template.name, template.meal, template.items + item, id = template.id) }
    }

    fun removeItem(template: MealTemplate, index: Int) {
        val items = template.items.toMutableList().also { if (index in it.indices) it.removeAt(index) }
        viewModelScope.launch { repo.save(template.name, template.meal, items, id = template.id) }
    }
}
