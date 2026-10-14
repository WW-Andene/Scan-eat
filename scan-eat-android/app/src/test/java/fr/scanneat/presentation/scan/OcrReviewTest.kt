package fr.scanneat.presentation.scan

import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.scan.components.applyOcrReview
import fr.scanneat.presentation.scan.components.parseReviewNumber
import org.junit.Assert.*
import org.junit.Test

/** Roadmap C16: user corrections to OCR-read values. */
class OcrReviewTest {
    private val product = Product(
        name = "Biscuit", category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
        ingredients = listOf(Ingredient("farine de blé"), Ingredient("sucre")),
        nutrition = NutritionPer100g(energyKcal = 450.0, fatG = 15.0, saturatedFatG = 5.0, carbsG = 70.0,
            sugarsG = 30.0, fiberG = 2.0, proteinG = 6.0, saltG = 15.0),
    )

    @Test fun `comma decimal accepted, invalid rejected`() {
        assertEquals(1.5, parseReviewNumber("1,5")!!, 0.0)
        assertNull(parseReviewNumber("abc"))
        assertNull(parseReviewNumber("-2"))
    }

    @Test fun `corrected salt applied, unchanged ingredients kept as is`() {
        val r = applyOcrReview(product, "Biscuit", mapOf("salt" to 1.5), "farine de blé, sucre", "farine de blé, sucre")
        assertEquals(1.5, r.nutrition.saltG, 0.0)
        assertEquals(450.0, r.nutrition.energyKcal, 0.0)
        assertSame(product.ingredients, r.ingredients)
    }

    @Test fun `edited ingredients are re-parsed`() {
        val r = applyOcrReview(product, "", emptyMap(), "farine de blé, sucre, arachides", "farine de blé, sucre")
        assertEquals("Biscuit", r.name)
        assertTrue(r.ingredients.any { it.name.contains("arachide") })
    }
}
