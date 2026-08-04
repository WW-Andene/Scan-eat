package fr.scanneat.presentation.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.MealTemplateRepository
import fr.scanneat.data.repository.planning.MenuDish
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.data.repository.planning.RecipeRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.OFFICIAL_RECIPE_DB
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.planning.findPairings
import fr.scanneat.domain.engine.planning.normalizeKey
import fr.scanneat.domain.engine.scoring.checkDiet
import fr.scanneat.domain.engine.scoring.checkUserAllergens
import fr.scanneat.domain.engine.scoring.healthConditionCautions
import fr.scanneat.data.repository.planning.FetchedRecipeResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class RecipesViewModel @Inject constructor(
    // Widened from private to internal so RecipesImportExt.kt's extension
    // functions (the URL/photo import + suggest flow, extracted verbatim into
    // that sibling file) can still reach it - same structural-only split
    // ScanRepository already went through with ScanOffLookup/ScanServerClient.
    internal val repo: RecipeRepository,
    // Widened from private to internal - RecipesOperationsExt.kt's extension
    // functions (the save/delete/scale/log/clone operations, extracted verbatim
    // into that sibling file) still need these, same reasoning as [repo] above.
    internal val templateRepo: MealTemplateRepository,
    internal val consumptionRepo: ConsumptionRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val scanRepository: ScanRepository,
    prefs: UserPreferences,
) : ViewModel() {
    enum class GoalFilter { ALL, HIGH_PROTEIN, LOW_CARB, LOW_FAT }

    // Distinct product names from scan history, for SuggestRecipesDialog's
    // "from history" mode — lets the user build a suggestFromPantry() list by
    // picking things they've actually scanned instead of only ever typing
    // free text (this dialog's own doc comment: "the app has no persisted
    // pantry inventory feature" — scan history doubles as the closest thing
    // to one it already has, at no new storage cost).
    val historyItems: StateFlow<List<String>> = scanRepository.observeHistory(limit = 50)
        .map { list -> list.map { it.product.name }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _goalFilter = MutableStateFlow(GoalFilter.ALL)
    val goalFilter: StateFlow<GoalFilter> = _goalFilter.asStateFlow()

    fun setGoalFilter(f: GoalFilter) { _goalFilter.value = f }

    // Recipes previously had no way to search by name at all - only the macro-based
    // GoalFilter chips - unlike ScanHistory's real debounced text search. The list
    // lives fully in memory already (repo.observeAll()), so a simple in-memory
    // contains() filter is enough; no DAO query needed.
    private val _recipeQuery = MutableStateFlow("")
    val recipeQuery: StateFlow<String> = _recipeQuery.asStateFlow()
    fun setRecipeQuery(q: String) { _recipeQuery.value = q }

    // Widened from private to internal - RecipesOperationsExt.kt's delete()/
    // undoDelete() extension functions need the current snapshot directly.
    internal val _allRecipes: StateFlow<List<Recipe>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipes: StateFlow<List<Recipe>> = combine(_allRecipes, _goalFilter, _recipeQuery.debounce(150)) { list, filter, query ->
        val filtered = when (filter) {
            GoalFilter.ALL         -> list
            GoalFilter.HIGH_PROTEIN -> list.filter { r -> r.totalGrams > 0 && r.totalProteinG / r.totalGrams * 100 >= 15 }
            GoalFilter.LOW_CARB    -> list.filter { r -> r.totalGrams > 0 && r.totalCarbsG / r.totalGrams * 100 <= 20 }
            GoalFilter.LOW_FAT     -> list.filter { r -> r.totalGrams > 0 && r.totalFatG / r.totalGrams * 100 <= 10 }
        }
        val searched = if (query.isBlank()) filtered else {
            val key = normalizeKey(query)
            filtered.filter { normalizeKey(it.name).contains(key) }
        }
        // Favorites first (Recipe had no equivalent to ScanResult's favorite field
        // at all) - sortedByDescending is stable, so createdAt-DESC ordering
        // (already applied by RecipeDao.observeAll) is preserved within each group.
        searched.sortedByDescending { it.favorite }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Every write below previously called repo's/templateRepo's/consumptionRepo's Room
    // writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a
    // write failure here wasn't just silent, it was an uncaught exception that would
    // crash the app.
    // cloneOfficial() only knows a picked FOOD_DB entry's macros via an exact
    // (case-insensitive) name match against OfficialRecipeDb's French labels - an
    // ingredient with no match silently clones in at kcal/protein/carbs/fat/fiber
    // = 0, with nothing telling the user the clone is macro-incomplete. Surfaced
    // as a one-shot count the screen can snackbar, same shape as MealPlanViewModel's
    // lastPrunedOrphanCount for its own silent-cleanup case.
    // Widened from private to internal - RecipesOperationsExt.kt's cloneOfficial()
    // extension function mutates this directly.
    internal val _cloneUnmatchedCount = MutableStateFlow(0)
    val cloneUnmatchedCount: StateFlow<Int> = _cloneUnmatchedCount.asStateFlow()
    fun clearCloneUnmatchedCount() { _cloneUnmatchedCount.value = 0 }

    // Widened from private to internal - RecipesOperationsExt.kt's extension
    // functions mutate this directly, same as every function in this file did
    // before the split.
    internal val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    /** Total custom-recipe count regardless of the active goal filter — used to
     *  show "X/Y résultats" in the filter row when a filter is active. */
    val totalRecipesCount: StateFlow<Int> = _allRecipes.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            val conditionHits = healthConditionCautions(product, profile.healthConditions, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            conditionHits.firstOrNull()?.let { parts += it }
            if (parts.isEmpty()) null else recipe.id to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Same check as [recipeWarnings], for the official/starter recipes shown above the user's own. */
    val officialRecipeWarnings: StateFlow<Map<String, String>> = combine(prefs.profile, language) { profile, lang ->
        OFFICIAL_RECIPE_DB.mapNotNull { recipe ->
            val product = recipe.toCheckProduct()
            val allergenHits = if (profile.allergens.isNotEmpty()) checkUserAllergens(product, profile.allergens, lang) else emptyList()
            val dietResult = checkDiet(product, profile.diet, lang)
            val conditionHits = healthConditionCautions(product, profile.healthConditions, lang)
            val parts = mutableListOf<String>()
            allergenHits.firstOrNull()?.let { parts += if (lang == "en") "Allergen: ${it.labelEn}" else "Allergène : ${it.labelFr}" }
            dietResult.reason?.let { parts += it }
            conditionHits.firstOrNull()?.let { parts += it }
            if (parts.isEmpty()) null else recipe.nameFr to parts.joinToString(" · ")
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * recipe id -> ProductHints, same combine-into-map pattern as [recipeWarnings]/
     * [recipePairings] above - the "💡 Bon à savoir" hint panel (previously only
     * reachable from ResultScreen's scanned-product flow) needs the exact same
     * benefits/risks/facts for a saved recipe, which toCheckProduct() already
     * makes possible.
     */
    val recipeHints: StateFlow<Map<String, ProductHints>> = combine(recipes, prefs.profile, language) { list, profile, lang ->
        list.associate { it.id to generateProductHints(it.toCheckProduct(), profile, lang) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Same as [recipeHints], for the official/starter recipes shown above the user's own. */
    val officialRecipeHints: StateFlow<Map<String, ProductHints>> = combine(prefs.profile, language) { profile, lang ->
        OFFICIAL_RECIPE_DB.associate { recipe -> recipe.nameFr to generateProductHints(recipe.toCheckProduct(), profile, lang) }
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

    // Widened from private to internal - RecipesOperationsExt.kt's delete()/
    // undoDelete() extension functions need this snapshot directly.
    internal var lastDeleted: Recipe? = null

    // Widened from private to internal - RecipesOperationsExt.kt's scale()/
    // undoScale() extension functions need this snapshot directly.
    internal var lastPreScale: Recipe? = null

    // save/toggleFavorite/delete/undoDelete/duplicate/saveAsTemplate/updateRecipe/
    // rename/updateNotes/scale/undoScale/log/logOfficial/cloneOfficial now live in
    // RecipesOperationsExt.kt (see that file) - extracted verbatim as extension
    // functions on RecipesViewModel, same cohesive "recipe CRUD + logging" concern
    // ScanRepository's own split pulled ScanOffLookup/ScanServerClient out for.

    // ── Import from URL — wires up the server's fetch-recipe route (SSRF-guarded
    // HTML fetch + schema.org Recipe JSON-LD extraction), which existed with no
    // Android caller at all: every recipe previously had to be typed in by hand.
    // ────────────────────────────────────────────────────────────────────────
    sealed class ImportUiState {
        data object Idle : ImportUiState()
        data object Loading : ImportUiState()
        data class Success(val result: FetchedRecipeResult) : ImportUiState()
        /** suggestRecipes()/suggestFromPantry() return several ideas to pick from, unlike the single-result URL/photo import. */
        data class SuggestSuccess(val results: List<FetchedRecipeResult>) : ImportUiState()
        /**
         * identifyMenuFromPhotos() result — unlike [Success]/[SuggestSuccess], these dishes
         * are external restaurant items with nothing to save/log, so they get their own
         * variant rather than reusing [Success]'s AddRecipeDialog-prefill handoff.
         */
        data class MenuSuccess(val dishes: List<MenuDish>) : ImportUiState()
        data class Error(val message: String) : ImportUiState()
    }

    // Widened from private to internal - RecipesImportExt.kt's extension functions
    // (moved verbatim into that sibling file) mutate this directly, same as every
    // function in this file did before the split.
    internal val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    // importRecipeFromUrl/importRecipeFromPhotos/identifyMenuFromPhotos/suggestRecipes/
    // suggestFromPantry/pickSuggestion/clearImportState/photoDecodeFailed/importErrorMessage
    // now live in RecipesImportExt.kt (see that file) - extracted verbatim as extension
    // functions on RecipesViewModel, same cohesive "URL/photo import + suggest" concern
    // ScanRepository's own split pulled ScanOffLookup/ScanServerClient out for.
}
