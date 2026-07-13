package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.normalizeForMatching
import fr.scanneat.domain.model.Ingredient

// ============================================================================
// INGREDIENT MATCHER — the one substring-matching algorithm shared by every
// "name dictionary" in this package (IngredientFactsDb, NamedSubstanceDb,
// MedicationSubstanceDb, and any future one). Before this file existed,
// each dictionary hand-rolled the same loop: for each query name, for each
// not-yet-seen table entry, check whether a synonym is a substring of the
// (accent/case-normalized) query name, and stop at one match per entry.
// Several copies of that loop meant several places to fix if the matching
// rule ever needed to change (e.g. adding fuzzy matching, or word-boundary
// checks instead of raw substring) — this is the single place that
// happens now.
//
// AdditivesDb keeps its own matching logic rather than moving onto this:
// it also does direct E-number lookup and a context-aware natural-colorant
// keyword map that don't fit this shape, so unifying it here would add
// parameters most callers don't need rather than simplifying anything.
// ============================================================================

/**
 * Returns every entry (at most [limit]) whose synonym list ([namesOf]) has a
 * substring match against some name in [queryNames], each entry appearing
 * at most once even if several query names would trigger it. Preserves
 * query-list order, so the first-mentioned name's match comes first.
 */
fun <T> matchNameDictionary(
    queryNames: List<String>,
    entries: List<T>,
    namesOf: (T) -> List<String>,
    limit: Int = Int.MAX_VALUE,
): List<T> {
    val seen = mutableSetOf<T>()
    val result = mutableListOf<T>()
    for (queryName in queryNames) {
        if (result.size >= limit) break
        val normName = normalizeForMatching(queryName)
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

/** Convenience overload for the common case of matching against a product's ingredient list. */
fun <T> matchIngredientDictionary(
    ingredients: List<Ingredient>,
    entries: List<T>,
    namesOf: (T) -> List<String>,
    limit: Int = Int.MAX_VALUE,
): List<T> = matchNameDictionary(ingredients.map { it.name }, entries, namesOf, limit)
