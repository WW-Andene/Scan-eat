package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

// Newer OFF records can carry only energy-kj_100g; that used to map to 0 kcal.
class OffMapperEnergyTest {
    private fun product(nutriments: Map<String, Any?>) = mapOffProduct(
        OffProductResponse(
            productName = "Test", productNameFr = null, genericNameFr = null, brands = null,
            categoriesTags = null, ingredientsTextFr = null, ingredientsText = null, novaGroup = null,
            nutriments = nutriments, labelsTags = null, origins = null, countriesTags = null,
            quantity = null, ecoscoreGrade = null, ecoscoreScore = null, nutritionGrades = null,
        ),
    )!!

    @Test
    fun `kJ-only record converts to kcal`() {
        assertEquals(200.0, product(mapOf("energy-kj_100g" to 836.8)).nutrition.energyKcal, 0.1)
    }

    @Test
    fun `kcal field still wins when present`() {
        assertEquals(150.0, product(mapOf("energy-kcal_100g" to 150.0, "energy-kj_100g" to 999.0)).nutrition.energyKcal, 0.1)
    }

    private fun organic(vararg tags: String) = mapOffProduct(
        OffProductResponse(
            productName = "Test", productNameFr = null, genericNameFr = null, brands = null,
            categoriesTags = null, ingredientsTextFr = null, ingredientsText = null, novaGroup = null,
            nutriments = null, labelsTags = tags.toList(), origins = null, countriesTags = null,
            quantity = null, ecoscoreGrade = null, ecoscoreScore = null, nutritionGrades = null,
        ),
    )!!.organic

    @Test
    fun `organic label tags are matched as whole tokens`() {
        assertEquals(true, organic("en:organic"))
        assertEquals(true, organic("en:eu-organic"))
        assertEquals(true, organic("fr:ab-agriculture-biologique"))
        assertEquals(true, organic("en:bio-suisse"))
        assertEquals(false, organic("en:antibiotic-free"))
        assertEquals(false, organic("en:no-antibiotics", "en:probiotics"))
    }
}
