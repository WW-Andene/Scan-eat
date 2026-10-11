package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import org.junit.Assert.assertTrue
import org.junit.Test

// Users type the brand printed on the box, and French labels use plurals.
class FoodDrugBrandPluralTest {
    private fun cautions(food: String, med: String) =
        checkFoodDrugInteractions(food, listOf(Ingredient(name = food, category = IngredientCategory.FOOD)), setOf(med), "fr")

    @Test
    fun `French brand names and plural food labels trigger cautions`() {
        assertTrue(cautions("Épinards hachés", "Previscan 20 mg").isNotEmpty())
        assertTrue(cautions("Jus de pamplemousses", "Tahor 10").isNotEmpty())
        assertTrue(cautions("Choux de Bruxelles", "fluindione").isNotEmpty())
        assertTrue(cautions("substitut de sel", "Coversyl").isNotEmpty())
    }
}
