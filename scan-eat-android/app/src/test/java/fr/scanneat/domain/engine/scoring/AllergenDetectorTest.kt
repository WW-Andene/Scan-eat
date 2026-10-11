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

    // ---- Regression lock: coverage gaps found in the allergen-pattern audit ----

    private fun keys(vararg names: String) = detectAllergens(productWithIngredients(*names)).map { it.key }.toSet()

    @Test
    fun `noix de saint-jacques is a mollusc not a tree nut`() {
        val k = keys("noix de Saint-Jacques")
        assertTrue("molluscs" in k)
        assertFalse("nuts" in k)
    }

    @Test
    fun `noix de veau and noix de jambon are not tree nuts`() {
        assertFalse("nuts" in keys("noix de veau", "noix de jambon"))
    }

    @Test
    fun `plain noix and noix de cajou still match tree nuts`() {
        assertTrue("nuts" in keys("noix"))
        assertTrue("nuts" in keys("noix de cajou"))
    }

    @Test
    fun `bonito and dashi are detected as fish`() {
        assertTrue("fish" in keys("dashi"))
        assertTrue("fish" in keys("katsuobushi"))
    }

    @Test
    fun `gambas and krill are crustaceans`() {
        assertTrue("crustaceans" in keys("gambas"))
        assertTrue("crustaceans" in keys("krill"))
    }

    @Test
    fun `escargots and coquillages are molluscs`() {
        assertTrue("molluscs" in keys("escargots"))
        assertTrue("molluscs" in keys("coquillages"))
    }

    @Test
    fun `camembert listed alone is detected as milk`() {
        assertTrue("lactose" in keys("camembert"))
    }

    @Test
    fun `gomasio is detected as sesame`() {
        assertTrue("sesame" in keys("gomasio"))
    }

    @Test
    fun `calamari spellings are molluscs`() {
        assertTrue("molluscs" in keys("anneaux de calamars"))
        assertTrue("molluscs" in keys("calamari"))
        assertTrue("molluscs" in keys("calmars"))
        assertTrue("molluscs" in keys("seiche"))
    }

    @Test
    fun `noix de boeuf is a beef cut not a tree nut`() {
        assertFalse("nuts" in keys("noix de bœuf"))
        assertFalse("nuts" in keys("noix de boeuf"))
        assertTrue("nuts" in keys("noix de cajou"))
    }
}
