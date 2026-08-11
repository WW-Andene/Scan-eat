package fr.scanneat.shared

// ============================================================================
// SECTION 8: PILLAR 4 — ADDITIVE RISK (max 15)
//
// Split out of ScoringEngine.kt - see CategoryThresholds.kt's header comment
// for why. Purely structural move, no behavior change.
// ============================================================================

private val SULFITE_E_NUMBERS = setOf("E220", "E221", "E222", "E223", "E224", "E225", "E226", "E227", "E228")

fun scoreAdditiveRisk(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()

    data class Hit(val ingredient: String, val additive: String, val concern: String)

    val tier1 = mutableListOf<Hit>()
    val tier2 = mutableListOf<Hit>()
    val tier3 = mutableListOf<Hit>()

    val isAlcoholic = product.category == ProductCategory.ALCOHOLIC_BEVERAGE
    for (ing in product.ingredients) {
        val additive = findAdditive(ing.eNumber, ing.name, ing.category) ?: continue
        if (isAlcoholic && additive.eNumber in SULFITE_E_NUMBERS) continue
        // Same category-blindness pattern SULFITE_E_NUMBERS above already
        // fixes for wine/cider, but general rather than category-scoped:
        // every PACKAGING_GAS additive (E290 carbonation, E938 argon, E941
        // nitrogen, E942 nitrous oxide propellant) is inert headspace/
        // carbonation gas, never a nutritionally consumed ingredient in any
        // category - scoring it as a food-safety risk penalized every
        // sparkling drink/vacuum-packed product for how it was packaged.
        if (additive.category == AdditiveCategory.PACKAGING_GAS) continue
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
        // CRITICAL only from 2+ hits, not a single one - a lone Tier-1
        // additive was previously labeled CRITICAL unconditionally, more
        // severe than NegativeNutrientsPillar's own worst salt tier (which
        // tops out at MAJOR, no CRITICAL band exists for salt at all). Since
        // this severity also feeds severeFlagCount's uncapped global
        // penalty, that mismatch wasn't just cosmetic. Mirrors the identical
        // fix on the Android side (see Scoring Drift Check).
        val severity = if (tier1.size >= 2) Severity.CRITICAL else Severity.MAJOR
        deductions += Deduction("additive_risk", if (en) "${tier1.size} Tier-1 additive(s) (serious concern)" else "${tier1.size} additif(s) de niveau 1 (préoccupation sérieuse)", -penalty, severity,
            tier1.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier2.isNotEmpty()) {
        val penalty = minOf(6.0, tier2.size * 2.0)
        score -= penalty
        deductions += Deduction("additive_risk", if (en) "${tier2.size} Tier-2 additive(s) (moderate concern)" else "${tier2.size} additif(s) de niveau 2 (préoccupation modérée)", -penalty, Severity.MODERATE,
            tier2.joinToString(" | ") { "${it.additive} (${it.ingredient}): ${it.concern}" })
    }
    if (tier3.isNotEmpty()) {
        val penalty = minOf(3.0, tier3.size * 1.0)
        score -= penalty
        deductions += Deduction("additive_risk", if (en) "${tier3.size} Tier-3 additive(s) (minor concern)" else "${tier3.size} additif(s) de niveau 3 (préoccupation mineure)", -penalty, Severity.MINOR,
            tier3.joinToString(" | ") { "${it.additive} (${it.ingredient})" })
    }

    return PillarScore(if (en) "Additive Risk" else "Risque additifs", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}

fun countTier1Additives(product: Product): Int {
    val isAlcoholic = product.category == ProductCategory.ALCOHOLIC_BEVERAGE
    return product.ingredients.mapNotNull { findAdditive(it.eNumber, it.name, it.category) }
        .count { it.tier == AdditiveTier.ONE && !(isAlcoholic && it.eNumber in SULFITE_E_NUMBERS) }
}
