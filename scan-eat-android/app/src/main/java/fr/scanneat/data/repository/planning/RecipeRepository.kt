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
        val createdAt = id?.let { dao.findById(it)?.createdAt } ?: System.currentTimeMillis()
        val recipe = Recipe(
            id         = id ?: UUID.randomUUID().toString(),
            name       = name.trim(),
            servings   = servings.coerceAtLeast(1),
            components = components,
            createdAt  = createdAt,
            notes      = notes,
        )
        dao.upsert(recipe.toEntity(profileId))
        return recipe
    }

    suspend fun delete(id: String) = dao.delete(id)

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
    )

    private fun RecipeEntity.toDomain(): Recipe? = runCatching {
        Recipe(
            id         = id,
            name       = name,
            servings   = servings,
            components = componentsAdapter.fromJson(componentsJson) ?: emptyList(),
            createdAt  = createdAt,
            notes      = notes,
        )
    }.onFailure {
        // §XI: same silent-drop gap app-audit §B1/L4 fixed in ConsumptionRepository.
        android.util.Log.w("RecipeRepository", "Failed to parse recipe id=$id", it)
    }.getOrNull()
}
