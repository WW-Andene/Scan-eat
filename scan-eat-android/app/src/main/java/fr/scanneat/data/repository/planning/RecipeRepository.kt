package fr.scanneat.data.repository.planning

import fr.scanneat.data.local.db.recipe.RecipeDao
import fr.scanneat.data.local.db.recipe.RecipeEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

// ---- Repository ----

@Singleton
class RecipeRepository @Inject constructor(private val dao: RecipeDao,
    private val moshi: Moshi,
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
