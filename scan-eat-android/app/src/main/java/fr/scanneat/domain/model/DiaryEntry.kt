package fr.scanneat.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

// ============================================================================
// DIARY / CONSUMPTION — port of public/data/consumption.js
// ============================================================================

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

data class DiaryEntry(
    val id: Long = 0,
    val date: LocalDate,
    val mealSlot: MealSlot,
    val loggedAt: LocalDateTime = LocalDateTime.now(),
    val productName: String,
    val barcode: String? = null,
    val portionG: Double,                // serving size in grams
    val nutrition: NutritionPer100g,     // per-100g values; scale by portionG/100
    val source: ScanSource,
    val profileId: String = "default",
    // Previously dropped at logging time even though the source Product always
    // has them available (ScanResult.product.ingredients / CustomFood) - meant
    // checkUserAllergens()/checkDiet() (already run live against Recipes,
    // Grocery lists and Meal Templates via an identical toCheckProduct()
    // pattern) could never run against a logged Diary entry at all, so a
    // user's declared allergen/diet profile was silently never checked here.
    val ingredients: List<Ingredient> = emptyList(),
) {
    /** Actual consumed macros for this entry. */
    val consumed: ConsumedNutrition get() {
        val factor = portionG / 100.0
        return ConsumedNutrition(
            energyKcal    = nutrition.energyKcal    * factor,
            proteinG      = nutrition.proteinG      * factor,
            carbsG        = nutrition.carbsG        * factor,
            fatG          = nutrition.fatG          * factor,
            fiberG        = nutrition.fiberG        * factor,
            sugarsG       = nutrition.sugarsG       * factor,
            saturatedFatG = nutrition.saturatedFatG * factor,
            saltG         = nutrition.saltG         * factor,
            ironMg        = (nutrition.ironMg       ?: 0.0) * factor,
            calciumMg     = (nutrition.calciumMg    ?: 0.0) * factor,
            vitDUg        = (nutrition.vitDUg       ?: 0.0) * factor,
            b12Ug         = (nutrition.b12Ug        ?: 0.0) * factor,
            // DailyTargets already computes personalized magnesium/potassium/zinc/vitC
            // targets (PersonalScoreEngine.kt), but this daily accumulator never tracked
            // the consumed side of any of them - the targets were calculated every day
            // and never had anything to compare against.
            magnesiumMg   = (nutrition.magnesiumMg  ?: 0.0) * factor,
            potassiumMg   = (nutrition.potassiumMg  ?: 0.0) * factor,
            zincMg        = (nutrition.zincMg       ?: 0.0) * factor,
            vitCMg        = (nutrition.vitCMg       ?: 0.0) * factor,
        )
    }
    /**
     * Synthetic Product so a logged diary entry can be run through the same
     * checkDiet()/checkUserAllergens() the barcode-scan, Recipes, Grocery, and
     * Meal Template flows already use (see Recipe.toCheckProduct() etc.) -
     * previously nothing in the Diary ever ran either check, so a user could
     * log a product containing one of their declared allergens with no
     * in-context reminder once it left the Result screen.
     */
    fun toCheckProduct(): Product = Product(
        name        = productName,
        category    = ProductCategory.OTHER,
        novaClass   = NovaClass.PROCESSED,
        ingredients = ingredients,
        nutrition   = nutrition,
    )
}

data class ConsumedNutrition(
    val energyKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarsG: Double,
    val saturatedFatG: Double,
    val saltG: Double,
    val ironMg: Double,
    val calciumMg: Double,
    val vitDUg: Double,
    val b12Ug: Double,
    val magnesiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val zincMg: Double = 0.0,
    val vitCMg: Double = 0.0,
) {
    operator fun plus(other: ConsumedNutrition) = ConsumedNutrition(
        energyKcal    = energyKcal    + other.energyKcal,
        proteinG      = proteinG      + other.proteinG,
        carbsG        = carbsG        + other.carbsG,
        fatG          = fatG          + other.fatG,
        fiberG        = fiberG        + other.fiberG,
        sugarsG       = sugarsG       + other.sugarsG,
        saturatedFatG = saturatedFatG + other.saturatedFatG,
        saltG         = saltG         + other.saltG,
        ironMg        = ironMg        + other.ironMg,
        calciumMg     = calciumMg     + other.calciumMg,
        vitDUg        = vitDUg        + other.vitDUg,
        b12Ug         = b12Ug         + other.b12Ug,
        magnesiumMg   = magnesiumMg   + other.magnesiumMg,
        potassiumMg   = potassiumMg   + other.potassiumMg,
        zincMg        = zincMg        + other.zincMg,
        vitCMg        = vitCMg        + other.vitCMg,
    )

    companion object {
        val ZERO = ConsumedNutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

data class DailySummary(
    val date: LocalDate,
    val entries: List<DiaryEntry>,
    val totals: ConsumedNutrition,
)
