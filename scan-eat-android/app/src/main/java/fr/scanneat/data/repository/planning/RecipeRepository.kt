package fr.scanneat.data.repository.planning

import fr.scanneat.data.local.db.recipe.RecipeDao
import com.squareup.moshi.Moshi
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// RECIPE REPOSITORY — port of public/data/recipes.js
//
// Recipes differ from meal templates:
//   templates → each component becomes a separate diary entry
//   recipes   → all components collapse into ONE diary entry (the dish)
//
// Room entity + DAO defined inline (small schema, no shared DAO file impact).
//
// Domain types (Recipe/RecipeComponent/FetchedRecipeResult/MenuDish and their
// extension functions) live in RecipeModels.kt; Recipe<->RecipeEntity mapping
// lives in RecipeEntityMapping.kt; the server-mode import calls (URL/photo/AI)
// are grouped in the RecipeServerImportClient delegate below - same
// purely-structural split ScanRepository/HealthConnectRepository already went
// through. Nothing about this class's public API changes.
// ============================================================================

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao,
    moshi: Moshi,
    prefs: UserPreferences,
    serverApiProvider: ServerScanApiProvider,
) {
    // Internal (not private) so RecipeEntityMapping.kt's toEntity()/toDomain()
    // extension functions can reach it - same reasoning as HealthConnectRepository's
    // widened `context`.
    internal val componentsAdapter = moshi.adapter<List<RecipeComponent>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, RecipeComponent::class.java)
    )

    private val serverImport = RecipeServerImportClient(prefs, serverApiProvider)

    fun observeAll(profileId: String = "default"): Flow<List<Recipe>> =
        dao.observeAll(profileId).map { it.mapNotNull { e -> e.toDomain(componentsAdapter) } }

    suspend fun findById(id: String): Recipe? = dao.findById(id)?.toDomain(componentsAdapter)

    /** Case-insensitive name lookup - see RecipeDao.findByName's own doc comment. */
    suspend fun findByName(name: String, profileId: String = "default"): Recipe? =
        dao.findByName(name, profileId)?.toDomain(componentsAdapter)

    suspend fun save(
        name: String,
        components: List<RecipeComponent>,
        servings: Int = 1,
        id: String? = null,
        profileId: String = "default",
        notes: String = "",
    ): Recipe {
        // Editing an existing recipe (rename/re-portion, called with its own id)
        // previously re-stamped createdAt to now on every save - same bug already
        // fixed in MedicationRepository/CustomFoodRepository - preserve the
        // original row's createdAt when one exists.
        val existing = id?.let { dao.findById(it) }
        val createdAt = existing?.createdAt ?: System.currentTimeMillis()
        val recipe = Recipe(
            id         = id ?: UUID.randomUUID().toString(),
            name       = name.trim(),
            servings   = servings.coerceAtLeast(1),
            components = components,
            createdAt  = createdAt,
            notes      = notes,
            // Same reconstruct-from-scratch shape as createdAt above - without
            // this, editing/renaming a favorited recipe would silently drop
            // favorite back to its false default on every save.
            favorite   = existing?.favorite ?: false,
        )
        dao.upsert(recipe.toEntity(profileId, componentsAdapter))
        return recipe
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    /**
     * Collapse a recipe into a single DiaryEntry for a given date + portion.
     * portionFraction = 1.0 means one full serving; 0.5 = half.
     */
    fun collapse(recipe: Recipe, date: LocalDate, mealSlot: MealSlot, portionFraction: Double = 1.0): DiaryEntry {
        val grams = recipe.totalGrams * portionFraction
        return DiaryEntry(
            date        = date,
            mealSlot    = mealSlot,
            productName = recipe.name,
            portionG    = grams.coerceAtLeast(1.0),
            nutrition   = recipe.nutritionPer100g,
            source      = ScanSource.LLM,
            // Previously omitted, defaulting to emptyList() - DiaryViewModel.diaryWarnings
            // runs entry.toCheckProduct() against this list to surface allergen/diet
            // warnings on logged entries, so a recipe logged straight from Recipes never
            // got a warning even when the same recipe's own recipeWarnings flagged it.
            ingredients = recipe.components.map { c -> Ingredient(name = c.productName, category = IngredientCategory.FOOD) },
        )
    }

    // ---- Server-mode import (URL / photo / AI suggestion) — delegated to RecipeServerImportClient ----

    suspend fun fetchRecipeFromUrl(url: String, lang: String): Result<FetchedRecipeResult> =
        serverImport.fetchRecipeFromUrl(url, lang)

    suspend fun identifyRecipeFromPhotos(images: List<ImagePayload>, lang: String): Result<FetchedRecipeResult> =
        serverImport.identifyRecipeFromPhotos(images, lang)

    suspend fun identifyMenuFromPhotos(images: List<ImagePayload>, lang: String): Result<List<MenuDish>> =
        serverImport.identifyMenuFromPhotos(images, lang)

    suspend fun suggestRecipes(ingredient: String, lang: String): Result<List<FetchedRecipeResult>> =
        serverImport.suggestRecipes(ingredient, lang)

    suspend fun suggestFromPantry(items: List<String>, lang: String): Result<List<FetchedRecipeResult>> =
        serverImport.suggestFromPantry(items, lang)
}
