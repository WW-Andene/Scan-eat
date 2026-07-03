package app.scaneat

import kotlin.math.roundToInt

object ScoringEngine {
    private val riskyAdditives = setOf("E102", "E110", "E122", "E124", "E129", "E250", "E251", "E252", "E320", "E321")
    private val sugarWords = Regex("sucre|sirop|glucose|fructose|dextrose|maltodextrine", RegexOption.IGNORE_CASE)
    private val wholeFoodWords = Regex("tomate|carotte|pomme|lentille|haricot|pois|riz|avoine|oeuf|œuf|poisson|poulet|lait|yaourt|eau", RegexOption.IGNORE_CASE)

    fun score(product: ProductInput): ScoreAudit {
        val processing = (24 - product.nova_class.coerceIn(1, 4) * 5).coerceIn(0, 20)
        val density = densityScore(product.nutrition)
        val negatives = negativeScore(product.nutrition)
        val additives = additiveScore(product.ingredients)
        val integrity = integrityScore(product)
        val pillars = linkedMapOf(
            "processing" to processing,
            "density" to density,
            "negative_nutrients" to negatives,
            "additives" to additives,
            "ingredient_integrity" to integrity,
        )
        val positives = buildList {
            if ((product.nutrition.fiber_g ?: 0.0) >= 6.0) add("Riche en fibres")
            if ((product.nutrition.protein_g ?: 0.0) >= 12.0) add("Bonne teneur en protéines")
            if (product.organic) add("Bio")
            if (product.ingredients.any { wholeFoodWords.containsMatchIn(it.name) }) add("Ingrédients simples détectés")
        }
        val warnings = buildList {
            if ((product.nutrition.sugars_g ?: 0.0) > 15.0) add("Sucres élevés")
            if ((product.nutrition.saturated_fat_g ?: 0.0) > 5.0) add("Graisses saturées élevées")
            if ((product.nutrition.salt_g ?: 0.0) > 1.5) add("Sel élevé")
            if (product.ingredients.any { sugarWords.containsMatchIn(it.name) }) add("Sucre ou sirop dans la liste d'ingrédients")
            if (product.ingredients.any { it.e_number?.uppercase() in riskyAdditives }) add("Additif à surveiller")
        }
        val total = pillars.values.sum().coerceIn(0, 100)
        return ScoreAudit(total, grade(total), pillars, positives, warnings)
    }

    private fun densityScore(n: NutritionPer100g): Int {
        var score = 12
        score += when {
            (n.fiber_g ?: 0.0) >= 6.0 -> 7
            (n.fiber_g ?: 0.0) >= 3.0 -> 4
            else -> 0
        }
        score += when {
            (n.protein_g ?: 0.0) >= 12.0 -> 6
            (n.protein_g ?: 0.0) >= 6.0 -> 3
            else -> 0
        }
        return score.coerceIn(0, 25)
    }

    private fun negativeScore(n: NutritionPer100g): Int {
        var score = 25
        score -= penalty(n.sugars_g, 5.0, 15.0, 8)
        score -= penalty(n.saturated_fat_g, 1.5, 5.0, 7)
        score -= penalty(n.salt_g, 0.3, 1.5, 7)
        return score.coerceIn(0, 25)
    }

    private fun additiveScore(ingredients: List<Ingredient>): Int {
        val additives = ingredients.count { it.e_number != null || it.category == "additive" }
        val risky = ingredients.count { it.e_number?.uppercase() in riskyAdditives }
        return (15 - additives * 2 - risky * 4).coerceIn(0, 15)
    }

    private fun integrityScore(product: ProductInput): Int {
        var score = 15
        if (product.ingredients.size > 12) score -= 4
        if (product.ingredients.firstOrNull()?.name?.let { sugarWords.containsMatchIn(it) } == true) score -= 4
        if (product.ingredients.isEmpty()) score -= 3
        if (product.organic) score += 1
        return score.coerceIn(0, 15)
    }

    private fun penalty(value: Double?, green: Double, red: Double, max: Int): Int = when {
        value == null || value <= green -> 0
        value >= red -> max
        else -> (((value - green) / (red - green)) * max).roundToInt()
    }

    private fun grade(score: Int): String = when {
        score >= 85 -> "A+"
        score >= 70 -> "A"
        score >= 55 -> "B"
        score >= 40 -> "C"
        score >= 25 -> "D"
        else -> "F"
    }
}
