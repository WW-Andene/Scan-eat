package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 9: PILLAR 5 — INGREDIENT INTEGRITY (max 15)
// ============================================================================

private fun isWholeFood(ingredient: Ingredient): Boolean {
    if (ingredient.isWholeFood == true) return true
    val lower = ingredient.name.lowercase()
    if (Regex("""sirop|huile|farine raffinée|amidon modifié|isolat|concentré""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) return false
    return WHOLE_FOOD_KEYWORDS.any { lower.contains(it) }
}

fun scoreIngredientIntegrity(product: Product): PillarScore {
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    var score = 0.0

    // 1. First 3 whole foods (+5)
    val first3 = product.ingredients.take(3)
    val first3Whole = first3.count { isWholeFood(it) }
    val first3Score = ((first3Whole.toDouble() / 3.0) * 5.0).let { kotlin.math.round(it).toDouble() }
    score += first3Score
    if (first3Score < 5) deductions += Deduction("ingredient_integrity", "Only $first3Whole/3 first ingredients are whole foods (${first3Score.toInt()}/5)", first3Score - 5, Severity.MODERATE)
    else bonuses += Deduction("ingredient_integrity", "First 3 ingredients are all whole foods", 5.0, Severity.INFO)

    // 2. Overall recognizability (+3)
    val nonAdditive = product.ingredients.filter { findAdditive(it.eNumber, it.name, it.category) == null }
    val recognizable = nonAdditive.count { ing ->
        val n = ing.name.lowercase()
        n.length < 40 && !Regex("""isolat|hydrolysat|concentré|modifié|extrait sec""", RegexOption.IGNORE_CASE).containsMatchIn(n)
    }
    val recogRatio = if (nonAdditive.isNotEmpty()) recognizable.toDouble() / nonAdditive.size else 1.0
    val recogScore = when {
        recogRatio >= 0.8 -> 3.0
        recogRatio >= 0.6 -> 2.0
        recogRatio >= 0.4 -> 1.0
        else              -> 0.0
    }
    score += recogScore
    if (recogScore < 3) deductions += Deduction("ingredient_integrity", "${(recogRatio * 100).toInt()}% recognizable ingredients (${recogScore.toInt()}/3)", recogScore - 3, Severity.MINOR)

    // 3. Origin transparency (+2)
    if (product.originTransparent || product.origin != null) {
        score += 2
        bonuses += Deduction("ingredient_integrity", "Origin declared: ${product.origin ?: "transparent"}", 2.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", "No origin information", -2.0, Severity.MINOR)
    }

    // 4. Hidden sugars (+2)
    val sugarAliases = mutableSetOf<String>()
    for (ing in product.ingredients) {
        val n = ing.name.lowercase()
        for (alias in HIDDEN_SUGAR_NAMES) { if (n.contains(alias)) sugarAliases += alias }
        if (Regex("""^sucre""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name.trim())) sugarAliases += "sucre"
    }
    if (sugarAliases.size >= 2) {
        deductions += Deduction("ingredient_integrity", "${sugarAliases.size} distinct sugar sources: ${sugarAliases.joinToString()}", -2.0, Severity.MODERATE)
    } else {
        score += 2
        bonuses += Deduction("ingredient_integrity", if (sugarAliases.size == 1) "Single transparent sugar source" else "No hidden sugars", 2.0, Severity.INFO)
    }

    // 5. Named oils (+3)
    val hasGenericOil = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        GENERIC_OIL_TERMS.any { n.contains(it) }
    }
    if (product.namedOils != false && !hasGenericOil) {
        score += 3
        bonuses += Deduction("ingredient_integrity", "Oils specifically named", 3.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", "Generic \"vegetable oil\" instead of specific named oil", -3.0, Severity.MINOR)
    }

    return PillarScore("Ingredient Integrity", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}
