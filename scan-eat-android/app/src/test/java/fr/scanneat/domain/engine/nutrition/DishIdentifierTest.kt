package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.detectAllergens
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.ProductCategory
import org.junit.Assert.*
import org.junit.Test

class DishIdentifierTest {
    @Test fun `known dish by its usual names`() {
        listOf("Pâtes à la bolognaise", "spaghetti bolognese", "Spaghettis bolognaise").forEach {
            val m = identifyDish(it)
            assertTrue(it, m is DishMatch.Exact && m.food.name == "spaghetti bolognaise")
        }
    }

    @Test fun `unknown combination is composed from its parts`() {
        val m = identifyDish("Tagliatelles au pesto") as DishMatch.Composed
        assertEquals(listOf("pâtes cuites", "sauce pesto"), m.parts.map { it.first.name })
        assertEquals(1.0, m.parts.sumOf { it.second }, 1e-9)
        // pasta 0.6 / sauce 0.3 -> 2/3 + 1/3
        assertEquals(140.0 * 2 / 3 + 450.0 / 3, m.food.kcal, 0.01)
        assertTrue(m.describe().contains("sauce pesto"))
    }

    @Test fun `sauce word after sauce is a sauce`() {
        val m = identifyDish("Penne sauce tomate") as DishMatch.Composed
        assertTrue(m.parts.any { it.first.name == "sauce tomate" })
    }

    @Test fun `part of the name is never presented as the whole`() {
        val m = identifyDish("pâtes au zorglub")
        assertTrue(m is DishMatch.Partial)
        assertEquals(listOf("zorglub"), (m as DishMatch.Partial).missing)
    }

    @Test fun `prepared dish is scored as a cooked preparation with its ingredients`() {
        val p = (identifyDish("spaghetti bolognaise") as DishMatch.Exact).food.toProduct()
        assertEquals(ProductCategory.READY_MEAL, p.category)
        assertEquals(NovaClass.PROCESSED, p.novaClass)
        assertFalse(p.novaDeclared)
        assertTrue(detectAllergens(p, "fr").any { it.key == "gluten" })
    }

    @Test fun `nothing for gibberish`() = assertNull(identifyDish("xqzt wvb"))
}
