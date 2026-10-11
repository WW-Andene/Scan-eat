package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.assertEquals
import org.junit.Test

// Plural label forms ("crevettes", "fromages", "lardons"...) were rejected by
// b()'s trailing word boundary, letting non-compliant products pass a diet;
// plant "butters" (cocoa, shea) failed vegan on every dark chocolate.
class DietPluralCoverageTest {

    private val zero = NutritionPer100g(
        energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0,
        sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0,
    )

    private fun compliant(diet: DietKey, ingredient: String) = checkDiet(
        Product(
            name = "t", category = ProductCategory.OTHER, novaClass = NovaClass.UNPROCESSED,
            ingredients = listOf(Ingredient(name = ingredient, category = IngredientCategory.FOOD)),
            nutrition = zero,
        ),
        diet,
    ).compliant

    @Test
    fun `plural animal ingredients break vegan and vegetarian`() {
        val passed = listOf("crevettes", "fromages", "saucisses", "lardons", "moules", "poissons", "maquereaux", "harengs")
            .filter { compliant(DietKey.VEGAN, it) }
        assertEquals("vegan wrongly compliant", emptyList<String>(), passed)
        val passedVeg = listOf("crevettes", "saucisses", "lardons", "poissons").filter { compliant(DietKey.VEGETARIAN, it) }
        assertEquals("vegetarian wrongly compliant", emptyList<String>(), passedVeg)
    }

    @Test
    fun `plant ingredients stay vegan`() {
        val failed = listOf("beurre de cacao", "beurre de karité", "pates", "pâtes de blé dur", "crème de tartre", "lait de coco")
            .filter { !compliant(DietKey.VEGAN, it) }
        assertEquals("vegan wrongly non-compliant", emptyList<String>(), failed)
    }
}
