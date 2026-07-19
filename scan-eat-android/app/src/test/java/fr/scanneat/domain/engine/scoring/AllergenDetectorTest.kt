package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.IngredientCategory
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// ALLERGEN DETECTOR UNIT TESTS
// No prior test coverage existed for this file. Added to lock in two audit
// fixes: the gluten rule missing the literal word "gluten" (false negative
// for coeliac users — the dangerous direction of error), and the "noix"
// (nuts) pattern over-matching "noix de coco"/"noix de muscade", which
// aren't tree nuts under EU Annex II.
// ============================================================================

class AllergenDetectorTest {

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

    // ---- Regression lock: gluten literal-word gap ----

    @Test
    fun `gluten allergen matches the literal word gluten`() {
        val hits = detectAllergens(productWithIngredients("gluten"))
        assertTrue(hits.any { it.key == "gluten" })
    }

    @Test
    fun `gluten allergen matches gluten de ble`() {
        val hits = detectAllergens(productWithIngredients("gluten de blé"))
        assertTrue(hits.any { it.key == "gluten" })
    }

    @Test
    fun `gluten allergen matches couscous and boulgour`() {
        assertTrue(detectAllergens(productWithIngredients("couscous")).any { it.key == "gluten" })
        assertTrue(detectAllergens(productWithIngredients("boulgour")).any { it.key == "gluten" })
    }

    @Test
    fun `gluten allergen still matches wheat flour`() {
        val hits = detectAllergens(productWithIngredients("farine de blé"))
        assertTrue(hits.any { it.key == "gluten" })
    }

    // ---- Regression lock: noix over-matching coconut/nutmeg ----

    @Test
    fun `tree nut allergen does not flag coconut`() {
        val hits = detectAllergens(productWithIngredients("noix de coco"))
        assertFalse(hits.any { it.key == "nuts" })
    }

    @Test
    fun `tree nut allergen does not flag nutmeg`() {
        val hits = detectAllergens(productWithIngredients("noix de muscade"))
        assertFalse(hits.any { it.key == "nuts" })
    }

    @Test
    fun `tree nut allergen still flags cashew and plain walnut`() {
        assertTrue(detectAllergens(productWithIngredients("noix de cajou")).any { it.key == "nuts" })
        assertTrue(detectAllergens(productWithIngredients("noix")).any { it.key == "nuts" })
        assertTrue(detectAllergens(productWithIngredients("noix du brésil")).any { it.key == "nuts" })
    }

    // ---- Regression lock: accented uppercase text (RegexOption.IGNORE_CASE
    // alone doesn't Unicode-fold accents, only ASCII a-z/A-Z) ----

    @Test
    fun `gluten allergen matches all-caps accented FARINE DE BLE`() {
        // A very common way OFF/OCR label text is stored - all-caps with the
        // accent preserved. Pattern.CASE_INSENSITIVE without UNICODE_CASE
        // does not match "É" against a lowercase-only [eé] character class.
        val hits = detectAllergens(productWithIngredients("FARINE DE BLÉ"))
        assertTrue(hits.any { it.key == "gluten" })
    }

    @Test
    fun `lactose allergen matches all-caps accented CREME FRAICHE`() {
        val hits = detectAllergens(productWithIngredients("CRÈME FRAÎCHE"))
        assertTrue(hits.any { it.key == "lactose" })
    }

    @Test
    fun `allergen trigger text preserves the ingredient's original casing`() {
        val hits = detectAllergens(productWithIngredients("FARINE DE BLÉ"))
        val gluten = hits.first { it.key == "gluten" }
        assertTrue(gluten.triggers.contains("FARINE DE BLÉ"))
    }

    // ---- checkUserAllergens filtering ----

    @Test
    fun `checkUserAllergens only returns declared allergens`() {
        val product = productWithIngredients("lait entier", "gluten", "noix de coco")
        val result = checkUserAllergens(product, setOf("lactose"))
        assertEquals(1, result.size)
        assertEquals("lactose", result.first().key)
    }
}
