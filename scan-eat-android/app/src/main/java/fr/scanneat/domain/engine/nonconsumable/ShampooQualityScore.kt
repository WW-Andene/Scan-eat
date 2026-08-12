package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// SHAMPOO QUALITY PROFILE — user-requested: a real functional score for
// shampoo specifically (not just the generic composition/regulatory signal
// CosmeticTransparencyScore/CosingRegulatoryDb already give every non-food
// product). First of a planned per-category series (shampoo → gel douche →
// maquillage → cosmétique → hygiène intime, in that order).
//
// Source: the OFFICIAL CosIng "List of Functions" reference (~80 EU-defined
// cosmetic-ingredient function categories with descriptions), provided
// directly by the app owner 12/08/2026 - e.g. "SURFACTANT - CLEANSING",
// "SKIN CONDITIONING - OCCLUSIVE". This file maps a small, user-reviewed set
// of the most common shampoo ingredients to those official function
// categories - NOT a fabricated "good/bad ingredient" list. Each entry below
// was drafted from well-established, textbook cosmetic-chemistry
// classifications (surfactant type, silicone vs non-silicone conditioning)
// and explicitly reviewed/approved by the app owner before being wired in
// (see this session's own back-and-forth confirming the list). It is
// intentionally small and will grow incrementally, not an exhaustive INCI
// database - an ingredient not in this map contributes to neither count,
// rather than being guessed at.
//
// This is a CLEANSING-BASE PROFILE (surfactant harshness + conditioning
// approach), not a safety/hazard verdict - sulfates and silicones are both
// legal, common, EU-compliant ingredients; this only describes what kind of
// shampoo it is (gentle vs high-foaming cleanser, silicone-smoothed vs
// silicone-free), the same "descriptive, not a verdict" stance
// CosmeticTransparencyScore already takes.
//
// Refined with two peer-reviewed sources (12/08/2026 web search, both freely
// accessible):
//   - SLS vs SLES/glucoside/isethionate irritation ranking: SLS has
//     consistently higher measured irritation potential (TEWL increase,
//     squamometry) than SLES (ethoxylated, milder) and than glucoside/
//     isethionate/sarcosinate surfactants in controlled comparisons -
//     Marrakchi & Maibach, "Sodium lauryl sulfate induced irritant contact
//     dermatitis..." (PubMed 8917825); Charbonnier, Morrison, Paye & Maibach,
//     open-assay SLS vs SLES comparison (PubMed 11278060, Food Chem Toxicol
//     2001 - corrected 13/08/2026: an earlier draft of this comment
//     misattributed this PMID to Löffler & Effendy and to a 3-surfactant
//     SLS/SLES/glucoside comparison; independently verified, this paper is
//     actually a 2-surfactant SLS-vs-SLES-only study); Löffler & Effendy,
//     SLS/SLES/alkyl-polyglucoside patch-test comparison (PubMed 12641575,
//     Contact Dermatitis 2003 - this is the correct citation for the
//     3-surfactant "SLS worst, SLES milder, glucoside barely detectable"
//     finding).
//   - Silicone buildup is NOT a property of "silicones" as a class - it
//     specifically concerns water-INSOLUBLE, high-viscosity fractions
//     (dimethicone, dimethiconol); volatile/cyclic silicones like
//     cyclopentasiloxane evaporate rather than accumulate. "With or without
//     Silicones? A Comprehensive Review of Their Role in Hair Care", Skin
//     Appendage Disorders (Karger) - this is why VOLATILE_SILICONE is its
//     own role below, not lumped into the buildup-prone SILICONE role the
//     way this file's first version (shipped, then corrected in the same
//     session once these sources were checked) had it.
// ============================================================================

enum class ShampooIngredientRole { HARSH_SURFACTANT, MILD_SURFACTANT, SILICONE, VOLATILE_SILICONE, GENTLE_CONDITIONER }

private val SHAMPOO_INGREDIENT_ROLES: Map<String, ShampooIngredientRole> = mapOf(
    // SURFACTANT - CLEANSING (high-foaming; higher measured irritation potential - see header)
    "sodium lauryl sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "sodium laureth sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "ammonium lauryl sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "ammonium laureth sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    // SURFACTANT - CLEANSING (milder, lower measured irritation potential - see header)
    "cocamidopropyl betaine" to ShampooIngredientRole.MILD_SURFACTANT,
    "decyl glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "coco-glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "lauryl glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "sodium cocoyl isethionate" to ShampooIngredientRole.MILD_SURFACTANT,
    "disodium laureth sulfosuccinate" to ShampooIngredientRole.MILD_SURFACTANT,
    "sodium lauroyl sarcosinate" to ShampooIngredientRole.MILD_SURFACTANT,
    // HAIR CONDITIONING - OCCLUSIVE: water-insoluble, higher-viscosity silicones - can build up with repeated use (see header)
    "dimethicone" to ShampooIngredientRole.SILICONE,
    "amodimethicone" to ShampooIngredientRole.SILICONE,
    "dimethiconol" to ShampooIngredientRole.SILICONE,
    // HAIR CONDITIONING - OCCLUSIVE: volatile/cyclic silicone - evaporates, not the same buildup profile (see header)
    "cyclopentasiloxane" to ShampooIngredientRole.VOLATILE_SILICONE,
    // HAIR CONDITIONING / HUMECTANT (non-silicone conditioning/moisture)
    "glycerin" to ShampooIngredientRole.GENTLE_CONDITIONER,
    "panthenol" to ShampooIngredientRole.GENTLE_CONDITIONER,
    "behentrimonium chloride" to ShampooIngredientRole.GENTLE_CONDITIONER,
    "guar hydroxypropyltrimonium chloride" to ShampooIngredientRole.GENTLE_CONDITIONER,
    "hydrolyzed keratin" to ShampooIngredientRole.GENTLE_CONDITIONER,
    "hydrolyzed wheat protein" to ShampooIngredientRole.GENTLE_CONDITIONER,
).mapKeys { (name, _) -> normalizeForMatching(name) }

enum class CleansingBase { GENTLE, MIXED, HARSH, UNKNOWN }

data class ShampooQualityResult(
    val harshSurfactantCount: Int,
    val mildSurfactantCount: Int,
    val siliconeCount: Int,
    val volatileSiliconeCount: Int,
    val gentleConditionerCount: Int,
) {
    val cleansingBase: CleansingBase get() = when {
        harshSurfactantCount == 0 && mildSurfactantCount == 0 -> CleansingBase.UNKNOWN
        harshSurfactantCount > 0 && mildSurfactantCount == 0  -> CleansingBase.HARSH
        harshSurfactantCount == 0 && mildSurfactantCount > 0  -> CleansingBase.GENTLE
        else                                                  -> CleansingBase.MIXED
    }
}

/** Product-name keyword gate for "is this specifically a shampoo" - same
 *  keyword-based category detection classifyNonFood already uses for OFF tags. */
fun isLikelyShampoo(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return "shampoo" in n || "shampooing" in n
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see CosmeticTransparencyScore.computeCosmeticTransparency's
 * own doc comment on why this must be shown honestly, not silently scored).
 */
fun computeShampooQuality(ingredientsText: String?): ShampooQualityResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var harsh = 0; var mild = 0; var silicone = 0; var volatileSilicone = 0; var gentle = 0
    for (ingredient in ingredients) {
        when (SHAMPOO_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            ShampooIngredientRole.HARSH_SURFACTANT -> harsh++
            ShampooIngredientRole.MILD_SURFACTANT  -> mild++
            ShampooIngredientRole.SILICONE         -> silicone++
            ShampooIngredientRole.VOLATILE_SILICONE -> volatileSilicone++
            ShampooIngredientRole.GENTLE_CONDITIONER -> gentle++
            null -> {}
        }
    }
    // Consistency fix 13/08/2026 (caught auditing all 6 per-category
    // scores together): none of this curated list's ingredients were found
    // in the product's actual ingredient list - same "no real signal, don't
    // show an all-zero/UNKNOWN section" null already returned above for
    // blank/unparseable text, now applied here too. MakeupQualityScore/
    // IntimateHygieneScore already had this guard; Shampoo/ShowerGel/
    // Toothpaste/CosmeticActives did not - null and "found nothing" were the
    // same real-world state everywhere, only some of the six treated them
    // differently.
    if (harsh == 0 && mild == 0 && silicone == 0 && volatileSilicone == 0 && gentle == 0) return null
    return ShampooQualityResult(harsh, mild, silicone, volatileSilicone, gentle)
}
