package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap S13: the hint panel and the score adjustments must flag the same facts. */
class HintScoreParityTest {
    private fun drink(category: ProductCategory, sugars: Double, vararg ingredients: String) = Product(
        name = "Boisson", category = category, novaClass = NovaClass.PROCESSED,
        ingredients = ingredients.map { Ingredient(it) },
        nutrition = NutritionPer100g(energyKcal = 45.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = sugars, sugarsG = sugars,
            fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
    )
    private val depressed = Profile(healthConditions = setOf("depression"))

    @Test fun `cider without the word alcohol is flagged by both`() {
        val p = drink(ProductCategory.ALCOHOLIC_BEVERAGE, 3.0, "jus de pomme fermenté")
        assertTrue(computePersonalScore(scoreProduct(p), p, depressed).adjustments.any { "alcool" in it.reason })
        assertTrue(generateProductHints(p, depressed, "fr").conditionRisks.any { "alcool" in it })
    }

    @Test fun `10 g soda flagged by both for depression`() {
        val p = drink(ProductCategory.BEVERAGE_SOFT, 10.0, "eau gazéifiée", "sucre")
        assertTrue(computePersonalScore(scoreProduct(p), p, depressed).adjustments.any { "Sucres" in it.reason })
        assertTrue(generateProductHints(p, depressed, "fr").conditionRisks.any { "Sucres" in it })
    }
}
