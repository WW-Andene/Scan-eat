package fr.scanneat.data.repository.planning

import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.recipe.RecipeEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.*
import fr.scanneat.util.ioCatching
import fr.scanneat.util.serverUrlMissingMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
// ============================================================================

// ---- Domain types ----

@JsonClass(generateAdapter = true)
data class RecipeComponent(
    val productName: String,
    val grams: Double,
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val saltG: Double = 0.0,
    val fiberG: Double = 0.0,
)

data class Recipe(
    val id: String,
    val name: String,
    val servings: Int,
    val components: List<RecipeComponent>,
    val createdAt: Long,
    val notes: String = "",
    val favorite: Boolean = false,
) {
    val totalKcal: Double get() = components.sumOf { it.kcal }
    val totalProteinG: Double get() = components.sumOf { it.proteinG }
    val totalCarbsG: Double get() = components.sumOf { it.carbsG }
    val totalFatG: Double get() = components.sumOf { it.fatG }
    val totalGrams: Double get() = components.sumOf { it.grams }

    /**
     * Synthetic Product so a saved recipe can be run through the same
     * checkDiet()/checkUserAllergens() the barcode-scan path already uses -
     * those checks previously only ever saw scanned Products, so a vegan or
     * allergic user could freely save/log a recipe containing an ingredient
     * their own profile forbids, with no warning anywhere in the recipe flow.
     */
    fun toCheckProduct(): Product = Product(
        name        = name,
        category    = ProductCategory.OTHER,
        novaClass   = NovaClass.UNPROCESSED,
        ingredients = components.map { c -> Ingredient(name = c.productName, category = IngredientCategory.FOOD) },
        nutrition   = nutritionPer100g,
    )

    /** Per-100g nutrition (for the scoring engine). */
    val nutritionPer100g: NutritionPer100g get() {
        val basis = if (totalGrams > 0) totalGrams else 100.0
        fun scale(v: Double) = v * 100.0 / basis
        return NutritionPer100g(
            energyKcal    = scale(totalKcal),
            fatG          = scale(totalFatG),
            saturatedFatG = 0.0,
            carbsG        = scale(totalCarbsG),
            sugarsG       = 0.0,
            fiberG        = scale(components.sumOf { it.fiberG }),
            proteinG      = scale(totalProteinG),
            saltG         = scale(components.sumOf { it.saltG }),
        )
    }
}

/**
 * Recomputes every component's grams/kcal/macros for a permanently different
 * batch size - previously `servings` was purely informational for anything
 * except a one-off logged portion (LogRecipeDialog divides by it, but never
 * writes a rescaled value back to the stored recipe), so doubling a recipe
 * for a dinner party meant manually re-entering every ingredient's quantity.
 */
fun Recipe.scaledComponents(newServings: Int): List<RecipeComponent> {
    if (servings <= 0 || newServings <= 0 || newServings == servings) return components
    val ratio = newServings.toDouble() / servings
    return components.map { c ->
        c.copy(
            grams    = c.grams    * ratio,
            kcal     = c.kcal     * ratio,
            proteinG = c.proteinG * ratio,
            carbsG   = c.carbsG   * ratio,
            fatG     = c.fatG     * ratio,
            saltG    = c.saltG    * ratio,
            fiberG   = c.fiberG   * ratio,
        )
    }
}

/**
 * Inverse of MealTemplate.toRecipeComponents() - a Recipe has no meal of its own
 * (it collapses into one diary entry, unlike a template's per-item meal), so the
 * caller supplies which slot the resulting Saved Meal should apply to. satFatG/
 * sugarsG have no equivalent on RecipeComponent, so they default to 0 same as
 * any other newly-created TemplateItem.
 */
fun Recipe.toTemplateItems(meal: MealSlot): List<TemplateItem> = components.map { c ->
    TemplateItem(
        productName = c.productName,
        grams       = c.grams,
        meal        = meal.name.lowercase(),
        kcal        = c.kcal,
        carbsG      = c.carbsG,
        fatG        = c.fatG,
        saltG       = c.saltG,
        proteinG    = c.proteinG,
        fiberG      = c.fiberG,
    )
}

/**
 * Plain-text rendering for the Android share sheet - previously a recipe could
 * only leave the app via the whole-database JSON backup, with no way to send
 * just this one recipe to someone else. Mirrors formatGroceryList()'s role
 * for the grocery list (domain/engine/planning/GroceryList.kt).
 */
fun Recipe.formatShareText(): String = buildString {
    appendLine(name)
    appendLine("${servings} portion${if (servings > 1) "s" else ""} · ${totalKcal.toInt()} kcal")
    appendLine()
    components.forEach { c -> appendLine("- ${c.productName} (${c.grams.toInt()} g)") }
    if (notes.isNotBlank()) {
        appendLine()
        appendLine(notes)
    }
}.trim()

/**
 * Result of a URL/photo recipe import (fetchRecipeFromUrl / identifyRecipeFromPhotos) —
 * a plain preview the caller pre-fills into AddRecipeDialog for the user to review before
 * saving, not something written to the database directly. Ingredients are free-text lines
 * (a schema.org Recipe or a photo of a recipe card describes "2 cups flour", not a gram
 * weight matched against FOOD_DB), so this intentionally does NOT produce RecipeComponents —
 * only [kcal]/[proteinG]/[fatG]/[carbsG] (when the source actually declared them) carry real
 * numbers; the rest is display text for the user to transcribe into tracked ingredients.
 */
data class FetchedRecipeResult(
    val name: String,
    val servings: String?,
    val ingredients: List<String>,
    val steps: List<String>,
    val cookTimeMinutes: Int?,
    val kcal: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val carbsG: Double?,
    val sourceUrl: String,
)

// ---- Repository ----

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao,
    private val moshi: Moshi,
    private val prefs: UserPreferences,
    private val serverApiProvider: ServerScanApiProvider,
) {
    private val componentsType = com.squareup.moshi.Types.newParameterizedType(List::class.java, RecipeComponent::class.java)
    private val componentsAdapter = moshi.adapter<List<RecipeComponent>>(componentsType)

    fun observeAll(profileId: String = "default"): Flow<List<Recipe>> =
        dao.observeAll(profileId).map { it.mapNotNull { e -> e.toDomain() } }

    suspend fun findById(id: String): Recipe? = dao.findById(id)?.toDomain()

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
        dao.upsert(recipe.toEntity(profileId))
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

    // ---- Server-mode import (URL / photo / AI suggestion) ----

    /**
     * FetchRecipeRoute.kt (SSRF-guarded HTML fetch + schema.org Recipe JSON-LD
     * extraction) has existed on the server since it was added, with no Android
     * caller — every recipe had to be typed in by hand even when the user was
     * looking at a recipe blog post right in front of them. Server-mode only:
     * the SSRF-safe scraping this needs has no Direct-mode equivalent (there is
     * no safe way to do it from the client, and Groq/Cerebras have no "fetch
     * this URL" tool). Needs no Groq key (see the route's own doc comment), so
     * unlike identifyRecipeFromPhotos/suggestRecipes this never checks apiKey.
     */
    suspend fun fetchRecipeFromUrl(url: String, lang: String): Result<FetchedRecipeResult> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val resp = serverApiProvider.get(serverUrl).fetchRecipe(url)
        FetchedRecipeResult(
            name            = resp.name,
            servings        = resp.servings,
            ingredients     = resp.ingredients,
            steps           = resp.steps,
            cookTimeMinutes = resp.cookTimeMin,
            kcal            = resp.nutrition?.kcal,
            proteinG        = resp.nutrition?.proteinG,
            fatG            = resp.nutrition?.fatG,
            carbsG          = resp.nutrition?.carbsG,
            sourceUrl       = resp.sourceUrl,
        )
    }

    /**
     * IdentifyRecipeRoute.kt (recipe card / cookbook page photo → structured recipe
     * via Groq vision) has existed on the server since it was added, with no Android
     * caller — a user with a paper recipe or a photographed cookbook page had no way
     * to import it short of retyping everything. Server-mode only, same reasoning as
     * fetchRecipeFromUrl: there's no equivalent Direct-mode prompt/parser for this
     * today. Unlike fetchRecipeFromUrl this does need a Groq key (the route calls
     * Groq's vision API, see requireGroqKey() in IdentifyRecipeRoute.kt).
     *
     * Reuses [FetchedRecipeResult] rather than a separate result type - both are
     * "unverified external content pre-filling AddRecipeDialog for review," and the
     * structured ingredient quantity/unit/name here collapses to the exact same kind
     * of free-text ingredient line a schema.org import produces (see that class's own
     * doc comment on why ingredients are text, not RecipeComponents). No nutrition
     * block exists on this route's response, unlike fetch-recipe's optional one.
     */
    suspend fun identifyRecipeFromPhotos(images: List<ImagePayload>, lang: String): Result<FetchedRecipeResult> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        val resp = serverApiProvider.get(serverUrl).identifyRecipe(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        FetchedRecipeResult(
            name            = resp.name,
            servings        = resp.servings?.toString(),
            ingredients     = resp.ingredients.map { i -> listOfNotNull(i.quantity, i.unit, i.name).joinToString(" ") },
            steps           = resp.steps,
            cookTimeMinutes = resp.cookTimeMin,
            kcal            = null,
            proteinG        = null,
            fatG            = null,
            carbsG          = null,
            sourceUrl       = "",
        )
    }

    /**
     * SuggestRoute.kt (single ingredient -> recipe ideas via Groq) has existed on
     * the server since it was added, with no Android caller - Recipes had no "give
     * me ideas" entry point at all, only manual/imported entry. Server-mode only
     * and needs a Groq key, same reasoning as identifyRecipeFromPhotos. Reuses
     * [FetchedRecipeResult] per idea (description folded into a single-entry
     * [FetchedRecipeResult.steps] line, main_ingredients as the ingredient list) so
     * picking a suggestion feeds the exact same AddRecipeDialog prefill path as a
     * URL/photo import - "unverified external content the user reviews before
     * saving" describes an LLM-generated idea just as well as a scraped page.
     */
    suspend fun suggestRecipes(ingredient: String, lang: String): Result<List<FetchedRecipeResult>> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val resp = serverApiProvider.get(serverUrl).suggestRecipes(
            groqKey = apiKey.takeIf { it.isNotBlank() },
            request = ServerSuggestRecipesRequest(ingredient),
        )
        resp.recipes.map { s ->
            FetchedRecipeResult(
                name            = s.name,
                servings        = null,
                ingredients     = s.mainIngredients,
                steps           = listOfNotNull(s.description.takeIf { it.isNotBlank() }),
                cookTimeMinutes = s.cookTimeMin,
                kcal            = null,
                proteinG        = null,
                fatG            = null,
                carbsG          = null,
                sourceUrl       = "",
            )
        }
    }

    private fun Recipe.toEntity(profileId: String) = RecipeEntity(
        id             = id,
        name           = name,
        servings       = servings,
        componentsJson = componentsAdapter.toJson(components),
        createdAt      = createdAt,
        profileId      = profileId,
        notes          = notes,
        favorite       = favorite,
    )

    private fun RecipeEntity.toDomain(): Recipe? = runCatching {
        Recipe(
            id         = id,
            name       = name,
            servings   = servings,
            components = componentsAdapter.fromJson(componentsJson) ?: emptyList(),
            createdAt  = createdAt,
            notes      = notes,
            favorite   = favorite,
        )
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository.
        android.util.Log.w("RecipeRepository", "Failed to parse recipe id=$id", it)
    }.getOrNull()
}
