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
        )
    }
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
