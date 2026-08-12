package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// SHOWER GEL / GEL DOUCHE QUALITY PROFILE — second in the planned per-category
// functional-score series (shampoo → gel douche → maquillage → cosmétique →
// hygiène intime), same "curated, user-reviewed ingredient list, descriptive
// not a verdict" approach as ShampooQualityScore.
//
// Differs from the shampoo profile in two ways specific to skin (not hair):
//   1. No silicone buildup axis (irrelevant on skin) - replaced by a
//      SOAP_BASED axis, since traditional soap (sodium tallowate/palmate) is
//      still sometimes used in "gel douche" formulas and has real,
//      well-established downsides on skin the surfactant split alone
//      doesn't capture.
//   2. GENTLE_CONDITIONER is split into EMOLLIENT_PROVEN (clinically
//      evidenced moisturizers) vs EMOLLIENT_MARKETING (plausible but not
//      equivalently evidenced) rather than one bucket, since "moisturizing"
//      is gel douche's headline marketing claim and the evidence quality
//      genuinely varies.
//
// Sourced from (12/08/2026 web search, all freely accessible):
//   - Surfactant irritation ranking (SLS harshest, SLES milder, glucosides/
//     betaine mildest): Löffler & Effendy (PubMed 11278060, patch-test skin
//     irritation comparison); Wilhelm et al., surfactant-mixture irritation
//     patch study (PubMed 18503452) - showing betaine/glucoside co-surfactants
//     measurably reduce irritation from SLES-based systems, which is why
//     MIXED (harsh+mild together) is treated as better than HARSH-only below.
//   - Soap vs syndet (pH 9-10 alkaline soap disrupts the skin's acid mantle
//     and raises water loss, vs pH 5-7 syndet which preserves it): classic
//     dermatology literature - Prottey et al./soap irritancy studies (PubMed
//     3608584); soap vs syndet effect on stratum corneum in dry/elderly skin
//     (PubMed 2459871); pH-irritation correlation across marketed cleansers
//     (PubMed 12207765).
//   - Glycerin as a clinically-evidenced (not just marketed) moisturizer:
//     RCT of glycerol-based emollient improving measured skin barrier
//     function (PubMed 18025807); glycerol vs petrolatum crossover RCT
//     (PubMed 31532576). Botanical oils/butters (shea butter, argan, sweet
//     almond oil) have no equivalent clinical-trial evidence found - they
//     are common, legitimate, EU-compliant emollients, just with weaker
//     sourcing, hence the separate EMOLLIENT_MARKETING bucket rather than
//     omitting them or conflating them with glycerin/panthenol.
// ============================================================================

enum class ShowerGelIngredientRole { HARSH_SURFACTANT, MILD_SURFACTANT, SOAP_BASED, EMOLLIENT_PROVEN, EMOLLIENT_MARKETING }

private val SHOWER_GEL_INGREDIENT_ROLES: Map<String, ShowerGelIngredientRole> = mapOf(
    // SURFACTANT - CLEANSING (high-foaming; higher measured irritation potential - see header)
    "sodium lauryl sulfate" to ShowerGelIngredientRole.HARSH_SURFACTANT,
    "sodium laureth sulfate" to ShowerGelIngredientRole.HARSH_SURFACTANT,
    "ammonium lauryl sulfate" to ShowerGelIngredientRole.HARSH_SURFACTANT,
    "ammonium laureth sulfate" to ShowerGelIngredientRole.HARSH_SURFACTANT,
    // SURFACTANT - CLEANSING (milder, lower measured irritation potential - see header)
    "cocamidopropyl betaine" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "decyl glucoside" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "coco-glucoside" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "lauryl glucoside" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "sodium cocoyl isethionate" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "disodium laureth sulfosuccinate" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "sodium lauroyl sarcosinate" to ShowerGelIngredientRole.MILD_SURFACTANT,
    "sodium methyl cocoyl taurate" to ShowerGelIngredientRole.MILD_SURFACTANT,
    // TRADITIONAL SOAP - alkaline, disrupts the skin's acid mantle (see header)
    "sodium tallowate" to ShowerGelIngredientRole.SOAP_BASED,
    "sodium palmate" to ShowerGelIngredientRole.SOAP_BASED,
    "sodium cocoate" to ShowerGelIngredientRole.SOAP_BASED,
    "potassium tallowate" to ShowerGelIngredientRole.SOAP_BASED,
    // EMOLLIENT - clinically evidenced (RCT-backed, see header)
    "glycerin" to ShowerGelIngredientRole.EMOLLIENT_PROVEN,
    "panthenol" to ShowerGelIngredientRole.EMOLLIENT_PROVEN,
    // EMOLLIENT - plausible, widely used, but no equivalent clinical-trial evidence found (see header)
    "butyrospermum parkii butter" to ShowerGelIngredientRole.EMOLLIENT_MARKETING,
    "shea butter" to ShowerGelIngredientRole.EMOLLIENT_MARKETING,
    "argania spinosa kernel oil" to ShowerGelIngredientRole.EMOLLIENT_MARKETING,
    "prunus amygdalus dulcis oil" to ShowerGelIngredientRole.EMOLLIENT_MARKETING,
    "cocos nucifera oil" to ShowerGelIngredientRole.EMOLLIENT_MARKETING,
).mapKeys { (name, _) -> normalizeForMatching(name) }

enum class ShowerGelCleansingBase { GENTLE, MIXED, HARSH, UNKNOWN }

data class ShowerGelQualityResult(
    val harshSurfactantCount: Int,
    val mildSurfactantCount: Int,
    val soapBasedCount: Int,
    val provenEmollientCount: Int,
    val marketingEmollientCount: Int,
) {
    /** MIXED (harsh+mild together) is treated as better than HARSH-only -
     *  co-surfactants measurably reduce irritation from SLES-based systems
     *  (see header, Wilhelm et al. PubMed 18503452). */
    val cleansingBase: ShowerGelCleansingBase get() = when {
        harshSurfactantCount == 0 && mildSurfactantCount == 0 -> ShowerGelCleansingBase.UNKNOWN
        harshSurfactantCount > 0 && mildSurfactantCount == 0  -> ShowerGelCleansingBase.HARSH
        harshSurfactantCount == 0 && mildSurfactantCount > 0  -> ShowerGelCleansingBase.GENTLE
        else                                                  -> ShowerGelCleansingBase.MIXED
    }
}

/** Product-name keyword gate for "is this specifically a shower gel/body wash" -
 *  same convention as ShampooQualityScore.isLikelyShampoo. */
fun isLikelyShowerGel(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return "gel douche" in n || "shower gel" in n || "body wash" in n || "gel de ducha" in n
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see ShampooQualityScore.computeShampooQuality's own doc
 * comment on why this must be shown honestly, not silently scored).
 */
fun computeShowerGelQuality(ingredientsText: String?): ShowerGelQualityResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var harsh = 0; var mild = 0; var soap = 0; var provenEmollient = 0; var marketingEmollient = 0
    for (ingredient in ingredients) {
        when (SHOWER_GEL_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            ShowerGelIngredientRole.HARSH_SURFACTANT     -> harsh++
            ShowerGelIngredientRole.MILD_SURFACTANT      -> mild++
            ShowerGelIngredientRole.SOAP_BASED           -> soap++
            ShowerGelIngredientRole.EMOLLIENT_PROVEN     -> provenEmollient++
            ShowerGelIngredientRole.EMOLLIENT_MARKETING  -> marketingEmollient++
            null -> {}
        }
    }
    return ShowerGelQualityResult(harsh, mild, soap, provenEmollient, marketingEmollient)
}
