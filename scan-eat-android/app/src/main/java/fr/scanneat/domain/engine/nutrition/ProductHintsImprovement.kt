package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Deduction
import fr.scanneat.domain.model.ScoreAudit

// ============================================================================
// "How to improve this score" — user-reported: the hint panel was helpful but
// underwhelming, all passive facts/warnings with nothing telling the reader
// WHY the grade landed where it did or what specifically to look for in a
// better alternative. ScoringEngine.kt already computes exactly that, per
// deduction, with real point values (Deduction.reason/points) - this was
// visible nowhere outside PillarsSection's own expandable rows deep in the
// Result screen. Surfaces the same data, ranked by actual impact, in the
// hint panel every screen with a HintIconButton already has.
// ============================================================================

/**
 * The [limit] deductions (across every pillar plus global penalties) with
 * the largest point cost, worded as "reason (points)" - e.g. "3 sucres
 * ajoutés distincts : ... (-2 pts)". Bonuses are excluded (this section
 * answers "what's dragging the score down", not "what's already good" -
 * that's [ProductHintsBenefitsRisks]'s job). Ties are broken by pillar order
 * (processing, nutritionalDensity, negativeNutrients, additiveRisk,
 * ingredientIntegrity) so the ranking is stable across repeated calls,
 * not dependent on each pillar's own internal list order.
 */
internal fun buildImprovementTips(audit: ScoreAudit, limit: Int = 4): List<String> {
    val allDeductions: List<Deduction> = audit.pillars.processing.deductions +
        audit.pillars.nutritionalDensity.deductions +
        audit.pillars.negativeNutrients.deductions +
        audit.pillars.additiveRisk.deductions +
        audit.pillars.ingredientIntegrity.deductions +
        audit.globalPenalties
    return allDeductions
        .filter { it.points < 0 }
        .sortedBy { it.points }
        .take(limit)
        .map { d ->
            val pts = d.points.let { if (it == it.toInt().toDouble()) it.toInt().toString() else "%.1f".format(it) }
            "${d.reason} ($pts pts)"
        }
}
