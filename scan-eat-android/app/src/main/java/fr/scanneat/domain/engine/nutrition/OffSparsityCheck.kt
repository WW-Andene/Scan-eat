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
    val hasNutrition   = n.energyKcal > 0 || n.proteinG > 0 || n.carbsG > 0
    // A genuinely single/dual-ingredient product (water, salt, single-origin oil)
    // isn't sparse data — only a fully empty ingredients list is a real gap.
    val hasIngredients = p.ingredients.isNotEmpty()
    val hasCategory    = p.category != ProductCategory.OTHER
    // Micronutrients are legitimately absent from most nutrition-facts panels
    // (a can of soda reporting zero vitamins isn't "sparse data", it's correct)
    // so their absence no longer counts against a product — this was flagging
    // almost every packaged product as sparse and forcing needless LLM merges.
    return !hasNutrition || !hasIngredients || !hasCategory
}
