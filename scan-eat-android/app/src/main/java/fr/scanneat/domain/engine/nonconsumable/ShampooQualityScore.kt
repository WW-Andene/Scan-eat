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
// ============================================================================

enum class ShampooIngredientRole { HARSH_SURFACTANT, MILD_SURFACTANT, SILICONE, GENTLE_CONDITIONER }

private val SHAMPOO_INGREDIENT_ROLES: Map<String, ShampooIngredientRole> = mapOf(
    // SURFACTANT - CLEANSING (high-foaming, can be drying with frequent use)
    "sodium lauryl sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "sodium laureth sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "ammonium lauryl sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    "ammonium laureth sulfate" to ShampooIngredientRole.HARSH_SURFACTANT,
    // SURFACTANT - CLEANSING (milder, lower-irritation cleansing base)
    "cocamidopropyl betaine" to ShampooIngredientRole.MILD_SURFACTANT,
    "decyl glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "coco-glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "lauryl glucoside" to ShampooIngredientRole.MILD_SURFACTANT,
    "sodium cocoyl isethionate" to ShampooIngredientRole.MILD_SURFACTANT,
    "disodium laureth sulfosuccinate" to ShampooIngredientRole.MILD_SURFACTANT,
    "sodium lauroyl sarcosinate" to ShampooIngredientRole.MILD_SURFACTANT,
    // HAIR CONDITIONING - OCCLUSIVE (silicones - smoothing, but can build up with repeated use)
    "dimethicone" to ShampooIngredientRole.SILICONE,
    "amodimethicone" to ShampooIngredientRole.SILICONE,
    "cyclopentasiloxane" to ShampooIngredientRole.SILICONE,
    "dimethiconol" to ShampooIngredientRole.SILICONE,
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
    var harsh = 0; var mild = 0; var silicone = 0; var gentle = 0
    for (ingredient in ingredients) {
        when (SHAMPOO_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            ShampooIngredientRole.HARSH_SURFACTANT -> harsh++
            ShampooIngredientRole.MILD_SURFACTANT  -> mild++
            ShampooIngredientRole.SILICONE         -> silicone++
            ShampooIngredientRole.GENTLE_CONDITIONER -> gentle++
            null -> {}
        }
    }
    return ShampooQualityResult(harsh, mild, silicone, gentle)
}
