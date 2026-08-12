package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// GENERAL COSMETIC/SKINCARE ACTIVES PROFILE — fourth in the planned
// per-category functional-score series (shampoo → gel douche → dentifrice →
// cosmétique → maquillage → hygiène intime), same "curated, user-reviewed
// ingredient list, descriptive not a verdict" approach as the earlier three.
// Covers face/body creams, lotions, and serums - NOT shampoo, shower gel, or
// toothpaste, which already have their own dedicated scores.
//
// Unlike shampoo/shower gel (surfactant harshness) or toothpaste (fluoride/
// abrasivity), general cosmetics don't share one obvious axis - this file
// instead recognizes a small set of the most common, well-evidenced "active"
// ingredient families (niacinamide, vitamin C, retinoids, UV filters) plus a
// humectant flag, rather than a single harsh/mild spectrum.
//
// Sourced from (13/08/2026 web search, independently verified same day -
// two PMIDs from the first research pass could not be confirmed and are
// intentionally NOT cited below; see the retinoid/humectant notes):
//   - Niacinamide: Tanno et al., "Nicotinamide increases biosynthesis of
//     ceramides..." (PubMed 10971324, Br J Dermatol 2000) for the skin-
//     barrier-lipid mechanism; Hakozaki et al., "The effect of niacinamide
//     on reducing cutaneous pigmentation and suppression of melanosome
//     transfer" (PubMed 12100180, Br J Dermatol 2002) for the pigmentation/
//     brightening mechanism; Draelos, Matsubara & Smiles, "The effect of 2%
//     niacinamide on facial sebum production" (PubMed 16766489, J Cosmet
//     Laser Ther 2006) for the sebum-regulation effect.
//   - Vitamin C: "A topical antioxidant solution containing vitamins C and E
//     stabilized by ferulic acid provides protection for human skin against
//     [UV] damage" (PubMed 18603326, J Am Acad Dermatol 2008) established
//     the benchmark stabilized-formula efficacy; "Topical Application of
//     Ascorbic Acid and its Derivatives: A Review Considering Clinical
//     Trials" (PubMed 36200216, Curr Med Chem 2023) confirms L-ascorbic acid
//     is the only reliably bioactive form and that oxidation/instability is
//     a real, formulation-dependent efficacy problem - this file can only
//     detect ingredient PRESENCE, not whether a given product's formulation
//     actually kept the vitamin C stable, so this is flagged as presence of
//     an antioxidant active, not a guarantee of efficacy.
//   - Retinoids: caution (not efficacy) is what this file surfaces, since
//     cosmetic-grade retinol/retinyl palmitate have weaker/less direct
//     potency evidence than prescription tretinoin (a tretinoin-efficacy
//     meta-analysis was found but its PMID could not be independently
//     confirmed, so no efficacy claim is cited here). The caution IS solid:
//     "Topical retinoid use in women of reproductive age and risk of major
//     congenital malformations in exposed pregnancies: a Nordic cohort
//     study" (PubMed 41365815, 2026) found no clear increase in
//     malformations but the authors still recommend precautionary avoidance
//     during pregnancy, consistent with mainstream dermatologic guidance -
//     presented here as a precaution, not a proven-harm claim.
//   - UV filters: EU SCCS 2021 opinion on octocrylene - safe up to 10% but
//     confirms slow degradation to benzophenone (a possible endocrine
//     disruptor) on storage; mineral filters (titanium dioxide, zinc oxide)
//     are FDA GRASE-recognized with no equivalent degradation concern -
//     this file does not claim mineral filters are more EFFECTIVE (no
//     consensus found either way), only that they carry a different,
//     better-characterized safety margin.
//   - Hyaluronic acid/sodium hyaluronate: the strongest recent RCT evidence
//     found was for ORAL sodium hyaluronate supplementation, not topical
//     application - this file does NOT cite that study for a topical-cream
//     claim (would misrepresent the evidence, the same mistake corrected in
//     ShowerGelQualityScore's citation fix). Hyaluronic acid is listed only
//     as a HUMECTANT (a well-established, uncontroversial ingredient-
//     function classification, not a clinical-efficacy claim), the same
//     descriptive-only treatment glycerin gets in ShampooQualityScore.
// ============================================================================

enum class CosmeticActiveRole { NIACINAMIDE, VITAMIN_C, RETINOID, HUMECTANT, MINERAL_UV_FILTER, CHEMICAL_UV_FILTER_CAUTION }

private val COSMETIC_ACTIVE_ROLES: Map<String, CosmeticActiveRole> = mapOf(
    // BARRIER / PIGMENTATION / SEBUM - see header (Tanno 2000, Hakozaki 2002, Draelos 2006)
    "niacinamide" to CosmeticActiveRole.NIACINAMIDE,
    // ANTIOXIDANT - presence only, formulation stability not verifiable from an ingredient list (see header)
    "ascorbic acid" to CosmeticActiveRole.VITAMIN_C,
    "sodium ascorbyl phosphate" to CosmeticActiveRole.VITAMIN_C,
    "magnesium ascorbyl phosphate" to CosmeticActiveRole.VITAMIN_C,
    // RETINOID - efficacy not asserted here, only the precautionary pregnancy note (see header)
    "retinol" to CosmeticActiveRole.RETINOID,
    "retinyl palmitate" to CosmeticActiveRole.RETINOID,
    // HUMECTANT - descriptive ingredient-function classification only (see header)
    "sodium hyaluronate" to CosmeticActiveRole.HUMECTANT,
    "hyaluronic acid" to CosmeticActiveRole.HUMECTANT,
    "glycerin" to CosmeticActiveRole.HUMECTANT,
    // UV FILTER - mineral (FDA GRASE, no degradation concern - see header)
    "titanium dioxide" to CosmeticActiveRole.MINERAL_UV_FILTER,
    "zinc oxide" to CosmeticActiveRole.MINERAL_UV_FILTER,
    // UV FILTER - chemical, flagged for storage-degradation caution (see header, EU SCCS 2021)
    "octocrylene" to CosmeticActiveRole.CHEMICAL_UV_FILTER_CAUTION,
).mapKeys { (name, _) -> normalizeForMatching(name) }

data class CosmeticActivesResult(
    val hasNiacinamide: Boolean,
    val hasVitaminC: Boolean,
    val hasRetinoid: Boolean,
    val humectantCount: Int,
    val mineralUvFilterCount: Int,
    val chemicalUvFilterCautionCount: Int,
)

/** Product-name keyword gate for "is this a general cosmetic/skincare
 *  product" - same convention as ShampooQualityScore.isLikelyShampoo. Only
 *  applied when the more specific shampoo/shower-gel/toothpaste gates
 *  already returned false, so a mislabeled "crème lavante" isn't double-
 *  scored (see ScanStateOverlay's ordering of these checks). */
fun isLikelyGeneralCosmetic(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return listOf("creme", "cream", "serum", "lotion", "soin visage", "moisturizer", "moisturiser").any { it in n }
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see ShampooQualityScore.computeShampooQuality's own doc
 * comment on why this must be shown honestly, not silently scored).
 */
fun computeCosmeticActives(ingredientsText: String?): CosmeticActivesResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var niacinamide = false; var vitaminC = false; var retinoid = false
    var humectant = 0; var mineralUv = 0; var chemicalUvCaution = 0
    for (ingredient in ingredients) {
        when (COSMETIC_ACTIVE_ROLES[normalizeForMatching(ingredient)]) {
            CosmeticActiveRole.NIACINAMIDE               -> niacinamide = true
            CosmeticActiveRole.VITAMIN_C                 -> vitaminC = true
            CosmeticActiveRole.RETINOID                  -> retinoid = true
            CosmeticActiveRole.HUMECTANT                 -> humectant++
            CosmeticActiveRole.MINERAL_UV_FILTER         -> mineralUv++
            CosmeticActiveRole.CHEMICAL_UV_FILTER_CAUTION -> chemicalUvCaution++
            null -> {}
        }
    }
    // Consistency fix 13/08/2026 - see ShampooQualityScore.computeShampooQuality's
    // matching comment. Unlike ToothpasteQualityResult's hasFluoride,
    // nothing here has an unconditional "not found" display line
    // (CosmeticActivesSection only renders a line per field when true/>0),
    // so an all-false result would show nothing but the title and
    // disclaimer - the same "no real signal" case as Shampoo/ShowerGel.
    if (!niacinamide && !vitaminC && !retinoid && humectant == 0 && mineralUv == 0 && chemicalUvCaution == 0) return null
    return CosmeticActivesResult(niacinamide, vitaminC, retinoid, humectant, mineralUv, chemicalUvCaution)
}
