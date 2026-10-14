package fr.scanneat.domain.engine.reaction

import fr.scanneat.domain.engine.scoring.detectAllergens
import fr.scanneat.domain.engine.scoring.normalizeForMatching
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory

/** Something present in several reaction products - a lead for an allergist, never a conclusion. */
data class SharedSuspect(val label: String, val reactionCount: Int, val totalReactions: Int, val isAnnexIIAllergen: Boolean)

private val EMPTY_NUTRITION = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)

/**
 * Roadmap N10: crosses the ingredient lists of the products behind logged
 * reactions. Returns the Annex II allergens (via [detectAllergens], same
 * matcher as the scan) and the plain ingredients found in at least 2
 * reactions, Annex II first, most frequent first. Ingredients present in
 * nearly every food (water, salt, sugar) are skipped: they say nothing.
 */
fun sharedReactionSuspects(reactionIngredients: List<List<String>>, lang: String = "fr"): List<SharedSuspect> {
    val total = reactionIngredients.size
    if (total < 2) return emptyList()
    val allergenCounts = mutableMapOf<String, Int>()
    val ingredientCounts = mutableMapOf<String, Pair<String, Int>>()
    reactionIngredients.forEach { names ->
        val product = Product(name = "", category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
            ingredients = names.map { Ingredient(name = it) }, nutrition = EMPTY_NUTRITION)
        detectAllergens(product, lang).filter { !it.isTraceOnly }
            .map { if (lang == "en") it.labelEn else it.labelFr }.toSet()
            .forEach { allergenCounts[it] = (allergenCounts[it] ?: 0) + 1 }
        names.map { it.trim() }.filter { it.isNotEmpty() }.associateBy { normalizeForMatching(it) }
            .filterKeys { it !in UBIQUITOUS }
            .forEach { (key, display) -> ingredientCounts[key] = display to ((ingredientCounts[key]?.second ?: 0) + 1) }
    }
    val allergens = allergenCounts.filter { it.value >= 2 }.map { SharedSuspect(it.key, it.value, total, true) }
    val ingredients = ingredientCounts.values.filter { it.second >= 2 }.map { SharedSuspect(it.first, it.second, total, false) }
    return allergens.sortedByDescending { it.reactionCount } + ingredients.sortedByDescending { it.reactionCount }
}

private val UBIQUITOUS = setOf("eau", "sel", "sucre", "water", "salt", "sugar").map(::normalizeForMatching).toSet()
