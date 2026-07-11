package fr.scanneat

import fr.scanneat.shared.*
import kotlin.test.*

// ============================================================================
// SERVER ENGINE TESTS
// Verifies the shared domain engine (copied from Android) produces the same
// results on the JVM as it does in the Android unit tests.
// ============================================================================

class ScoringEngineTest {

    private fun water() = Product(
        name = "Evian eau minérale",
        category = ProductCategory.BEVERAGE_WATER,
        novaClass = NovaClass.UNPROCESSED,
        ingredients = listOf(Ingredient("eau minérale naturelle", isWholeFood = true)),
        nutrition = NutritionPer100g(0.0, 0.0, 0.0, 0.0, 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        origin = "France", namedOils = true, originTransparent = true,
    )

    private fun cola() = Product(
        name = "Coca-Cola",
        category = ProductCategory.BEVERAGE_SOFT,
        novaClass = NovaClass.ULTRA_PROCESSED,
        ingredients = listOf(
            Ingredient("eau gazéifiée", isWholeFood = true),
            Ingredient("sucre"),
            Ingredient("colorant: caramel", eNumber = "E150", category = IngredientCategory.ADDITIVE),
            Ingredient("acide phosphorique", eNumber = "E338", category = IngredientCategory.ADDITIVE),
            Ingredient("arômes naturels", category = IngredientCategory.ADDITIVE),
        ),
        nutrition = NutritionPer100g(42.0, 0.0, 0.0, 10.6, 10.6, addedSugarsG = 10.6, fiberG = 0.0, proteinG = 0.0, saltG = 0.0),
        namedOils = true,
    )

    @Test
    fun `water scores A or A+`() {
        val audit = scoreProduct(water())
        assertTrue(audit.grade in listOf(Grade.A_PLUS, Grade.A), "Got ${audit.grade} (${audit.score})")
        assertTrue(audit.score >= 70)
    }

    @Test
    fun `cola scores D or F`() {
        val audit = scoreProduct(cola())
        assertTrue(audit.grade in listOf(Grade.D, Grade.F), "Got ${audit.grade} (${audit.score})")
        assertTrue(audit.score <= 40)
    }

    @Test
    fun `trans fat veto caps at 40`() {
        val product = Product(
            name = "Margarine industrielle",
            category = ProductCategory.OIL_FAT,
            novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(Ingredient("huiles partiellement hydrogénées")),
            nutrition = NutritionPer100g(700.0, 80.0, 30.0, 0.0, 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 1.2, transFatG = 2.5),
        )
        val audit = scoreProduct(product)
        assertTrue(audit.veto.triggered)
        assertTrue(audit.score <= 40)
    }

    @Test
    fun `engine version is 2_3_0`() {
        assertEquals("2.3.0", ENGINE_VERSION)
    }

    @Test
    fun `E250 is tier 1`() {
        val info = findAdditive("E250", "nitrite de sodium", IngredientCategory.ADDITIVE)
        assertNotNull(info)
        assertEquals(AdditiveTier.ONE, info!!.tier)
    }

}
