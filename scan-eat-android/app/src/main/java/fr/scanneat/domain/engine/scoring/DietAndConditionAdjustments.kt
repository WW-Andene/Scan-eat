package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// Personal score sub-computations — one rule category per function, each a
// direct extraction of one `===== SECTION =====` block from the original
// monolithic computePersonalScore(). Behavior is unchanged; only the grouping
// into named functions - and, since then, files (IngredientMatchPatterns.kt,
// DietCompliance.kt, HealthConditionAdjustments.kt) - is new.
// ============================================================================

// ============================================================================
// PUBLIC SURFACE — reusable "why this isn't ideal for you" badge for anywhere
// outside PersonalScoreEngine's own pipeline (Diary/Recipes/Grocery/Templates
// entry cards, the scan-result "better alternative" filter). Wraps
// checkHealthConditions() with the same category-relative thresholds and
// sugar-sweetened-beverage definition the real score uses, so a Diary entry's
// "not recommended" reason is never a second, drifted copy of the scoring
// logic - just the negative-points subset of the exact same computation,
// surfaced as short reasons instead of numeric adjustments.
// ============================================================================

/** Same sugar-sweetened-beverage definition checkVeto/checkHealthConditions use internally. */
private fun isSugarSweetenedBeverage(product: Product): Boolean =
    product.category == ProductCategory.BEVERAGE_SOFT &&
        product.nutrition.sugarsG > 5.0 && product.nutrition.proteinG < 1.0 && product.nutrition.fiberG < 1.0

/**
 * Short caution reasons from the user's declared health conditions for
 * [product] - empty if the user has none selected or none apply. Diet-key
 * compliance and allergens are intentionally NOT included here (Diary/Recipes/
 * Grocery/Templates already surface those separately via checkDiet()/
 * checkUserAllergens() - see e.g. DiaryViewModel.diaryWarnings) - this is
 * purely the healthConditions-driven half.
 */
fun healthConditionCautions(product: Product, healthConditions: Set<String>, lang: String = "fr"): List<String> {
    if (healthConditions.isEmpty()) return emptyList()
    val result = checkHealthConditions(
        product           = product,
        profile           = Profile(healthConditions = healthConditions),
        lang              = lang,
        catThresholds     = getThresholds(product.category),
        isSugarSweetenedBeverage = isSugarSweetenedBeverage(product),
    )
    return result.adjustments.filter { it.points < 0.0 }.map { it.reason }
}
