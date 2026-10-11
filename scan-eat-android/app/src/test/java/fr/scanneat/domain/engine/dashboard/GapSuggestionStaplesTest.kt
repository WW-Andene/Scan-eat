package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.scoring.dailyTargets
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import org.junit.Assert.assertTrue
import org.junit.Test

// Cooking staples (gelatine 86 g protein/100 g, cinnamon 53 g fibre/100 g...)
// must never be offered as "eat this" to close a nutrient gap.
class GapSuggestionStaplesTest {
    @Test
    fun `gap suggestions never include cooking ingredients`() {
        val targets = dailyTargets(Profile(sex = Sex.FEMALE, ageYears = 30, heightCm = 165.0, weightKg = 60.0, activityLevel = ActivityLevel.SEDENTARY))!!
        val empty = ConsumedNutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val staples = FOOD_DB.filter { it.cookingIngredient }.map { it.name }.toSet()
        assertTrue(staples.isNotEmpty())
        val suggested = closeTheGap(empty, targets, FOOD_DB).flatMap { it.suggestions }.map { it.name }
        assertTrue(suggested.isNotEmpty())
        assertTrue("suggested staples: ${suggested.filter { it in staples }}", suggested.none { it in staples })
    }
}
