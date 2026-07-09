package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 8: PILLAR 4 — ADDITIVE RISK (max 15)
// ============================================================================

fun scoreAdditiveRisk(product: Product): PillarScore {
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    data class Hit(val ingredient: String, val additive: String, val concern: String)

    val tier1 = mutableListOf<Hit>()
    val tier2 = mutableListOf<Hit>()
    val tier3 = mutableListOf<Hit>()

    for (ing in product.ingredients) {
        val additive = findAdditive(ing.eNumber, ing.name, ing.category) ?: continue
        val hit = Hit(ing.name, additive.eNumber, additive.concern)
        when (additive.tier) {
            AdditiveTier.ONE   -> tier1 += hit
            AdditiveTier.TWO   -> tier2 += hit
            AdditiveTier.THREE -> tier3 += hit
        }
    }

    var score = MAX.toDouble()

    if (tier1.isNotEmpty()) {
        val penalty = minOf(10.0, tier1.size * 5.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier1.size} Tier-1 additive(s) (serious concern)", -penalty, Severity.CRITICAL,
            tier1.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier2.isNotEmpty()) {
        val penalty = minOf(6.0, tier2.size * 2.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier2.size} Tier-2 additive(s) (moderate concern)", -penalty, Severity.MODERATE,
            tier2.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier3.isNotEmpty()) {
        val penalty = minOf(3.0, tier3.size * 1.0)
        score -= penalty
        deductions += Deduction("additive_risk", "${tier3.size} Tier-3 additive(s) (minor concern)", -penalty, Severity.MINOR,
            tier3.joinToString(" | ") { "${it.additive} (${it.ingredient})" })
    }

    return PillarScore("Additive Risk", MAX, maxOf(0.0, score), deductions, bonuses)
}

fun countTier1Additives(product: Product): Int =
    product.ingredients.mapNotNull { findAdditive(it.eNumber, it.name, it.category) }.count { it.tier == AdditiveTier.ONE }
