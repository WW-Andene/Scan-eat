package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ============================================================================
// Main function
// ============================================================================

/**
 * Compute the personal score overlay on top of the classic ScoreAudit.
 * Port of computePersonalScore() from personal-score.js.
 *
 * @param audit       Output of scoreProduct()
 * @param product     The same product that was scored
 * @param profile     User profile (diet, allergens, sex, age, weight, activity)
 * @param lang        "fr" or "en" for reason strings
 * @param bioTdeeKcal Optional Biolism-computed TDEE (BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay)
 *   to rescale the daily sat-fat/sugar budget adjustment onto, matching what
 *   Dashboard/Diary/Widget already show for the same day - see
 *   computeDailyTargetAdjustments's own doc comment.
 */
fun computePersonalScore(
    audit: ScoreAudit,
    product: Product,
    profile: Profile,
    lang: String = "fr",
    bioTdeeKcal: Double? = null,
): PersonalScoreResult {

    val applicable = (profile.diet != DietKey.NONE) ||
        hasMinimalProfile(profile) ||
        profile.allergens.isNotEmpty() ||
        profile.healthConditions.isNotEmpty()

    if (!applicable) {
        return PersonalScoreResult(
            personalScore = audit.score,
            delta         = 0,
            adjustments   = emptyList(),
            applicable    = false,
            dietReason    = null,
            veto          = false,
        )
    }

    val catThresholds = getThresholds(product.category)
    // Matches checkVeto's own already-established SSB definition exactly
    // (BEVERAGE_SOFT, sugar>5g, protein<1g, fiber<1g) - see checkHealthConditions'
    // own parameter doc for why every condition/BMI check needs to agree on
    // this instead of re-deriving a subtly different bar each time.
    val isSugarSweetenedBeverage = product.category == ProductCategory.BEVERAGE_SOFT &&
        (product.nutrition.addedSugarsG ?: product.nutrition.sugarsG) > 5.0 &&
        product.nutrition.proteinG < 1.0 && product.nutrition.fiberG < 1.0

    val adjustments = mutableListOf<PersonalAdjustment>()
    var veto = false

    val dietResult = checkDietCompliance(product, profile, lang)
    adjustments += dietResult.adjustments
    veto = veto || dietResult.veto

    // ===== ALLERGEN CHECK =====
    val allergenHits = if (profile.allergens.isNotEmpty())
        checkUserAllergens(product, profile.allergens, lang)
    else emptyList()

    val conditionsResult = checkHealthConditions(product, profile, lang, catThresholds, isSugarSweetenedBeverage)
    adjustments += conditionsResult.adjustments
    veto = veto || conditionsResult.veto

    // Both checks above can independently veto the same product (e.g. a vegan
    // profile that's also pregnant, scanning something with both gelatin and
    // alcohol) - previously an Elvis-chained `dietReason ?: otherReason` kept only
    // whichever fired first and silently dropped the other, so DietVetoBanner (the
    // one UI surface explaining why the score was zeroed) could hide a genuinely
    // safety-relevant health-condition reason behind an unrelated diet reason.
    val dietReason = listOfNotNull(dietResult.dietReason, conditionsResult.dietReason)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

    adjustments += computeAgeAdjustments(product, profile, catThresholds, isSugarSweetenedBeverage, lang)
    adjustments += computeSexAdjustments(product, profile, lang)
    adjustments += computeActivityAdjustments(product, profile, lang)
    adjustments += computeGoalAdjustments(product, profile, catThresholds, lang)
    adjustments += computeProteinPriAdjustments(product, profile, lang)
    adjustments += computeDailyTargetAdjustments(product, profile, lang, bioTdeeKcal)
    adjustments += computeBmiAdjustments(product, profile, catThresholds, isSugarSweetenedBeverage, lang)

    val delta = adjustments.sumOf { it.points }

    var personalScore = if (veto) 0.0
        else (audit.score + delta).coerceIn(0.0, 100.0)

    // Re-apply engine veto cap after personal delta (same as JS Fix #7)
    if (!veto && audit.veto.triggered && personalScore > audit.veto.cap) {
        personalScore = audit.veto.cap.toDouble()
    }

    return PersonalScoreResult(
        personalScore = personalScore.roundToInt(),
        delta         = if (veto) -audit.score else delta.roundToInt(),
        adjustments   = adjustments,
        applicable    = true,
        dietReason    = dietReason,
        veto          = veto,
        allergenHits  = allergenHits,
    )
}

/** Map 0-100 personal score to grade. Same breakpoints as the main engine. */
fun personalGrade(score: Int): Grade = when {
    score >= 85 -> Grade.A_PLUS
    score >= 70 -> Grade.A
    score >= 55 -> Grade.B
    score >= 40 -> Grade.C
    score >= 25 -> Grade.D
    else        -> Grade.F
}
