package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

class PersonalWarningTest {

    private val nutrition = NutritionPer100g(
        energyKcal = 100.0, fatG = 1.0, saturatedFatG = 0.5, carbsG = 10.0,
        sugarsG = 1.0, fiberG = 1.0, proteinG = 3.0, saltG = 0.1,
    )

    private fun product(vararg ingredients: String, traces: List<String> = emptyList()) = Product(
        name = "Test",
        category = ProductCategory.OTHER,
        novaClass = NovaClass.PROCESSED,
        ingredients = ingredients.map { Ingredient(name = it, category = IngredientCategory.FOOD) },
        nutrition = nutrition,
        declaredTracesTags = traces,
    )

    @Test
    fun `returns null when nothing conflicts`() {
        assertNull(personalWarning(product("tomate", "sel"), Profile(allergens = setOf("peanuts")), "en"))
    }

    @Test
    fun `lists every confirmed allergen not just the first`() {
        val w = personalWarning(product("farine de blé", "lait", "oeuf"), Profile(allergens = setOf("gluten", "lactose", "eggs")), "en")!!
        assertTrue(w, w.startsWith("Allergen: "))
        assertTrue(w, "Gluten" in w && "Eggs" in w)
    }

    @Test
    fun `trace-only hit is worded as may contain not as a confirmed allergen`() {
        val w = personalWarning(product("tomate", traces = listOf("en:peanuts")), Profile(allergens = setOf("peanuts")), "en")!!
        assertTrue(w, w.startsWith("May contain: "))
        assertFalse(w, w.startsWith("Allergen"))
    }

    @Test
    fun `includes diet reason and is language aware`() {
        val fr = personalWarning(product("lait"), Profile(diet = DietKey.VEGAN), "fr")!!
        assertTrue(fr, fr.startsWith("Non "))
        val en = personalWarning(product("milk"), Profile(diet = DietKey.VEGAN), "en")!!
        assertTrue(en, en.startsWith("Not "))
    }
}
