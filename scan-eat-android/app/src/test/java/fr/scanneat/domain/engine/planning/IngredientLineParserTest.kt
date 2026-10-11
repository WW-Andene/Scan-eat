package fr.scanneat.domain.engine.planning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientLineParserTest {

    private fun check(line: String, food: String, grams: Double, estimated: Boolean = false) {
        val p = parseIngredientLine(line)
        assertEquals("food for '$line'", food, p.food?.name)
        assertEquals("grams for '$line'", grams, p.grams ?: -1.0, 0.01)
        assertEquals("estimated for '$line'", estimated, p.gramsEstimated)
    }

    @Test fun `metric weights`() {
        check("250 g de farine", "farine de blé", 250.0)
        check("1 kg de pommes de terre", "pomme de terre", 1000.0)
        check("200g beurre", "beurre", 200.0)
    }

    @Test fun `volumes`() {
        check("50 cl de lait entier", "lait entier", 500.0)
        check("20 cl de crème liquide", "crème liquide", 200.0)
    }

    @Test fun `kitchen measures`() {
        check("2 c. à soupe d'huile d'olive", "huile d'olive", 30.0)
        check("1 c. à café de sel", "sel", 5.0)
        check("une pincée de sel", "sel", 0.5)
        check("1 sachet de levure chimique", "levure chimique", 10.0)
    }

    @Test fun `counted items use the typical piece weight`() {
        check("3 œufs", "oeuf", 165.0, estimated = true)
        check("2 tomates", "tomate", 2 * (parseIngredientLine("1 tomate").grams ?: 0.0), estimated = true)
    }

    @Test fun `fractions and decimals`() {
        assertEquals(0.5, (parseIngredientLine("1/2 citron").grams ?: 0.0) / (parseIngredientLine("1 citron").grams ?: 1.0), 0.001)
        check("1,5 kg de farine", "farine de blé", 1500.0)
        check("½ c. à café de cannelle", "cannelle", 2.5)
    }

    @Test fun `English lines`() {
        check("2 tbsp olive oil", "huile d'olive", 30.0)
        check("100 g flour", "farine de blé", 100.0)
    }

    @Test fun `descriptive words and brackets don't block the match`() {
        check("150 g de gruyère râpé (ou comté)", "emmental", 150.0)
        check("1 oignon émincé", "oignon", parseIngredientLine("1 oignon").grams ?: -1.0, estimated = true)
    }

    @Test fun `unknown food keeps the line but no component`() {
        val p = parseIngredientLine("2 c. à soupe de sauce xyzzy")
        assertNull(p.food)
        assertNull(p.toRecipeComponent())
    }

    @Test fun `component carries scaled nutrition`() {
        val c = parseIngredientLine("100 g de farine").toRecipeComponent()!!
        assertEquals(352.0, c.kcal, 0.5)
        assertTrue(c.carbsG > 70)
        assertFalse(c.productName.isBlank())
    }
}

class FindFoodByNameTest {
    @Test
    fun `English image labels map to built-in foods`() {
        val expected = mapOf("Banana" to "banane", "Tomato" to "tomate", "Carrot" to "carotte", "Egg" to "oeuf", "Butter" to "beurre")
        val wrong = expected.filter { (label, food) -> findFoodByName(label)?.name != food }
            .map { (label, _) -> "$label -> ${findFoodByName(label)?.name}" }
        assertEquals(emptyList<String>(), wrong)
    }
}
