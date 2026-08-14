package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.medication.checkFoodDrugInteractions
import fr.scanneat.domain.engine.scoring.scoreProduct
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile

// ============================================================================
// PRODUCT HINTS — the "💡" info panel's content. Deliberately rule-based off
// data already on the Product (NOVA class, nutrition thresholds, organic/
// fermented flags) and, where the profile has it, Profile.healthConditions
// — not fabricated medical claims: every hint traces back to a concrete
// field, so there is no line here that isn't backed by either the
// product's own declared data or a cited public-health guidance source
// (see HealthConditionGuidanceDb).
//
// generateProductHints itself just orchestrates, in order: the population-
// level rules (ProductHintsBenefitsRisks.kt), the profile-personalized rules
// (ProductHintsPersonalization.kt), the key-info/facts section
// (ProductHintsFacts.kt), and the pairing rules (ProductHintsPairings.kt).
// ============================================================================

data class ProductHints(
    val benefits: List<String>,
    /** General, population-level cautions - apply to anyone regardless of profile
     *  (high sat fat/sugar/salt, trans fat, NOVA 4, additive concerns...). */
    val risks: List<String>,
    /** Risks specific to *this* user's own profile (declared allergens, chosen
     *  diet, and Profile.healthConditions) - previously merged into [risks], so
     *  a generic "high sugar" caution that applies to everyone and a "caution
     *  advised for diabetes" line that only applies because of this one user's
     *  own condition were visually indistinguishable, even though they mean
     *  very different things to a reader without a diabetes diagnosis. */
    val conditionRisks: List<String>,
    /** User-requested: does the app know about medication+ingredient risks?
     *  Previously no real cross-reference existed anywhere (only static,
     *  non-product-specific text on a medication's own detail sheet) - see
     *  checkFoodDrugInteractions's own doc comment. Kept separate from
     *  [conditionRisks] (profile.healthConditions-driven) since this is
     *  driven by the user's active Medication list instead, a different
     *  data source the reader should be able to tell apart at a glance. */
    val medicationRisks: List<String> = emptyList(),
    val facts: List<String>,
    /** NOVA processing class + energy density — shown in their own section
     *  ahead of risks/benefits (see generateProductHints's own comment on
     *  why these two are split out of [facts] rather than folded into it). */
    val keyInfo: List<String> = emptyList(),
    /** What complements this product nutritionally or gastronomically — flavor
     *  pairings (Ahn et al. flavor-network co-occurrence, same PairingsDb the
     *  standalone PairingsCard already uses) plus absorption-enhancer pairings
     *  (e.g. vitamin C alongside an iron source). */
    val pairWell: List<String> = emptyList(),
    /** What to avoid pairing this product with — nutrient-absorption inhibitor
     *  interactions (e.g. tea/coffee tannins alongside an iron source), not a
     *  flavor judgment. */
    val avoidPairing: List<String> = emptyList(),
    /** User-reported: the panel was helpful but underwhelming, all passive
     *  facts with nothing telling the reader why the grade landed where it
     *  did, no severity distinction, and no visible connection to the score
     *  itself. Structured (not pre-formatted text) so the UI can color-code
     *  by severity - see ProductHintsImprovement.kt. */
    val improvementTips: List<ImprovementTip> = emptyList(),
    /** Grade/score/verdict header, and the 5-pillar breakdown bar row - the
     *  same real ScoreAudit data PillarsSection already shows deep in the
     *  Result screen, now visible from the hint panel itself so a hint read
     *  in isolation still carries the score context it explains. */
    val scoreSummary: ScoreSummary? = null,
    val pillarSummary: List<PillarSummary> = emptyList(),
) {
    companion object {
        /** Fallback for a combine-into-map StateFlow lookup miss (e.g. the one-frame
         *  gap right after a new recipe/template is added, before its hints entry
         *  lands) — same role as NutritionPer100g.EMPTY elsewhere in the codebase. */
        val EMPTY = ProductHints(benefits = emptyList(), risks = emptyList(), conditionRisks = emptyList(), facts = emptyList())
    }
}

/**
 * [activeMedicationNames] is optional (defaults to none) - only the Result
 * screen (the food's own "fiche de scan") currently has a reactive
 * MedicationRepository read wired in to pass real data here; Recipes/
 * Templates/CustomFood's own call sites keep the prior behavior (no
 * medication cross-reference) until they get the same wiring.
 *
 * [todaysLoggedFoodNames] - same pattern as [activeMedicationNames]: optional,
 * defaults to none, only Result screen has a reactive ConsumptionRepository
 * read wired in to pass today's already-logged foods here (see
 * ProductHintsPairings.buildPairings's own doc comment).
 */
fun generateProductHints(product: Product, profile: Profile, lang: String, activeMedicationNames: Set<String> = emptySet(), todaysLoggedFoodNames: Set<String> = emptySet()): ProductHints {
    val benefits = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val conditionRisks = mutableListOf<String>()

    appendGeneralBenefits(product, lang, benefits, risks)
    appendGeneralRisks(product, lang, risks)

    // ---- Personalized (Profile.healthConditions) ----
    val containsCaffeineSource = appendPersonalizedHints(product, profile, lang, benefits, conditionRisks)
    val medicationRisks = checkFoodDrugInteractions(product, activeMedicationNames, lang)

    val keyInfo = buildKeyInfo(product, lang)
    val facts = buildFacts(product, lang).toMutableList()
    appendWaterMineralHints(product, lang, benefits, facts)
    appendSeasonalFact(product, lang, facts)
    val (pairWell, avoidPairing) = buildPairings(product, lang, containsCaffeineSource, profile.healthConditions, todaysLoggedFoodNames)
    // scoreProduct is a pure function of Product alone (see ScoringEngine.kt) -
    // computed once here rather than threading a ScoreAudit through every one
    // of this function's 6 call sites, several of which (Recipes/Templates/
    // CustomFood) never compute one for their own preview-only Product anyway.
    val audit = scoreProduct(product, lang)
    val improvementTips = buildImprovementTips(audit)
    val scoreSummary = buildScoreSummary(audit)
    val pillarSummary = buildPillarSummary(audit)

    return ProductHints(
        benefits = benefits, risks = risks, conditionRisks = conditionRisks, medicationRisks = medicationRisks,
        facts = facts, keyInfo = keyInfo, pairWell = pairWell, avoidPairing = avoidPairing,
        improvementTips = improvementTips, scoreSummary = scoreSummary, pillarSummary = pillarSummary,
    )
}
