package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.*
import org.junit.Test

/** "sans gluten" is a claim of absence, not a gluten source. */
class GlutenFreeClaimTest {
    private fun gluten(vararg ing: String) = detectAllergens(
        Product(name = "Test", category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
            ingredients = ing.map { Ingredient(name = it, category = IngredientCategory.FOOD) },
            nutrition = fr.scanneat.domain.model.NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0)), "fr",
    ).any { it.key == "gluten" && !it.isTraceOnly }

    @Test fun `gluten-free rice flour is not gluten`() = assertFalse(gluten("farine de riz sans gluten"))
    @Test fun `certified gluten-free oats are not gluten`() = assertFalse(gluten("flocons d'avoine sans gluten"))
    @Test fun `plain oats still flagged`() = assertTrue(gluten("flocons d'avoine"))
    @Test fun `gluten-free oats with wheat gluten still flagged`() = assertTrue(gluten("avoine sans gluten, gluten de blé"))
    @Test fun `wheat flour flagged`() = assertTrue(gluten("farine de blé"))
}
