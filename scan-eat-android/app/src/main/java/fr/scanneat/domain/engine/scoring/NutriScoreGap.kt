package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Grade
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.ScoreAudit

/**
 * Roadmap S11: when Scan'eat's grade and the pack's Nutri-Score disagree by two
 * letters or more, say why from the audit itself. The Nutri-Score (Santé
 * publique France) rates the nutrient profile per 100 g only; Scan'eat adds the
 * processing level, additives and ingredient quality, and uses category
 * thresholds. Null when the two agree or there is no Nutri-Score.
 */
data class NutriScoreGap(val scanEatWorse: Boolean, val reasons: List<String>)

private fun nutriRank(letter: String): Int? = when (letter.trim().lowercase()) {
    "a" -> 0; "b" -> 1; "c" -> 2; "d" -> 3; "e" -> 4; else -> null
}

private fun scanEatRank(g: Grade): Int = when (g) {
    Grade.A_PLUS, Grade.A -> 0; Grade.B -> 1; Grade.C -> 2; Grade.D -> 3; Grade.E, Grade.F -> 4
}

fun nutriScoreGap(audit: ScoreAudit, lang: String): NutriScoreGap? {
    val ns = audit.nutriscoreGrade?.let(::nutriRank) ?: return null
    val diff = scanEatRank(audit.grade) - ns
    if (kotlin.math.abs(diff) < 2) return null
    val en = lang == "en"
    val p = audit.pillars
    fun lost(s: fr.scanneat.domain.model.PillarScore) = s.max - s.score
    val reasons = mutableListOf<String>()
    if (diff > 0) {
        if (audit.veto.triggered) reasons += (if (en) "A cap was applied: " else "Un plafond a été appliqué : ") + audit.veto.reason
        if (lost(p.processing) >= 5) reasons += if (en) "Processing level (NOVA) — not part of the Nutri-Score" else "Niveau de transformation (NOVA) — non pris en compte par le Nutri-Score"
        if (lost(p.additiveRisk) >= 5) reasons += if (en) "Additives — not part of the Nutri-Score" else "Additifs — non pris en compte par le Nutri-Score"
        audit.globalPenalties.filter { it.points < 0 }.forEach { reasons += it.reason }
        if (lost(p.ingredientIntegrity) >= 6) reasons += if (en) "Ingredient quality (generic oils, several sugar sources...) — not part of the Nutri-Score" else "Qualité des ingrédients (huiles non précisées, sucres multiples…) — non prise en compte par le Nutri-Score"
    } else {
        if (lost(p.processing) < 3 && lost(p.additiveRisk) < 3) reasons += if (en) "Little processed and no risky additive — Scan'eat rewards this, the Nutri-Score doesn't look at it" else "Peu transformé et sans additif à risque — Scan'eat le valorise, le Nutri-Score ne le regarde pas"
        if (audit.category != ProductCategory.OTHER) reasons += if (en) "Fat, sugar and salt judged against this food category's own thresholds, not one scale for all foods" else "Gras, sucre et sel jugés selon les seuils de cette catégorie d'aliments, pas sur une échelle commune à tous"
    }
    if (reasons.isEmpty()) reasons += if (en) "Several small differences across the five pillars add up" else "Plusieurs petits écarts sur les cinq piliers s'additionnent"
    return NutriScoreGap(scanEatWorse = diff > 0, reasons = reasons)
}
