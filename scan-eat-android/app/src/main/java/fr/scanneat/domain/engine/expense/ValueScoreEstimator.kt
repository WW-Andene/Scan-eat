package fr.scanneat.domain.engine.expense

import fr.scanneat.domain.model.ProductCategory

/** How a logged purchase's price/kg compares to what this category typically costs. */
enum class ValueScore { GREAT, GOOD, AVERAGE, POOR }

/**
 * Rough EU-retail price/kg reference per category, for a purchase that has no
 * same-category history yet in this user's own price_log to compare against
 * instead (PriceRepository prefers the user's own median once ≥ 3 prior
 * entries exist - these are only the cold-start fallback, same "category-
 * typical, honestly approximate" philosophy as MicronutrientEstimator's
 * CATEGORY_DEFAULTS). Figures are deliberately coarse - a real per-country,
 * per-brand price index is out of scope for a client-side estimate.
 */
private val CATEGORY_REF_PRICE_PER_KG: Map<ProductCategory, Double> = mapOf(
    ProductCategory.SANDWICH         to 12.0,
    ProductCategory.READY_MEAL       to 9.0,
    ProductCategory.SOUP             to 4.0,
    ProductCategory.BREAD            to 4.5,
    ProductCategory.BREAKFAST_CEREAL to 6.5,
    ProductCategory.YOGURT           to 3.5,
    ProductCategory.CHEESE           to 14.0,
    ProductCategory.PROCESSED_MEAT   to 11.0,
    ProductCategory.FRESH_MEAT       to 13.0,
    ProductCategory.FISH             to 16.0,
    ProductCategory.SNACK_SWEET      to 9.0,
    ProductCategory.SNACK_SALTY      to 8.0,
    ProductCategory.BEVERAGE_SOFT    to 2.5,
    ProductCategory.BEVERAGE_JUICE   to 3.0,
    ProductCategory.BEVERAGE_WATER   to 0.6,
    ProductCategory.CONDIMENT        to 7.0,
    ProductCategory.OIL_FAT          to 6.0,
    ProductCategory.OTHER            to 7.0,
)

fun referencePricePerKg(category: ProductCategory): Double =
    CATEGORY_REF_PRICE_PER_KG[category] ?: 7.0

/**
 * Compares [pricePerKg] against [referencePerKg] (either the category default
 * above or the user's own same-category median, whichever PriceRepository
 * chose to pass in). Thresholds are ratios, not absolutes, so they apply
 * consistently across cheap (water) and expensive (fish) categories alike.
 */
fun valueScoreFor(pricePerKg: Double, referencePerKg: Double): ValueScore {
    if (referencePerKg <= 0.0) return ValueScore.AVERAGE
    val ratio = pricePerKg / referencePerKg
    return when {
        ratio <= 0.75 -> ValueScore.GREAT
        ratio <= 0.95 -> ValueScore.GOOD
        ratio <= 1.25 -> ValueScore.AVERAGE
        else          -> ValueScore.POOR
    }
}
