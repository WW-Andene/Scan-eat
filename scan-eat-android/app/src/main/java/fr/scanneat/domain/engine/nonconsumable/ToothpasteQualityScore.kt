package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// TOOTHPASTE / DENTIFRICE QUALITY PROFILE — third in the planned per-category
// functional-score series (shampoo → gel douche → dentifrice → maquillage →
// cosmétique → hygiène intime), same "curated, user-reviewed ingredient list,
// descriptive not a verdict" approach as ShampooQualityScore/
// ShowerGelQualityScore.
//
// Unlike shampoo/shower gel, toothpaste's single most evidence-backed axis is
// POSITIVE (fluoride = caries protection), not just "which surfactant is
// gentlest" - so this profile leads with fluoride presence rather than
// treating everything as a harshness axis.
//
// IMPORTANT LIMITATION: INCI ingredient lists do not carry concentration
// (ppm) data, only presence/absence of a compound. Fluoride effectiveness
// and abrasivity (RDA) are both dose-dependent (see header sources below) -
// this file can only report "contains a fluoride compound" / "contains a
// higher-abrasivity vs lower-abrasivity polishing agent", never a verified
// ppm or RDA number. This is the same "descriptive signal, not a lab
// measurement" honesty stance CosmeticTransparencyScore already takes.
//
// Sourced from (13/08/2026 web search, independently verified same day):
//   - Fluoride dose-response for caries prevention: Cochrane systematic
//     review, Walsh et al., "Fluoride toothpastes of different
//     concentrations for preventing dental caries" (CD007868) - 1000-1500ppm
//     reduces caries vs non-fluoride toothpaste, effect increases with
//     concentration, ≤550ppm shows no significant effect. This file cannot
//     read ppm from an ingredient list, so FLUORIDE below only marks
//     presence of a recognized fluoride compound (sodium fluoride, stannous
//     fluoride, sodium monofluorophosphate) - NOT a claim that any listed
//     product meets the effective concentration threshold.
//   - Abrasivity: ISO 11609 sets RDA ≤ 250 as the daily-use safety ceiling
//     (ADA guidance aligns). Comparative data (PubMed 11413496) found silica
//     generally more abrasive than calcium carbonate at similar particle
//     size (independently verified 13/08/2026: this paper is on-topic but is
//     a niche radiometric-abrasivity method study - the "RDA ~19-136" figure
//     an earlier draft attributed to it is not something this specific paper
//     is known to establish and has been dropped; only the qualitative
//     "silica generally more abrasive" ranking is kept) - so this file marks
//     silica as the higher-abrasivity ingredient relative to calcium
//     carbonate, not as "harsh" in absolute terms; no authoritative source
//     was found ranking sodium bicarbonate or dicalcium phosphate on the
//     same scale, so neither is included here.
//   - SLS and mouth ulcers: Alli et al., systematic review/meta-analysis
//     (PubMed 30839136, J Oral Pathol Med 2019) found SLS-free dentifrice
//     significantly reduced recurrent aphthous ulcer count/duration/pain vs
//     SLS-containing, from a meta-analysis subset of the reviewed trials -
//     graded low-certainty evidence, not proof SLS causes ulcers in the
//     general population. Kasi et al., scoping review of SLS oral side
//     effects (PubMed 35506963, Am J Dent 2022). Presented here as a
//     modest, low-certainty association, not a hazard verdict.
//   - Sensitivity relief: Cochrane review, Poulsen et al., "Potassium
//     nitrate toothpaste for dentine hypersensitivity" (PubMed 11405992,
//     CD001476 - corrected 13/08/2026: an earlier draft used this review's
//     later-update title "Potassium containing toothpastes..."; the PMID
//     itself was independently verified correct) - found a statistically
//     significant reduction in sensitivity scores but authors judged
//     evidence insufficient to confirm clinical efficacy overall; presented
//     as weak/mixed evidence.
// ============================================================================

enum class ToothpasteIngredientRole { FLUORIDE, HIGHER_ABRASIVE, LOWER_ABRASIVE, ORAL_IRRITANT, SENSITIVITY_CARE }

private val TOOTHPASTE_INGREDIENT_ROLES: Map<String, ToothpasteIngredientRole> = mapOf(
    // FLUORIDE - caries-protective compound, presence only (see header for the ppm caveat)
    "sodium fluoride" to ToothpasteIngredientRole.FLUORIDE,
    "stannous fluoride" to ToothpasteIngredientRole.FLUORIDE,
    "sodium monofluorophosphate" to ToothpasteIngredientRole.FLUORIDE,
    // POLISHING/ABRASIVE - higher vs lower relative abrasivity (see header)
    "hydrated silica" to ToothpasteIngredientRole.HIGHER_ABRASIVE,
    "silica" to ToothpasteIngredientRole.HIGHER_ABRASIVE,
    "calcium carbonate" to ToothpasteIngredientRole.LOWER_ABRASIVE,
    // SURFACTANT - modest, low-certainty oral-irritation association (see header)
    "sodium lauryl sulfate" to ToothpasteIngredientRole.ORAL_IRRITANT,
    // SENSITIVITY RELIEF - weak/mixed clinical evidence (see header)
    "potassium nitrate" to ToothpasteIngredientRole.SENSITIVITY_CARE,
).mapKeys { (name, _) -> normalizeForMatching(name) }

data class ToothpasteQualityResult(
    val hasFluoride: Boolean,
    val higherAbrasiveCount: Int,
    val lowerAbrasiveCount: Int,
    val oralIrritantCount: Int,
    val sensitivityCareCount: Int,
)

/** Product-name keyword gate for "is this specifically a toothpaste" - same
 *  convention as ShampooQualityScore.isLikelyShampoo. */
// Top 10 oral-care-only brands - added 13/08/2026, user-requested. Unlike
// shampoo/shower-gel, toothpaste brands are genuinely, almost always
// single-category (an oral-care company rarely also sells shampoo under the
// same brand name), so this list is on much firmer ground than the shower-
// gel one above.
private val TOOTHPASTE_BRANDS = listOf(
    "signal", "colgate", "elmex", "sensodyne", "parodontax", "fluocaril",
    "aquafresh", "oral-b", "emoform", "arthrodont",
)

fun isLikelyToothpaste(productName: String, brand: String = ""): Boolean {
    val n = normalizeForMatching(productName)
    if ("dentifrice" in n || "toothpaste" in n) return true
    val b = normalizeForMatching(brand)
    return TOOTHPASTE_BRANDS.any { it in b }
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see ShampooQualityScore.computeShampooQuality's own doc
 * comment on why this must be shown honestly, not silently scored).
 */
fun computeToothpasteQuality(ingredientsText: String?): ToothpasteQualityResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var fluoride = false; var higherAbrasive = 0; var lowerAbrasive = 0; var irritant = 0; var sensitivityCare = 0
    for (ingredient in ingredients) {
        when (TOOTHPASTE_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            ToothpasteIngredientRole.FLUORIDE         -> fluoride = true
            ToothpasteIngredientRole.HIGHER_ABRASIVE  -> higherAbrasive++
            ToothpasteIngredientRole.LOWER_ABRASIVE   -> lowerAbrasive++
            ToothpasteIngredientRole.ORAL_IRRITANT    -> irritant++
            ToothpasteIngredientRole.SENSITIVITY_CARE -> sensitivityCare++
            null -> {}
        }
    }
    // Deliberately NO all-zero/all-false null guard here, unlike the other
    // five per-category scores - considered during the 13/08/2026 audit that
    // added those guards, then rejected for this file specifically:
    // hasFluoride=false, even with every other field at 0, is itself real,
    // useful information ("this ingredient list was successfully read and
    // contains no recognized fluoride compound") the way an all-zero/
    // UNKNOWN cleansingBase in Shampoo/ShowerGel is NOT (that state conveys
    // nothing). Suppressing this result would hide a legitimate finding, not
    // just an empty one - see ToothpasteQualitySection's own unconditional
    // "no fluoride detected" line for the same reasoning on the display side.
    return ToothpasteQualityResult(fluoride, higherAbrasive, lowerAbrasive, irritant, sensitivityCare)
}
