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
// Sourced from (12/08/2026 web search, independently verified 13/08/2026 -
// two corrections made after verification, see below):
//   - Surfactant irritation ranking (SLS harshest, SLES milder, glucosides/
//     betaine mildest): Charbonnier, Morrison, Paye & Maibach, open-assay SLS
//     vs SLES comparison (PubMed 11278060, Food Chem Toxicol 2001 - corrected
//     13/08/2026: an earlier draft misattributed this PMID to Löffler &
//     Effendy and to a 3-surfactant SLS/SLES/glucoside comparison; it is
//     actually a 2-surfactant SLS-vs-SLES-only study); Löffler & Effendy,
//     SLS/SLES/alkyl-polyglucoside patch-test comparison (PubMed 12641575,
//     Contact Dermatitis 2003 - the correct citation for "SLS worst, SLES
//     milder, glucoside barely detectable"). A surfactant-mixture patch study
//     (PubMed 18503452) was also checked but is NOT cited for a directional
//     "co-surfactants reduce irritation" claim - independent verification
//     found its actual result is dose/combination-dependent (irritation was
//     highest with certain combinations, not simply lower than SLES alone),
//     so this file does not rely on it; the MIXED-better-than-HARSH ordering
//     below rests only on the two SLS/SLES/glucoside comparisons above.
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
    /** MIXED (harsh+mild together) is treated as better than HARSH-only,
     *  same ordering as ShampooQualityScore.CleansingBase - based on the
     *  SLS/SLES/glucoside irritation ranking in this file's header, not on
     *  PubMed 18503452 (checked but not relied on, see header). */
    val cleansingBase: ShowerGelCleansingBase get() = when {
        harshSurfactantCount == 0 && mildSurfactantCount == 0 -> ShowerGelCleansingBase.UNKNOWN
        harshSurfactantCount > 0 && mildSurfactantCount == 0  -> ShowerGelCleansingBase.HARSH
        harshSurfactantCount == 0 && mildSurfactantCount > 0  -> ShowerGelCleansingBase.GENTLE
        else                                                  -> ShowerGelCleansingBase.MIXED
    }
}

/** Product-name keyword gate for "is this specifically a shower gel/body wash" -
 *  same convention as ShampooQualityScore.isLikelyShampoo. */
// Shower-gel/body-wash-specific brands - added 13/08/2026, user-requested.
// Same "single-category brand only" bar as ShampooQualityScore's own list -
// a genuinely harder category to source cleanly for, since most big brands
// (Dove, Nivea, Le Petit Marseillais's own wider soap/hand-cream range) also
// sell shampoo/skincare under the same brand tag. Kept to 8 rather than
// padded to 10 with brands that are really deodorant-first (Axe, Rexona,
// Narta) or an unrelated category (Franck Provost is a hairdressing-salon
// brand, not shower gel) - an inaccurate list would misclassify real
// products, worse than a shorter accurate one.
private val SHOWER_GEL_BRANDS = listOf(
    "sanex", "le petit marseillais", "monsavon", "camay", "lux", "adidas",
    "zest", "cadum",
)

fun isLikelyShowerGel(productName: String, brand: String = ""): Boolean {
    val n = normalizeForMatching(productName)
    if ("gel douche" in n || "shower gel" in n || "body wash" in n || "gel de ducha" in n) return true
    val b = normalizeForMatching(brand)
    return SHOWER_GEL_BRANDS.any { it in b }
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
    // Consistency fix 13/08/2026 - see ShampooQualityScore.computeShampooQuality's
    // matching comment: none of this curated list's ingredients were found,
    // treated the same as "no data" rather than an all-zero result.
    if (harsh == 0 && mild == 0 && soap == 0 && provenEmollient == 0 && marketingEmollient == 0) return null
    return ShowerGelQualityResult(harsh, mild, soap, provenEmollient, marketingEmollient)
}
