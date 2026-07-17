package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 6: PILLAR 2 — NUTRITIONAL DENSITY (max 25)
// ============================================================================

private val NRV_TARGETS = mapOf(
    "protein"   to Pair("proteinG",  50.0),
    "fiber"     to Pair("fiberG",    25.0),
    "iron"      to Pair("ironMg",    14.0),
    "calcium"   to Pair("calciumMg", 800.0),
    "vitD"      to Pair("vitDUg",    5.0),
    "vitC"      to Pair("vitCMg",    80.0),
    "vitA"      to Pair("vitAUg",    800.0),
    "vitE"      to Pair("vitEMg",    12.0),
    "b12"       to Pair("b12Ug",     2.5),
    "magnesium" to Pair("magnesiumMg", 375.0),
    "potassium" to Pair("potassiumMg", 2000.0),
    "zinc"      to Pair("zincMg",    10.0),
)

private fun getNutrientValue(n: NutritionPer100g, key: String): Double? = when (key) {
    "proteinG"    -> n.proteinG
    "fiberG"      -> n.fiberG
    "ironMg"      -> n.ironMg
    "calciumMg"   -> n.calciumMg
    "vitDUg"      -> n.vitDUg
    "vitCMg"      -> n.vitCMg
    "vitAUg"      -> n.vitAUg
    "vitEMg"      -> n.vitEMg
    "b12Ug"       -> n.b12Ug
    "magnesiumMg" -> n.magnesiumMg
    "potassiumMg" -> n.potassiumMg
    "zincMg"      -> n.zincMg
    else          -> null
}

fun scoreNutritionalDensity(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 25
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    val n = product.nutrition
    val thresholds = getThresholds(product.category)
    var score = 0.0

    // Protein (0–7)
    val (pLow, pMed, pHigh) = thresholds.proteinG
    val protScore = when {
        n.proteinG >= pHigh -> 7.0
        n.proteinG >= pMed  -> 5.0
        n.proteinG >= pLow  -> 3.0
        else                -> 0.0
    }
    score += protScore
    if (protScore < 7) deductions += Deduction("nutritional_density", if (en) "Protein ${n.proteinG}g/100g (${protScore.toInt()}/7)" else "Protéines ${n.proteinG}g/100g (${protScore.toInt()}/7)", protScore - 7, Severity.MINOR)
    else if (n.proteinG > 0.0) bonuses += Deduction("nutritional_density", if (en) "High protein ${n.proteinG}g/100g" else "Riche en protéines ${n.proteinG}g/100g", 7.0, Severity.INFO)
    // else: protScore maxed only because this category's protein threshold is 0 (irrelevant, e.g. water) — not a real achievement, skip the highlight

    // Fiber (0–7)
    val (fLow, fMed, fHigh) = thresholds.fiberG
    val fiberScore = when {
        n.fiberG >= fHigh -> 7.0
        n.fiberG >= fMed  -> 5.0
        n.fiberG >= fLow  -> 3.0
        else              -> 0.0
    }
    score += fiberScore
    if (fiberScore >= 5 && n.fiberG > 0.0) bonuses += Deduction("nutritional_density", if (en) "Good fiber ${n.fiberG}g/100g" else "Bonne teneur en fibres ${n.fiberG}g/100g", fiberScore, Severity.INFO)
    else if (fiberScore < 5) deductions += Deduction("nutritional_density", if (en) "Low fiber ${n.fiberG}g/100g (${fiberScore.toInt()}/7)" else "Fibres faibles ${n.fiberG}g/100g (${fiberScore.toInt()}/7)", fiberScore - 7, Severity.MINOR)
    // else: fiberScore >= 5 only because this category's fiber threshold is 0 (irrelevant, e.g. water) — not a real achievement, skip the highlight

    // Micronutrients NRV-15% bonus (0–8): +1 per micro declared at ≥15% NRV per 100g, cap 8
    var microBonus = 0.0
    val declaredMicros = mutableListOf<String>()
    for ((name, pair) in NRV_TARGETS) {
        val (fieldKey, nrv) = pair
        val value = getNutrientValue(n, fieldKey) ?: continue
        if (value >= nrv * 0.15) {
            microBonus += 1.0
            declaredMicros += name
        }
    }
    microBonus = minOf(8.0, microBonus)
    score += microBonus
    if (microBonus > 0) bonuses += Deduction("nutritional_density", (if (en) "Micronutrient richness: " else "Richesse en micronutriments : ") + declaredMicros.joinToString(), microBonus, Severity.INFO)

    // Healthy-fat bonus (+3): omega-3 or declared omega-3 nutrition
    val hasOmega3 = (n.omega3G ?: 0.0) > 0.5 || product.ingredients.any { ing ->
        Regex("""(graine de )?lin\b|\bchia\b|\bnoix\b|saumon|sardine|maquereau|hareng|anchois""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name)
    }
    if (hasOmega3) {
        score += 3
        bonuses += Deduction("nutritional_density", if (en) "Omega-3 source present" else "Présence d'oméga-3", 3.0, Severity.INFO)
    }

    return PillarScore(if (en) "Nutritional Density" else "Densité nutritionnelle", MAX, minOf(MAX.toDouble(), maxOf(0.0, score)), deductions, bonuses)
}
