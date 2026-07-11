package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.Ingredient
import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// DIET CHECKER UNIT TESTS
// No prior test coverage existed for this file before app-audit §O1/L4 -
// added specifically to lock in the §A1/L3 fix (dairy-free/paleo false-
// flagging coconut/peanut butter) so it can't silently regress.
// ============================================================================

class DietCheckerTest {

    private val zeroNutrition = NutritionPer100g(
        energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0,
        sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0,
    )

    private fun productWithIngredients(vararg names: String) = Product(
        name = "Test product",
        category = ProductCategory.OTHER,
        novaClass = NovaClass.UNPROCESSED,
        ingredients = names.map { Ingredient(name = it, category = IngredientCategory.FOOD) },
        nutrition = zeroNutrition,
    )

    // ---- Regression lock: §A1/L3 — coconut/peanut butter false positives ----

    @Test
    fun `dairy-free does not flag peanut butter`() {
        val result = checkDiet(productWithIngredients("beurre de cacahuète", "sel"), DietKey.DAIRY_FREE)
        assertTrue(result.compliant)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun `dairy-free does not flag coconut butter or coconut cream`() {
        val butter = checkDiet(productWithIngredients("beurre de coco"), DietKey.DAIRY_FREE)
        val cream  = checkDiet(productWithIngredients("crème de coco"), DietKey.DAIRY_FREE)
        assertTrue(butter.compliant)
        assertTrue(cream.compliant)
    }

    @Test
    fun `dairy-free still flags real dairy`() {
        val result = checkDiet(productWithIngredients("beurre", "lait entier"), DietKey.DAIRY_FREE)
        assertFalse(result.compliant)
        assertTrue(result.violations.isNotEmpty())
    }

    @Test
    fun `paleo does not flag coconut butter via the dairy pattern`() {
        // Coconut butter isn't dairy and isn't a legume either, so it's clean for paleo.
        val coconut = checkDiet(productWithIngredients("beurre de coco"), DietKey.PALEO)
        assertTrue(coconut.compliant)
    }

    @Test
    fun `paleo still flags peanut butter, correctly, as a legume`() {
        // Peanuts are legumes, and paleo genuinely excludes legumes via a separate
        // forbidden pattern from the dairy one §A1/L3 fixed - "beurre de cacahuète"
        // being non-compliant here is correct paleo behavior, not a regression of
        // that fix (which only ever concerned the dairy-pattern false positive).
        val result = checkDiet(productWithIngredients("beurre de cacahuète"), DietKey.PALEO)
        assertFalse(result.compliant)
    }

    @Test
    fun `paleo still flags real dairy and grains`() {
        val dairy = checkDiet(productWithIngredients("beurre", "lait"), DietKey.PALEO)
        val grain = checkDiet(productWithIngredients("farine de blé"), DietKey.PALEO)
        assertFalse(dairy.compliant)
        assertFalse(grain.compliant)
    }

    // ---- General sanity coverage (previously entirely untested) ----

    @Test
    fun `none diet is always compliant`() {
        val result = checkDiet(productWithIngredients("porc", "lait"), DietKey.NONE)
        assertTrue(result.compliant)
    }

    @Test
    fun `vegan flags meat, dairy, and eggs`() {
        assertFalse(checkDiet(productWithIngredients("poulet"), DietKey.VEGAN).compliant)
        assertFalse(checkDiet(productWithIngredients("lait entier"), DietKey.VEGAN).compliant)
        assertFalse(checkDiet(productWithIngredients("blanc d'oeuf"), DietKey.VEGAN).compliant)
    }

    @Test
    fun `vegan does not flag plant milk`() {
        val result = checkDiet(productWithIngredients("lait de soja", "sucre"), DietKey.VEGAN)
        assertTrue(result.compliant)
    }

    @Test
    fun `halal flags pork and alcohol`() {
        assertFalse(checkDiet(productWithIngredients("lard"), DietKey.HALAL).compliant)
        assertFalse(checkDiet(productWithIngredients("vin"), DietKey.HALAL).compliant)
    }

    @Test
    fun `halal certification overrides a detected violation`() {
        val result = checkDiet(productWithIngredients("gélatine", "certifié halal"), DietKey.HALAL)
        assertTrue(result.certified)
        assertTrue(result.compliant)
    }

    @Test
    fun `keto flags high net-carb products`() {
        val highCarb = Product(
            name = "Test", category = ProductCategory.OTHER, novaClass = NovaClass.UNPROCESSED,
            ingredients = emptyList(),
            nutrition = zeroNutrition.copy(carbsG = 50.0, fiberG = 2.0, energyKcal = 300.0, fatG = 2.0),
        )
        val result = checkDiet(highCarb, DietKey.KETO)
        assertFalse(result.compliant)
    }
}
