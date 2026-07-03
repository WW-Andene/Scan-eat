package app.scaneat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

class ScoringEngineTest {
    @Test fun `whole food style product scores better than sugary ultra processed product`() {
        val yogurt = ProductInput(
            name = "Yaourt nature",
            category = "yogurt",
            nova_class = 1,
            ingredients = listOf(Ingredient("lait"), Ingredient("ferments lactiques")),
            nutrition = NutritionPer100g(sugars_g = 4.0, saturated_fat_g = 1.0, salt_g = 0.1, protein_g = 5.0),
        )
        val candy = ProductInput(
            name = "Bonbons",
            category = "snack_sweet",
            nova_class = 4,
            ingredients = listOf(Ingredient("sucre"), Ingredient("sirop de glucose"), Ingredient("colorant E102", e_number = "E102", category = "additive")),
            nutrition = NutritionPer100g(sugars_g = 70.0, saturated_fat_g = 0.0, salt_g = 0.2),
        )
        assertTrue(ScoringEngine.score(yogurt).score > ScoringEngine.score(candy).score)
        assertTrue(ScoringEngine.score(candy).score < 55)
    }

    @Test fun `allergen detector handles French plurals and engine metadata`() {
        val cookie = ProductInput(
            name = "Cookie",
            ingredients = listOf(Ingredient("arachides"), Ingredient("œufs"), Ingredient("laits")),
            nutrition = NutritionPer100g(energy_kcal = 500.0, protein_g = 5.0, carbs_g = 60.0, fat_g = 20.0),
        )
        val audit = ScoringEngine.score(cookie)
        assertContains(audit.allergens, "arachide")
        assertContains(audit.allergens, "œuf")
        assertContains(audit.allergens, "lait")
        assertEquals(ScoringEngine.ENGINE_VERSION, audit.engineVersion)
    }

    @Test fun `personal score caps products that conflict with user diets and allergens`() {
        val sandwich = ProductInput(
            name = "Sandwich jambon fromage",
            ingredients = listOf(Ingredient("pain de blé"), Ingredient("jambon de porc"), Ingredient("fromage au lait")),
            nutrition = NutritionPer100g(energy_kcal = 250.0, protein_g = 12.0, carbs_g = 30.0, fat_g = 8.0),
        )
        val audit = ScoringEngine.score(sandwich, UserPreferences(diets = listOf("halal", "gluten_free"), allergens = listOf("lait")))
        assertTrue(audit.score <= 35)
        assertContains(audit.personalScore.matchedAllergens, "lait")
        assertTrue(audit.warnings.any { it.contains("halal") })
        assertTrue(audit.warnings.any { it.contains("sans gluten") })
    }
}
