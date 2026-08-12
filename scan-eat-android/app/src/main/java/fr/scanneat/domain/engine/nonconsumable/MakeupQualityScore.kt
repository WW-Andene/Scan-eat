package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// MAKEUP QUALITY PROFILE — last category in the per-category functional-score
// series (shampoo → gel douche → dentifrice → cosmétique → hygiène intime →
// maquillage). Covers foundation, mascara, lipstick, eyeshadow. Same
// "curated, user-reviewed ingredient list, descriptive not a verdict"
// approach as the earlier scores, split the same two-mechanism way as
// IntimateHygieneScore: an ingredient-based profile (computeMakeupProfile)
// plus product-name-gated educational facts (generateMakeupEducationalFacts)
// for things no ingredient list can carry (trace-contaminant regulatory
// status, applicator-hygiene guidance).
//
// Sourced from (13/08/2026 web search, independently verified same day):
//   - Comedogenicity: JAAD Reviews 2025, "Comedogenicity in cosmeceuticals:
//     clinical relevance, regulatory gaps, and future directions" -
//     confirms the classic 0-5 rabbit-ear-assay comedogenicity scale
//     (AAD-panel-endorsed 1989) is now widely regarded by dermatologists as
//     an unreliable predictor of human comedogenicity - CONTESTED, not a
//     settled hazard classification. Isopropyl myristate specifically
//     scores high (4-5) on the classic scale but newer human-skin work
//     questions this - presented here as "historically flagged, contested
//     by newer evidence", not a pore-clogging verdict.
//   - Lead in lipstick: FDA, "Limiting Lead in Lipstick and Other
//     Cosmetics" / "Lead in Cosmetics" - FDA's own survey found an average
//     of 1.11 ppm lead across ~400 lipsticks tested, with guidance capping
//     lead as an unavoidable trace impurity at 10 ppm max - FDA states
//     current levels are NOT a safety concern. This is a LARGELY RESOLVED
//     historical concern, not an active hazard - presented as a factual,
//     reassuring note, not a caution, since ingredient lists don't carry
//     ppm data anyway (same limitation as ToothpasteQualityScore's fluoride
//     ppm caveat).
//   - Talc/asbestos: FDA Federal Register rulemaking on standardized
//     asbestos-testing methods for talc-containing cosmetics (mandated by
//     MoCRA) was proposed Dec 2024 and WITHDRAWN Nov 2025 pending a
//     replacement rule with no announced timeline - this is a genuinely
//     OPEN regulatory gap, not a resolved safety question either way,
//     presented as such rather than as either a hazard or an all-clear.
//   - Preservatives: EU SCCS opinion on phenoxyethanol (PubMed 27825833) -
//     concludes safe up to 1.0% with a margin of safety of ~50; French
//     ANSM recommended a stricter 0.4% limit specifically for nappy-area
//     products used on children under 3 (not directly applicable to
//     makeup, noted only for completeness) - overall a well-established,
//     regulator-reviewed safety consensus at cosmetic-use concentrations.
// ============================================================================

enum class MakeupIngredientRole { COMEDOGENIC_CONTESTED, REGULATED_PRESERVATIVE, TALC }

private val MAKEUP_INGREDIENT_ROLES: Map<String, MakeupIngredientRole> = mapOf(
    // COMEDOGENICITY - historically flagged, contested by newer evidence (see header)
    "isopropyl myristate" to MakeupIngredientRole.COMEDOGENIC_CONTESTED,
    // PRESERVATIVE - EU SCCS-reviewed, safe at cosmetic-use concentrations (see header)
    "phenoxyethanol" to MakeupIngredientRole.REGULATED_PRESERVATIVE,
    // TALC - open regulatory testing gap on asbestos contamination, not a hazard verdict (see header)
    "talc" to MakeupIngredientRole.TALC,
).mapKeys { (name, _) -> normalizeForMatching(name) }

data class MakeupQualityResult(
    val hasComedogenicContested: Boolean,
    val hasRegulatedPreservative: Boolean,
    val hasTalc: Boolean,
)

/** Product-name keyword gate for "is this specifically makeup". */
fun isLikelyMakeup(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return listOf("fond de teint", "mascara", "rouge a levres", "lipstick", "eyeliner", "fard a paupieres", "eyeshadow", "foundation", "concealer", "anti-cernes").any { it in n }
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see ShampooQualityScore.computeShampooQuality's own doc
 * comment on why this must be shown honestly, not silently scored).
 */
fun computeMakeupQuality(ingredientsText: String?): MakeupQualityResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var comedogenicContested = false; var regulatedPreservative = false; var talc = false
    for (ingredient in ingredients) {
        when (MAKEUP_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            MakeupIngredientRole.COMEDOGENIC_CONTESTED  -> comedogenicContested = true
            MakeupIngredientRole.REGULATED_PRESERVATIVE -> regulatedPreservative = true
            MakeupIngredientRole.TALC                   -> talc = true
            null -> {}
        }
    }
    if (!comedogenicContested && !regulatedPreservative && !talc) return null
    return MakeupQualityResult(comedogenicContested, regulatedPreservative, talc)
}

data class MakeupEducationalFacts(val facts: List<String>)

/**
 * NOT a per-ingredient score - trace-contaminant regulatory status (lead)
 * and applicator-hygiene guidance (eye cosmetics) can't be read from an
 * ingredient list, so these are sourced educational facts, same
 * NonConsumableHints-style "category fact, not a per-product verdict"
 * approach as AbsorbentHygieneFacts in IntimateHygieneScore.kt.
 */
fun generateMakeupEducationalFacts(productName: String, lang: String): MakeupEducationalFacts {
    val en = lang == "en"
    val n = normalizeForMatching(productName)
    val facts = mutableListOf<String>()

    if ("rouge a levres" in n || "lipstick" in n) {
        facts += if (en) "FDA's own survey of ~400 lipsticks found an average lead level of 1.11 ppm, well within its 10 ppm guidance cap for this unavoidable trace impurity - FDA states current lipstick lead levels are not a safety concern (a largely resolved historical topic, not an active one)."
                 else "L'enquête de la FDA sur environ 400 rouges à lèvres a mesuré une teneur moyenne en plomb de 1,11 ppm, bien en dessous de son seuil de tolérance de 10 ppm pour cette impureté à l'état de trace inévitable — la FDA indique que les niveaux actuels de plomb dans les rouges à lèvres ne posent pas de problème de sécurité (un sujet historique largement résolu, pas une préoccupation active)."
    }

    if ("mascara" in n || "eyeliner" in n) {
        facts += if (en) "Eye cosmetics (mascara, eyeliner) are commonly recommended for replacement roughly every 3 months, since repeated applicator contact with the eye area is a recognized route for microbial contamination over time."
                 else "Les cosmétiques pour les yeux (mascara, eyeliner) sont généralement recommandés à remplacer tous les 3 mois environ, car le contact répété de l'applicateur avec la zone oculaire est une voie reconnue de contamination microbienne progressive."
    }

    return MakeupEducationalFacts(facts)
}
