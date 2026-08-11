package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// OFF SPARSITY CHECK — split out of OffMapper.kt.
// ============================================================================

/**
 * True when an OFF-sourced product is missing enough data that LLM
 * augmentation is worth attempting.
 */
fun isOffSparse(p: Product): Boolean {
    val n = p.nutrition
    // A genuinely single/dual-ingredient product (water, salt, single-origin oil)
    // isn't sparse data — only a fully empty ingredients list is a real gap.
    val hasIngredients = p.ingredients.isNotEmpty()
    val hasCategory    = p.category != ProductCategory.OTHER
    // A genuinely calorie-free product (water, black coffee/tea, diet soda)
    // legitimately reports energyKcal/proteinG/carbsG/fatG/sugarsG/saltG as
    // exactly 0 - that's real, complete nutrition data, not a missing-data
    // gap. The plain `> 0` check below treated "every macro is 0" identically
    // to "OFF has nothing at all for this product", forcing every water/zero-
    // calorie-beverage scan into the sparse/needs-photo LLM-augment branch
    // even when the OFF record's ingredients/category/NOVA/nutrition-grade
    // were fully populated - reported case: Cristaline sparkling water
    // (barcode 3254380008430), whose real OFF record is complete and just
    // genuinely all-zero on macros. Only counts as real (non-sparse) data
    // when ingredients/category are ALSO present, matching the existing
    // "ingredients present = not sparse" reasoning above - a record with no
    // ingredients or no category is still flagged sparse regardless of what
    // its zero macros say.
    val allMacrosDeclaredZero = n.energyKcal == 0.0 && n.proteinG == 0.0 && n.carbsG == 0.0 &&
        n.fatG == 0.0 && n.sugarsG == 0.0 && n.saltG == 0.0
    val hasNutrition = n.energyKcal > 0 || n.proteinG > 0 || n.carbsG > 0 ||
        (allMacrosDeclaredZero && hasIngredients && hasCategory)
    // Micronutrients are legitimately absent from most nutrition-facts panels
    // (a can of soda reporting zero vitamins isn't "sparse data", it's correct)
    // so their absence no longer counts against a product — this was flagging
    // almost every packaged product as sparse and forcing needless LLM merges.
    return !hasNutrition || !hasIngredients || !hasCategory
}
