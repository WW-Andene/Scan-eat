package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Product

// ============================================================================
// SEASONAL PRODUCE CROSS-REFERENCE — R&D audit finding: SeasonalProduceDb's
// isInSeasonNow/matchSeasonalProduce were already used by Grocery
// (GroceryItemRow's in-season badge) but nowhere else - a scanned or logged
// product never got the same seasonal check Grocery already gives it. Same
// itemName-substring-match approach Grocery already uses (see
// matchSeasonalProduce's own doc comment), so a fresh-produce name match
// carries the identical precision/recall tradeoff already accepted there -
// not a new, separately-invented heuristic.
// ============================================================================

internal fun appendSeasonalFact(product: Product, lang: String, facts: MutableList<String>) {
    val match = matchSeasonalProduce(product.name) ?: return
    if (isInSeasonNow(product.name)) return
    val en = lang == "en"
    facts += if (en) "Out of season in France right now (${match.nameEn})"
             else "Hors saison en France en ce moment (${match.nameFr})"
}
