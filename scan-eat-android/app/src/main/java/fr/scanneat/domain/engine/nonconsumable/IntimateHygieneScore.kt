package fr.scanneat.domain.engine.nonconsumable

import fr.scanneat.domain.engine.scoring.normalizeForMatching

// ============================================================================
// INTIMATE HYGIENE PROFILE — fifth and last in the planned per-category
// functional-score series (shampoo → gel douche → dentifrice → cosmétique →
// hygiène intime). Covers intimate wet wipes, tampons, pads, and toilet
// paper - deliberately split into TWO different mechanisms below, because
// this category is NOT uniform the way the earlier ones were:
//
//   1. Intimate wet wipes DO carry an INCI ingredient list (like shampoo/
//      shower gel), so computeIntimateWipeQuality() below follows the same
//      ingredient-role-map pattern as the earlier scores.
//   2. Tampons/pads almost never carry a usable INCI ingredient list on
//      Open Products Facts (they're absorbent-fiber products, not
//      formulated liquids) - so generateAbsorbentHygieneFacts() below is
//      deliberately NOT a per-ingredient score. It surfaces general,
//      well-sourced educational facts about this product category, the
//      same NonConsumableHints-style "category fact, not a per-product
//      verdict" approach already used elsewhere in this app, kept in its
//      own function here (rather than folded into NonConsumableHints)
//      because it needs the product name to detect "bio"/"organic" and
//      "sans chlore"/"chlorine-free" label claims, which NonConsumableHints
//      doesn't have access to.
//
// This category involves genuine, sometimes-emotive health topics
// (menstrual products, mucosal exposure) - extra care was taken during
// sourcing (13/08/2026) to separate WELL-ESTABLISHED claims from
// UNRESOLVED/ACTIVE-RESEARCH ones, and two claims researched but not found
// with a citable primary source (fragranced toilet paper causing contact
// dermatitis; a single authoritative body ranking mucosal fragrance
// sensitivity above other body-care fragrance exposure) are intentionally
// NOT included below rather than asserted without a source.
//
// Sourced from (13/08/2026 web search):
//   - WELL-ESTABLISHED: modern tampons sold in the US/EU use elemental
//     chlorine-free (ECF) or chlorine-free bleaching; FDA states this
//     prevents dangerous dioxin levels, and FDA guidance requires products
//     be essentially free of TCDD/TCDF dioxin and pesticide/herbicide
//     residues. Historical (pre-1990s) elemental-chlorine bleaching did
//     produce measurable dioxin - that specific process is obsolete. FDA,
//     "The Facts on Tampons—and How to Use Them Safely"; FDA guidance
//     "Menstrual Tampons and Pads: Information for Premarket Notification
//     Submissions."
//   - WEAKLY EVIDENCED (included as a caution against overpaying for an
//     unproven claim, not a hazard flag): organic vs conventional cotton
//     tampons - no clinical evidence organic tampons are safer for health
//     outcomes; the difference is compositional/environmental (no rayon
//     blend, no chlorine bleach, no pesticide residue), not a demonstrated
//     clinical benefit. EPA considers glyphosate residue levels in
//     conventional cotton products unlikely to be harmful.
//   - UNRESOLVED / ACTIVE RESEARCH (explicitly flagged as such, not
//     presented as either "safe" or "dangerous"): a 2024 Columbia
//     University study (Environment International) detected trace metals
//     across tampon brands but did not establish release or absorption in
//     the body; FDA's own December 2024 literature review found "no clear
//     evidence of health risks" but flagged research gaps and is running
//     its own lab study (FDA, "Contaminants in Vaginal Tampons: A
//     Systematic Literature Review").
//   - WELL-ESTABLISHED: normal vaginal pH is ~3.8-4.5 (standard
//     obstetric/gynecologic physiology - e.g. Cleveland Clinic patient
//     education material, consistent with the clinical literature).
//   - MODERATE EVIDENCE (mechanism-level, best documented for douching
//     specifically, thinner but corroborating for wipes/washes): products
//     that alter vaginal pH or have direct antibacterial action (fragrance,
//     harsh surfactants, alcohol, douching) can disrupt the Lactobacillus-
//     dominated vaginal flora and raise pH, a known pathway toward
//     bacterial vaginosis risk - "Effects of feminine hygiene products on
//     the vaginal mucosal biome" (PMC3758931); "The INTIMATE Study"
//     (PMC12538639).
// ============================================================================

enum class IntimateWipeIngredientRole { FRAGRANCE, ALCOHOL, PH_BUFFERING }

private val INTIMATE_WIPE_INGREDIENT_ROLES: Map<String, IntimateWipeIngredientRole> = mapOf(
    // FRAGRANCE - moderate-evidence mucosal-irritation/flora-disruption concern (see header)
    "parfum" to IntimateWipeIngredientRole.FRAGRANCE,
    "fragrance" to IntimateWipeIngredientRole.FRAGRANCE,
    // ALCOHOL - can dry/irritate mucosal tissue and disrupt flora (see header)
    "alcohol denat" to IntimateWipeIngredientRole.ALCOHOL,
    "alcohol" to IntimateWipeIngredientRole.ALCOHOL,
    "ethanol" to IntimateWipeIngredientRole.ALCOHOL,
    // PH-BUFFERING - supports the ~3.8-4.5 normal vaginal pH range (see header)
    "lactic acid" to IntimateWipeIngredientRole.PH_BUFFERING,
).mapKeys { (name, _) -> normalizeForMatching(name) }

data class IntimateWipeQualityResult(
    val hasFragrance: Boolean,
    val hasAlcohol: Boolean,
    val hasPhBuffering: Boolean,
)

/** Product-name keyword gate for "is this specifically an intimate wet wipe". */
fun isLikelyIntimateWipe(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return "lingette intime" in n || "toilette intime" in n || "intimate wipe" in n || "feminine wipe" in n
}

/**
 * Returns null when [ingredientsText] is null/blank - "no data" is a real,
 * common state (see ShampooQualityScore.computeShampooQuality's own doc
 * comment on why this must be shown honestly, not silently scored).
 */
fun computeIntimateWipeQuality(ingredientsText: String?): IntimateWipeQualityResult? {
    if (ingredientsText.isNullOrBlank()) return null
    val ingredients = parseIngredientsText(ingredientsText)
    if (ingredients.isEmpty()) return null
    var fragrance = false; var alcohol = false; var phBuffering = false
    for (ingredient in ingredients) {
        when (INTIMATE_WIPE_INGREDIENT_ROLES[normalizeForMatching(ingredient)]) {
            IntimateWipeIngredientRole.FRAGRANCE     -> fragrance = true
            IntimateWipeIngredientRole.ALCOHOL       -> alcohol = true
            IntimateWipeIngredientRole.PH_BUFFERING  -> phBuffering = true
            null -> {}
        }
    }
    if (!fragrance && !alcohol && !phBuffering) return null
    return IntimateWipeQualityResult(fragrance, alcohol, phBuffering)
}

/** Product-name keyword gate for "is this a tampon/pad/menstrual product" -
 *  these products rarely carry a usable ingredient list, see header. */
fun isLikelyAbsorbentHygieneProduct(productName: String): Boolean {
    val n = normalizeForMatching(productName)
    return listOf("tampon", "serviette hygienique", "protege-slip", "sanitary pad", "menstrual pad", "coupe menstruelle", "menstrual cup").any { it in n }
}

data class AbsorbentHygieneFacts(val facts: List<String>, val notes: List<String>)

/**
 * NOT a per-ingredient score (see header for why) - a small set of
 * well-sourced educational facts about tampons/pads generally, plus a
 * claim-detection check against the product name for "bio"/"organic" and
 * "sans chlore"/"chlorine-free" labeling, each framed at its real evidence
 * strength (see header: WELL-ESTABLISHED / WEAKLY EVIDENCED / UNRESOLVED).
 */
fun generateAbsorbentHygieneFacts(productName: String, lang: String): AbsorbentHygieneFacts {
    val en = lang == "en"
    val n = normalizeForMatching(productName)
    val facts = mutableListOf<String>()
    val notes = mutableListOf<String>()

    facts += if (en) "Tampons sold in the US and EU today use chlorine-free bleaching processes; regulators require them to be essentially free of dioxin residue - the historical elemental-chlorine bleaching process linked to measurable dioxin is obsolete (FDA)."
             else "Les tampons vendus aujourd'hui aux États-Unis et dans l'UE utilisent des procédés de blanchiment sans chlore élémentaire ; la réglementation impose qu'ils soient quasiment exempts de résidus de dioxine — l'ancien procédé au chlore élémentaire, associé à des niveaux de dioxine mesurables, n'est plus utilisé (FDA)."

    if ("bio" in n || "organic" in n) {
        notes += if (en) "\"Organic cotton\" labeling reflects farming/bleaching differences (no pesticide residue, no chlorine bleach) - no clinical evidence currently shows a proven health advantage over conventional cotton tampons, so this is an environmental/compositional claim, not a demonstrated safety benefit."
                 else "La mention « coton bio » reflète des différences de culture/blanchiment (pas de résidu de pesticide, pas de blanchiment au chlore) — aucune preuve clinique actuelle ne démontre un avantage santé prouvé par rapport aux tampons en coton conventionnel : c'est une allégation environnementale/de composition, pas un bénéfice de sécurité démontré."
    }

    notes += if (en) "Ongoing research (a 2024 university study, and FDA's own December 2024 literature review) has looked at trace metals in tampons across brands; FDA states current evidence shows no clear health risk but flags real research gaps and is running further testing - this is an active, unresolved area, not a settled safety concern."
             else "Des recherches en cours (une étude universitaire de 2024, et la revue de littérature de la FDA de décembre 2024) se sont penchées sur des traces de métaux dans les tampons, tous fabricants confondus ; la FDA indique qu'à ce jour rien ne démontre clairement de risque pour la santé, tout en signalant de réelles lacunes de recherche et en menant des tests complémentaires — un sujet actif et non tranché, pas une inquiétude de sécurité établie."

    return AbsorbentHygieneFacts(facts, notes)
}
