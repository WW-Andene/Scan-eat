package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.normalizeForMatching
import fr.scanneat.domain.model.Ingredient

// ============================================================================
// INGREDIENT MATCHER — the one substring-matching algorithm shared by every
// "ingredient dictionary" in this package (IngredientFactsDb,
// NamedSubstanceDb, and any future one). Before this file existed, each
// dictionary hand-rolled the same loop: for each ingredient, for each
// not-yet-seen table entry, check whether a synonym is a substring of the
// (accent/case-normalized) ingredient name, and stop at one match per
// entry. Three copies of that loop meant three places to fix if the
// matching rule ever needed to change (e.g. adding fuzzy matching, or
// word-boundary checks instead of raw substring) — this is the single
// place that happens now.
//
// AdditivesDb keeps its own matching logic rather than moving onto this:
// it also does direct E-number lookup and a context-aware natural-colorant
// keyword map that don't fit this shape, so unifying it here would add
// parameters most callers don't need rather than simplifying anything.
// ============================================================================

/**
 * Returns every entry (at most [limit]) whose synonym list ([namesOf]) has a
 * substring match against some ingredient in [ingredients], each entry
 * appearing at most once even if several ingredients would trigger it.
 * Preserves ingredient-list order, so the first-mentioned ingredient's
 * match comes first.
 */
fun <T> matchIngredientDictionary(
    ingredients: List<Ingredient>,
    entries: List<T>,
    namesOf: (T) -> List<String>,
    limit: Int = Int.MAX_VALUE,
): List<T> {
    val seen = mutableSetOf<T>()
    val result = mutableListOf<T>()
    for (ingredient in ingredients) {
        if (result.size >= limit) break
        val normName = normalizeForMatching(ingredient.name)
        for (entry in entries) {
            if (entry in seen) continue
            if (namesOf(entry).any { normName.contains(normalizeForMatching(it)) }) {
                seen += entry
                result += entry
                break
            }
        }
    }
    return result
}
