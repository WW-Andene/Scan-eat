package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.nutrition.wordBoundaryMatch
import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// COSMETIC/HYGIENE TRANSPARENCY SCORE — user-requested: "score" a scanned
// non-food product (shampoo, gel douche, cosmétique...) the way food gets
// a nutrition-based grade, but WITHOUT inventing a per-ingredient toxicology
// verdict this app has no verified, sourced database for (see
// NonConsumableLookupDb's own header on why fabricating that would be a real
// harm risk - the exact same principle applies here).
//
// This is deliberately a TRANSPARENCY/COMPOSITION signal, not a safety
// verdict - closer in spirit to NOVA's "how processed is this" (a complexity
// signal) than to a toxicity score:
//   1. Formula complexity: how many INCI ingredients the product declares.
//      Fewer ingredients is a legitimate, widely-used "simpler formulation"
//      heuristic (the same reasoning apps like INCI Beauty surface) - not a
//      claim that a longer list is unsafe, just less minimal.
//   2. Declared fragrance allergens: the 26 fragrance substances the EU has
//      required individual labeling for since the 2003/15/EC Cosmetics
//      Directive amendment (in force 2005), carried forward into Regulation
//      (EC) No 1223/2009 Annex III - not a "these are dangerous" claim, just
//      "these specific substances must be named if present, because a
//      meaningful share of the population is sensitized to them". This is
//      the SAME kind of "widely known, EU-mandated disclosure list" as the
//      14 EU food allergens already hardcoded in allergenLabels()
//      (ProfileSelectors.kt) - not a fabricated hazard judgment.
//      NOTE: the EU expanded this list further in Regulation (EU) 2023/1545
//      (in force 2026); that later expansion is NOT included here since it
//      could not be verified against a live/bulk source in this session
//      (ec.europa.eu is unreachable from this environment) - only the
//      original, long-stable 26-substance list is checked, and this
//      limitation is stated explicitly in the UI, not silently implied to
//      be exhaustive.
//
// Étape 2: per-substance Annex II (prohibited)/III (restricted-with-conditions)
// regulatory-status checking now lives in CosingRegulatoryDb.kt - it needed
// CosIng's own official bulk export (Annex II/III text files, provided
// directly by the user 12/08/2026 after ec.europa.eu proved unreachable
// from this environment) rather than anything fabricated here.
// ============================================================================

enum class FormulaComplexity { SIMPLE, MODERATE, COMPLEX, UNKNOWN }

data class CosmeticTransparencyResult(
    val ingredientCount: Int?,
    val complexity: FormulaComplexity,
    val detectedAllergens: List<String>,
)

// Editorial thresholds (not a validated clinical cutoff) - same "design
// choice, not a calibration" honesty BmrCalculations.kt's own header already
// applies to its point magnitudes.
private const val SIMPLE_MAX = 10
private const val MODERATE_MAX = 20

/**
 * The 26 EU-mandated fragrance allergens (Cosmetics Directive 2003/15/EC
 * amendment → Regulation (EC) No 1223/2009 Annex III, entries 67-92) - see
 * this file's own header for what this list is and isn't. INCI names,
 * normalized the same way IngredientMatcher's other dictionaries are.
 */
val EU_FRAGRANCE_ALLERGENS_26: List<String> = listOf(
    "amyl cinnamal", "benzyl alcohol", "cinnamyl alcohol", "citral", "eugenol",
    "hydroxycitronellal", "isoeugenol", "amylcinnamyl alcohol", "benzyl salicylate",
    "cinnamal", "coumarin", "geraniol", "anisyl alcohol", "benzyl cinnamate",
    "farnesol", "butylphenyl methylpropional", "linalool", "benzyl benzoate",
    "citronellol", "hexyl cinnamal", "limonene", "methyl heptin carbonate",
    "alpha-isomethyl ionone", "evernia prunastri extract", "evernia furfuracea extract",
    "methyl 2-octynoate",
).map(::normalizeForMatching)

/**
 * Parses an OPF/OFF-style `ingredients_text` free-text INCI list (comma-
 * separated, sometimes with parenthetical sub-lists) into individual
 * ingredient names. Deliberately simple (split on top-level commas only,
 * dropping empty/parenthetical-only fragments) - a full INCI parser would
 * need to handle nested parentheses correctly, which the sparse OPF data
 * this feeds from doesn't reliably format anyway.
 */
internal fun parseIngredientsText(text: String): List<String> {
    var depth = 0
    val parts = mutableListOf<StringBuilder>(StringBuilder())
    for (c in text) {
        when (c) {
            '(', '[' -> { depth++; parts.last().append(c) }
            ')', ']' -> { depth = (depth - 1).coerceAtLeast(0); parts.last().append(c) }
            ',' -> if (depth == 0) parts.add(StringBuilder()) else parts.last().append(c)
            else -> parts.last().append(c)
        }
    }
    return parts.map { it.toString().trim() }.filter { it.isNotEmpty() }
}

/**
 * Returns null when [ingredientsText] is null/blank - "no ingredient data
 * available" is a real, common state (most OPF non-food entries don't carry
 * ingredients_text) and must be shown as such, not silently scored as if an
 * empty list were a genuinely minimal formulation.
 */
fun computeCosmeticTransparency(ingredientsText: String?): CosmeticTransparencyResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    val count = ingredients.size
    val complexity = when {
        count <= SIMPLE_MAX   -> FormulaComplexity.SIMPLE
        count <= MODERATE_MAX -> FormulaComplexity.MODERATE
        else                  -> FormulaComplexity.COMPLEX
    }
    val normalized = normalizeForMatching(ingredientsText)
    val detected = EU_FRAGRANCE_ALLERGENS_26.filter { allergen -> wordBoundaryMatch(normalized, allergen) }
    return CosmeticTransparencyResult(ingredientCount = count, complexity = complexity, detectedAllergens = detected)
}
