package fr.scanneat.domain.engine.nutrition

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
) {
    companion object {
        /** Fallback for a combine-into-map StateFlow lookup miss (e.g. the one-frame
         *  gap right after a new recipe/template is added, before its hints entry
         *  lands) — same role as NutritionPer100g.EMPTY elsewhere in the codebase. */
        val EMPTY = ProductHints(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

fun generateProductHints(product: Product, profile: Profile, lang: String): ProductHints {
    val benefits = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val conditionRisks = mutableListOf<String>()

    appendGeneralBenefits(product, lang, benefits, risks)
    appendGeneralRisks(product, lang, risks)

    // ---- Personalized (Profile.healthConditions) ----
    val containsCaffeineSource = appendPersonalizedHints(product, profile, lang, benefits, conditionRisks)

    val keyInfo = buildKeyInfo(product, lang)
    val facts = buildFacts(product, lang)
    val (pairWell, avoidPairing) = buildPairings(product, lang, containsCaffeineSource)

    return ProductHints(benefits, risks, conditionRisks, facts, keyInfo, pairWell, avoidPairing)
}
