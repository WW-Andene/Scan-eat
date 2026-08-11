package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 8: PILLAR 4 — ADDITIVE RISK (max 15)
// ============================================================================

// Sulfites (E220-E224) are the legally-mandated, near-universal preservative
// in wine/cider - EU law requires a "contains sulfites" label above 10mg/L
// specifically because virtually every wine has them, either added or as a
// natural fermentation byproduct. Scoring them identically to nitrites in
// cured meat (same Tier-1 bucket) penalized nearly every wine/cider scan for
// something structurally expected in the category, the same category-
// blindness already fixed for salt/sugar/kcal thresholds but never extended
// to additive risk. Excluded only for ALCOHOLIC_BEVERAGE - sulfites in any
// other category are still a real Tier-1 concern.
private val SULFITE_E_NUMBERS = setOf("E220", "E221", "E222", "E223", "E224", "E225", "E226", "E227", "E228")

// Same category-blindness pattern SULFITE_E_NUMBERS above already fixes for
// wine/cider: E290 (carbon dioxide) IS the carbonation in any "pétillante"/
// sparkling water or soda - not an added risk ingredient but the structurally
// expected reason the product is fizzy at all (its own ADDITIVES_DB entry
// literally reads "Carbonation gas / packaging gas; no concern"). Scoring it
// as a Tier-3 risk penalized every sparkling water/soda for being carbonated.
private val CARBONATED_BEVERAGE_CATEGORIES = setOf(ProductCategory.BEVERAGE_WATER, ProductCategory.BEVERAGE_SOFT)

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
    val isCarbonatedBeverage = product.category in CARBONATED_BEVERAGE_CATEGORIES
    for (ing in product.ingredients) {
        val additive = findAdditive(ing.eNumber, ing.name, ing.category) ?: continue
        if (isAlcoholic && additive.eNumber in SULFITE_E_NUMBERS) continue
        if (isCarbonatedBeverage && additive.eNumber == "E290") continue
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
        // penalty, that mismatch wasn't just cosmetic - a trace additive
        // outweighed genuinely dangerous salt levels in the cumulative-risk
        // count. One hit now reads MAJOR, matching the graduated ladder
        // every other nutrient axis in this engine uses.
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
