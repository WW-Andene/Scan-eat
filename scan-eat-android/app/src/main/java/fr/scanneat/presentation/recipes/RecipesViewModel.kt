package fr.scanneat.presentation.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.OFFICIAL_RECIPE_DB
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.planning.findPairings
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.ScanSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val repo: RecipeRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val customFoodRepo: CustomFoodRepository,
    prefs: UserPreferences,
) : ViewModel() {
    val recipes: StateFlow<List<Recipe>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    /**
     * recipe id -> short warning, e.g. "Contains gluten (your allergen)" or
     * "Not vegan: chicken". checkDiet()/checkUserAllergens() previously only
     * ever ran against scanned Products - a saved recipe (this screen) or a
     * cloned official recipe could silently contain an ingredient the user's
     * own diet/allergen profile forbids, with no warning anywhere in this flow.
     */
    val recipeWarnings: StateFlow<Map<String, String>> = combine(recipes, prefs.profile, language) { list, profile, lang ->
        list.mapNotNull { recipe ->
            val product = recipe.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else recipe.id to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Same check as [recipeWarnings], for the official/starter recipes shown above the user's own. */
    val officialRecipeWarnings: StateFlow<Map<String, String>> = combine(prefs.profile, language) { profile, lang ->
        OFFICIAL_RECIPE_DB.mapNotNull { recipe ->
            val product = recipe.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            if (parts.isEmpty()) null else recipe.nameFr to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Officially-sourced starter recipes (see OfficialRecipeDb.kt) — read-only, browsable alongside the user's own. */
    val officialRecipes: List<OfficialRecipe> = OFFICIAL_RECIPE_DB

    /**
     * recipe id -> ingredients (French names) that pair well with the recipe's
     * main (largest by grams) ingredient. findPairings()/PairingsDb.kt was
     * already used for scanned products (ResultViewModel) but never reached
     * Recipes at all - a natural place to also deepen recipe discovery, since
     * it's the exact same "what goes well with this" question.
     */
    val recipePairings: StateFlow<Map<String, List<String>>> = recipes.map { list ->
        list.mapNotNull { recipe ->
            val main = recipe.components.maxByOrNull { it.grams } ?: return@mapNotNull null
            val pairs = findPairings(main.productName, limit = 4)
            if (pairs.isEmpty()) null else recipe.id to pairs
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Same as [recipePairings], for the official/starter recipes. */
    val officialRecipePairings: Map<String, List<String>> = OFFICIAL_RECIPE_DB.mapNotNull { recipe ->
        val main = recipe.ingredients.maxByOrNull { it.grams } ?: return@mapNotNull null
        val pairs = findPairings(main.foodName, limit = 4)
        if (pairs.isEmpty()) null else recipe.nameFr to pairs
    }.toMap()

    // ── Ingredient search for the "add ingredient" dialog ────────────────────
    // Previously pure manual entry: name/grams/kcal typed by hand, with no
    // lookup at all - every recipe ingredient's protein/carbs/fat/fiber
    // defaulted to 0 even when the food was already in FOOD_DB or the user's
    // own custom foods (searchFoodDB already merges both, same as Diary's
    // manual-add search), and a user's own custom food could never be reused
    // as a recipe ingredient with correct macros.
    private val _ingredientQuery = MutableStateFlow("")
    val ingredientQuery: StateFlow<String> = _ingredientQuery.asStateFlow()

    val ingredientSearchResults: StateFlow<List<FoodEntry>> =
        combine(_ingredientQuery.debounce(200), customFoodRepo.observeAll()) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 6, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setIngredientQuery(q: String) { _ingredientQuery.value = q }

    fun save(name: String, components: List<RecipeComponent>, servings: Int = 1) {
        viewModelScope.launch { repo.save(name, components, servings) }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    fun rename(recipe: Recipe, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { repo.save(newName, recipe.components, recipe.servings, id = recipe.id) }
    }

    fun log(recipe: Recipe, mealSlot: MealSlot, portionFraction: Double = 1.0) {
        viewModelScope.launch {
            consumptionRepo.log(repo.collapse(recipe, LocalDate.now(), mealSlot, portionFraction))
        }
    }

    /** Logs an official recipe straight to the diary, no cloning required first. */
    fun logOfficial(recipe: OfficialRecipe, mealSlot: MealSlot) {
        viewModelScope.launch {
            val basis = recipe.totalGrams.takeIf { it > 0 } ?: 100.0
            fun per100(v: Double) = v * 100.0 / basis
            consumptionRepo.log(
                DiaryEntry(
                    date        = LocalDate.now(),
                    mealSlot    = mealSlot,
                    productName = recipe.nameFr,
                    barcode     = null,
                    portionG    = basis,
                    nutrition   = NutritionPer100g(
                        energyKcal    = per100(recipe.totalKcal),
                        fatG          = per100(recipe.totalFatG),
                        saturatedFatG = 0.0,
                        carbsG        = per100(recipe.totalCarbsG),
                        sugarsG       = 0.0,
                        fiberG        = per100(recipe.totalFiberG),
                        proteinG      = per100(recipe.totalProteinG),
                        saltG         = 0.0,
                    ),
                    source = ScanSource.MANUAL,
                )
            )
        }
    }

    /** Clones an official recipe into the user's own editable recipe list. */
    fun cloneOfficial(recipe: OfficialRecipe) {
        val components = recipe.ingredients.map { ing ->
            val food = FOOD_DB.firstOrNull { it.name.equals(ing.foodName, ignoreCase = true) }
            RecipeComponent(
                productName = ing.foodName,
                grams       = ing.grams,
                kcal        = (food?.kcal ?: 0.0) * ing.grams / 100.0,
                proteinG    = (food?.proteinG ?: 0.0) * ing.grams / 100.0,
                carbsG      = (food?.carbsG ?: 0.0) * ing.grams / 100.0,
                fatG        = (food?.fatG ?: 0.0) * ing.grams / 100.0,
                fiberG      = (food?.fiberG ?: 0.0) * ing.grams / 100.0,
            )
        }
        save(recipe.nameFr, components)
    }
}
