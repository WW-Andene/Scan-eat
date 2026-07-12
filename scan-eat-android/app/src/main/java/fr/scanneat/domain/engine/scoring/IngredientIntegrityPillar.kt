package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 9: PILLAR 5 — INGREDIENT INTEGRITY (max 15)
// ============================================================================

// Word-boundary match rather than raw `.contains` - plain substring search lets
// e.g. "riz" match inside "chorizo" or "eau" match inside "gâteau"/"veau",
// wrongly counting processed ingredients as whole foods.
private fun containsWord(text: String, word: String): Boolean =
    Regex("(?<![a-zà-ÿ0-9])${Regex.escape(word)}(?![a-zà-ÿ0-9])", RegexOption.IGNORE_CASE).containsMatchIn(text)

private fun isWholeFood(ingredient: Ingredient): Boolean {
    if (ingredient.isWholeFood == true) return true
    val lower = ingredient.name.lowercase()
    if (Regex("""sirop|huile|farine raffinée|amidon modifié|isolat|concentré""", RegexOption.IGNORE_CASE).containsMatchIn(lower)) return false
    return WHOLE_FOOD_KEYWORDS.any { containsWord(lower, it) }
}

fun scoreIngredientIntegrity(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 15
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    var score = 0.0

    // 1. First 3 whole foods (+5) — divide by the actual count taken (up to 3),
    // not a fixed 3: a single-ingredient whole food (e.g. "Pommes") is 100%
    // whole-food and deserves the full 5, not 1/3 of it just for having a
    // short ingredient list.
    val first3 = product.ingredients.take(3)
    val first3Whole = first3.count { isWholeFood(it) }
    val first3Score = if (first3.isEmpty()) 0.0
        else ((first3Whole.toDouble() / first3.size) * 5.0).let { kotlin.math.round(it).toDouble() }
    score += first3Score
    if (first3Score < 5) deductions += Deduction("ingredient_integrity", if (en) "Only $first3Whole/3 first ingredients are whole foods (${first3Score.toInt()}/5)" else "Seulement $first3Whole/3 premiers ingrédients sont des aliments bruts (${first3Score.toInt()}/5)", first3Score - 5, Severity.MODERATE)
    else bonuses += Deduction("ingredient_integrity", if (en) "First 3 ingredients are all whole foods" else "Les 3 premiers ingrédients sont des aliments bruts", 5.0, Severity.INFO)

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
    if (recogScore < 3) deductions += Deduction("ingredient_integrity", if (en) "${(recogRatio * 100).toInt()}% recognizable ingredients (${recogScore.toInt()}/3)" else "${(recogRatio * 100).toInt()}% d'ingrédients reconnaissables (${recogScore.toInt()}/3)", recogScore - 3, Severity.MINOR)

    // 3. Origin transparency (+2)
    if (product.originTransparent || product.origin != null) {
        score += 2
        bonuses += Deduction("ingredient_integrity", (if (en) "Origin declared: " else "Origine déclarée : ") + (product.origin ?: (if (en) "transparent" else "transparente")), 2.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", if (en) "No origin information" else "Aucune information d'origine", -2.0, Severity.MINOR)
    }

    // 4. Hidden sugars (+2)
    val sugarAliases = mutableSetOf<String>()
    for (ing in product.ingredients) {
        val n = ing.name.lowercase()
        for (alias in HIDDEN_SUGAR_NAMES) { if (n.contains(alias)) sugarAliases += alias }
        if (Regex("""^sucre""", RegexOption.IGNORE_CASE).containsMatchIn(ing.name.trim())) sugarAliases += "sucre"
    }
    if (sugarAliases.size >= 2) {
        deductions += Deduction("ingredient_integrity", (if (en) "${sugarAliases.size} distinct sugar sources: " else "${sugarAliases.size} sources de sucre distinctes : ") + sugarAliases.joinToString(), -2.0, Severity.MODERATE)
    } else {
        score += 2
        bonuses += Deduction("ingredient_integrity", if (sugarAliases.size == 1) (if (en) "Single transparent sugar source" else "Source de sucre unique et transparente") else (if (en) "No hidden sugars" else "Aucun sucre caché"), 2.0, Severity.INFO)
    }

    // 5. Named oils (+3)
    val hasGenericOil = product.ingredients.any { ing ->
        val n = ing.name.lowercase()
        GENERIC_OIL_TERMS.any { n.contains(it) }
    }
    if (product.namedOils != false && !hasGenericOil) {
        score += 3
        bonuses += Deduction("ingredient_integrity", if (en) "Oils specifically named" else "Huiles nommées précisément", 3.0, Severity.INFO)
    } else {
        deductions += Deduction("ingredient_integrity", if (en) "Generic \"vegetable oil\" instead of specific named oil" else "Huile végétale générique au lieu d'une huile précisément nommée", -3.0, Severity.MINOR)
    }

    return PillarScore(if (en) "Ingredient Integrity" else "Intégrité des ingrédients", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}
