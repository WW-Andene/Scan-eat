package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/** Roadmap S8: bonuses and penalties without a sourced basis. */
class S8BonusesTest {
    private val n = NutritionPer100g(energyKcal = 120.0, fatG = 3.0, saturatedFatG = 1.0, carbsG = 15.0, sugarsG = 5.0, fiberG = 2.0, proteinG = 6.0, saltG = 0.2)
    private fun p(vararg ing: String, nutrition: NutritionPer100g = n, cat: ProductCategory = ProductCategory.OTHER, organic: Boolean = false,
                  claims: Boolean = false, origin: String? = null) =
        Product(name = "Test", category = cat, novaClass = NovaClass.PROCESSED, organic = organic, hasHealthClaims = claims, origin = origin,
            ingredients = ing.map { Ingredient(name = it, category = IngredientCategory.FOOD) }, nutrition = nutrition)

    @Test fun `organic and health claims no longer move the score`() {
        val base = scoreProduct(p("lait", "sucre"), "fr").score
        assertEquals(base, scoreProduct(p("lait", "sucre", organic = true), "fr").score)
        assertEquals(base, scoreProduct(p("lait", "sucre", claims = true), "fr").score)
    }

    @Test fun `EU and non-EU origin is not informative`() {
        assertTrue(isNonInformativeOrigin("UE et non UE"))
        assertTrue(isNonInformativeOrigin("Origine : UE/non-UE"))
        assertFalse(isNonInformativeOrigin("France"))
        assertTrue(scoreProduct(p("lait", origin = "France"), "fr").score > scoreProduct(p("lait", origin = "UE et non UE"), "fr").score)
    }

    @Test fun `oil-free products are not held to the named-oil points`() {
        assertFalse(containsAnyOil(p("lait", "ferments lactiques")))
        assertTrue(containsAnyOil(p("farine", "huile de tournesol")))
        assertTrue(containsAnyOil(p("farine", "huile végétale")))
    }

    @Test fun `fortified micronutrients earn no bonus`() {
        val rich = n.copy(ironMg = 8.0, vitDUg = 2.0)
        assertEquals(setOf("ironMg", "vitDUg"), fortifiedMicros(p("céréales", "fumarate ferreux", "vitamine D")))
        val natural = scoreNutritionalDensity(p("lentilles", nutrition = rich), "fr").score
        val fortified = scoreNutritionalDensity(p("céréales", "fumarate ferreux", "vitamine D", nutrition = rich), "fr").score
        assertTrue(natural > fortified)
    }

    @Test fun `beverages count at 7,5 percent of NRV per 100 mL`() {
        val juice = NutritionPer100g(energyKcal = 45.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 10.0, sugarsG = 9.0, fiberG = 0.0, proteinG = 0.5, saltG = 0.0, vitCMg = 8.0)
        // 8 mg = 10 % of the 80 mg NRV: significant for a beverage, not for a solid food.
        assertTrue(scoreNutritionalDensity(p("jus d'orange", nutrition = juice, cat = ProductCategory.BEVERAGE_JUICE), "fr").bonuses.any { "vitC" in it.reason })
        assertFalse(scoreNutritionalDensity(p("orange", nutrition = juice), "fr").bonuses.any { "vitC" in it.reason })
    }
}
