package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import kotlin.math.roundToInt

// ===== PROTEIN PRI =====
internal fun computeProteinPriAdjustments(product: Product, profile: Profile, lang: String): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    val priTarget = proteinPriG(profile)
    if (priTarget != null && priTarget > 0) {
        val pctOfPRI = (product.nutrition.proteinG / priTarget) * 100.0
        if (pctOfPRI >= 20) {
            adjustments += PersonalAdjustment(
                points   = 2.0,
                reason   = if (lang == "en")
                    "100 g covers ${pctOfPRI.roundToInt()} % of your daily protein target (${priTarget.roundToInt()} g, EFSA PRI)"
                else "100 g couvre ${pctOfPRI.roundToInt()} % de ton besoin protéique journalier (${priTarget.roundToInt()} g, PRI EFSA)",
                category = AdjustmentCategory.PROTEIN_BUDGET,
            )
        }
    }
    return adjustments
}

// ===== DAILY TARGET CONTEXT =====
// [bioTdeeKcal], when supplied, rescales the sat-fat/sugar budgets onto
// Biolism's richer body-composition-aware TDEE the same way Dashboard/Diary/
// Widget already do via DailyTargets.withKcalOverride() - previously this was
// the one remaining consumer of dailyTargets() that never received it, so a
// user with a valid Biolism profile saw their scanned product's "100 g uses
// X% of your daily sat-fat budget" adjustment computed from the plain PAL-
// based estimate while every other screen already agreed on the richer
// number, the exact "same fact, different daily budget" split the app had
// otherwise eliminated everywhere else.
internal fun computeDailyTargetAdjustments(product: Product, profile: Profile, lang: String, bioTdeeKcal: Double? = null): List<PersonalAdjustment> {
    val adjustments = mutableListOf<PersonalAdjustment>()
    val targets = dailyTargets(profile)?.let { t -> bioTdeeKcal?.let { t.withKcalOverride(it, profile.goal) } ?: t }
    if (targets != null) {
        val satFatPct = (product.nutrition.saturatedFatG / targets.satFatGMax.coerceAtLeast(1.0)) * 100.0
        if (satFatPct >= 50) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "100 g uses ${satFatPct.roundToInt()} % of your daily sat-fat budget (${targets.satFatGMax.roundToInt()} g, WHO 2023)"
                else "100 g consomme ${satFatPct.roundToInt()} % de ton budget AGS journalier (${targets.satFatGMax.roundToInt()} g, OMS 2023)",
                category = AdjustmentCategory.PROTEIN_BUDGET,
            )
        }
        val sugars    = product.nutrition.addedSugarsG ?: product.nutrition.sugarsG
        val sugarPct  = (sugars / targets.freeSugarsGMax.coerceAtLeast(1.0)) * 100.0
        if (sugarPct >= 50) {
            adjustments += PersonalAdjustment(
                points   = -4.0,
                reason   = if (lang == "en")
                    "100 g uses ${sugarPct.roundToInt()} % of your daily free-sugar budget (${targets.freeSugarsGMax.roundToInt()} g, WHO 2015)"
                else "100 g consomme ${sugarPct.roundToInt()} % de ton budget sucres libres (${targets.freeSugarsGMax.roundToInt()} g, OMS 2015)",
                category = AdjustmentCategory.PROTEIN_BUDGET,
            )
        }
        val saltPct = (product.nutrition.saltG / targets.saltGMax) * 100.0
        if (saltPct >= 30) {
            adjustments += PersonalAdjustment(
                points   = -3.0,
                reason   = if (lang == "en")
                    "100 g uses ${saltPct.roundToInt()} % of the WHO 5 g/day salt ceiling"
                else "100 g consomme ${saltPct.roundToInt()} % du plafond OMS de 5 g/j de sel",
                category = AdjustmentCategory.PROTEIN_BUDGET,
            )
        }
    }
    return adjustments
}
