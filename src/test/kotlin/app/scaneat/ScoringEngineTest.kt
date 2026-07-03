package app.scaneat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}
