package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CaffeineBudgetTest {
    private fun coffee(mgPer100g: Double, g: Double = 100.0) = DiaryEntry(
        date = LocalDate.of(2026, 1, 1), mealSlot = MealSlot.BREAKFAST, productName = "Café", portionG = g,
        nutrition = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0, caffeineMg = mgPer100g),
        source = ScanSource.MANUAL)

    @Test fun `adult limit 400, pregnancy 200, minor 3 mg per kg`() {
        assertEquals(400.0, caffeineDailyLimitMg(Profile(ageYears = 30))!!, 0.0)
        assertEquals(200.0, caffeineDailyLimitMg(Profile(ageYears = 30, healthConditions = setOf("pregnancy")))!!, 0.0)
        assertEquals(150.0, caffeineDailyLimitMg(Profile(ageYears = 15, weightKg = 50.0))!!, 0.0)
        assertNull(caffeineDailyLimitMg(Profile(ageYears = 15)))
    }

    @Test fun `total, over flag and single dose`() {
        val b = caffeineBudget(Profile(ageYears = 30), listOf(coffee(80.0), coffee(250.0)))!!
        assertEquals(330.0, b.totalMg, 0.01)
        assertFalse(b.over)
        assertEquals(1, b.highSingleDoses.size)
        assertNull(caffeineBudget(Profile(ageYears = 30), listOf(coffee(0.0))))
    }
}
