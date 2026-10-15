package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AlcoholWeekTest {
    private val end = LocalDate.of(2026, 10, 14)
    private fun drink(day: Long, ml: Double, vol: Double?, cat: ProductCategory = ProductCategory.ALCOHOLIC_BEVERAGE) = DiaryEntry(
        date = end.minusDays(day), mealSlot = MealSlot.DINNER, productName = "Vin", portionG = ml,
        nutrition = NutritionPer100g(energyKcal = 0.0, fatG = 0.0, saturatedFatG = 0.0, carbsG = 0.0, sugarsG = 0.0, fiberG = 0.0, proteinG = 0.0, saltG = 0.0, alcoholPercentVol = vol),
        source = ScanSource.MANUAL, category = cat)

    @Test fun `125 mL of 12 percent wine is 1 2 standard drinks`() {
        val w = alcoholWeek(Profile(), listOf(drink(0, 125.0, 12.0)), end)!!
        assertEquals(1.2, w.standardDrinks, 0.01)
        assertEquals(1, w.daysWithAlcohol)
    }

    @Test fun `days over two, window of 7 days, unknown strength`() {
        val w = alcoholWeek(Profile(), listOf(drink(1, 500.0, 6.0), drink(7, 500.0, 6.0), drink(2, 330.0, null)), end)!!
        assertEquals(2.4, w.standardDrinks, 0.01)  // the day-7 drink is outside the window
        assertEquals(1, w.daysOverTwo)
        assertEquals(1, w.unknownStrength)
    }

    @Test fun `pregnancy flags any alcohol, none logged returns null`() {
        assertTrue(alcoholWeek(Profile(healthConditions = setOf("pregnancy")), listOf(drink(0, 100.0, 5.0)), end)!!.overWeekly)
        assertNull(alcoholWeek(Profile(), listOf(drink(0, 330.0, 0.0, ProductCategory.OTHER)), end))
    }
}
