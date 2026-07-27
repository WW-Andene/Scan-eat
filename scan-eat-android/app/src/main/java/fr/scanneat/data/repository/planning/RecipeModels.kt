package fr.scanneat.data.repository.planning

import com.squareup.moshi.JsonClass
import fr.scanneat.domain.model.*

// ============================================================================
// RECIPE DOMAIN MODELS — extracted verbatim out of RecipeRepository.kt, the
// cohesive "what a Recipe is / how it's rendered or transformed" concern,
// independent of the repository's persistence/import logic. Same package, so
// every existing caller of these types is unaffected. Pure structural move,
// no behavior change.
// ============================================================================

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

/**
 * One dish from a restaurant menu photo (IdentifyMenuRoute.kt via [identifyMenuFromPhotos]) —
 * an estimate only, not a scored/trackable Product: there's no barcode, no ingredient list,
 * nothing to save as a recipe or log to the diary, just enough to help the user pick a dish
 * before ordering. estimatedKcal/proteinG are nullable because the LLM may not always be able
 * to estimate them for a given menu line.
 */
data class MenuDish(
    val name: String,
    val description: String?,
    val estimatedKcal: Int?,
    val proteinG: Double?,
)
