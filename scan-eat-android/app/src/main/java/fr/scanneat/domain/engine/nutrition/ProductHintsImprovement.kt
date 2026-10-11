package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Deduction
import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.ScoreAudit
import fr.scanneat.domain.model.Severity

// ============================================================================
// Score-aware hint panel data — user-reported: the hint panel was helpful but
// underwhelming, all flat-styled facts/warnings with no visible connection to
// the actual grade, no severity distinction between a critical issue and a
// minor one, and no at-a-glance sense of *where* the score comes from.
// ScoringEngine.kt already computes all of this (grade, per-pillar scores,
// per-deduction reason/points/severity) - it just never reached the hint
// panel, only PillarsSection's own expandable rows deep in the Result screen.
// ============================================================================

/** One "what's costing points" line — kept structured (not pre-formatted
 *  text) so the UI can color-code by [severity] and format [points] as its
 *  own badge instead of an inline string suffix. */
data class ImprovementTip(val reason: String, val points: Double, val severity: Severity)

/** One pillar's score/max, for the panel's at-a-glance breakdown bar row. */
data class PillarSummary(val name: String, val score: Double, val max: Int)

/** Score-header summary — grade/value/verdict, shown at the top of the panel
 *  so a hint read in isolation still carries the score it explains. */
data class ScoreSummary(val grade: Grade, val value: Int, val verdict: String)

internal fun buildScoreSummary(audit: ScoreAudit): ScoreSummary =
    ScoreSummary(audit.grade, audit.score, audit.verdict)

internal fun buildPillarSummary(audit: ScoreAudit): List<PillarSummary> = with(audit.pillars) {
    listOf(processing, nutritionalDensity, negativeNutrients, additiveRisk, ingredientIntegrity)
        .map { PillarSummary(it.name, it.score, it.max) }
}

/**
 * The [limit] deductions (across every pillar plus global penalties) with
 * the largest point cost. Bonuses are excluded (this section answers
 * "what's dragging the score down", not "what's already good" - that's
 * [ProductHintsBenefitsRisks]'s job). Ties are broken by pillar order
 * (processing, nutritionalDensity, negativeNutrients, additiveRisk,
 * ingredientIntegrity) so the ranking is stable across repeated calls, not
 * dependent on each pillar's own internal list order.
 */
internal fun buildImprovementTips(audit: ScoreAudit, limit: Int = 5): List<ImprovementTip> {
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
        .map { d -> ImprovementTip(d.reason, d.points, d.severity) }
}
