package fr.scanneat.presentation.foodsearch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchNutrientQueryTest {

    private fun item(salt: Double = 0.0, potassium: Double = 0.0, fiber: Double = 0.0, protein: Double = 0.0) = FoodSearchItem(
        name = "x", kcal = 0.0, proteinG = protein, carbsG = 0.0, fatG = 0.0, fiberG = fiber, saltG = salt,
        ironMg = 0.0, calciumMg = 0.0, vitDUg = 0.0, b12Ug = 0.0, vitCMg = 0.0, magnesiumMg = 0.0,
        potassiumMg = potassium, zincMg = 0.0, vitAUg = 0.0, b9Ug = 0.0, vitEMg = 0.0, vitKUg = 0.0, b6Mg = 0.0,
        category = FoodSearchCategory.CUSTOM,
    )

    // Regression lock: "sodium" was aliased to saltG, so "sodium<200" compared 200 to grams of salt.
    @Test
    fun `sodium is compared in mg derived from salt`() {
        assertTrue(predicateFor("sodium<=200")!!(item(salt = 0.5)))   // 0.5 g salt = 200 mg sodium
        assertFalse(predicateFor("sodium<100")!!(item(salt = 0.5)))
        assertTrue(predicateFor("sodium>400")!!(item(salt = 2.0)))
        assertTrue(predicateFor("sodium")!!(item(salt = 1.6)))        // bare word = high sodium
        assertFalse(predicateFor("sodium")!!(item(salt = 1.0)))
    }

    @Test
    fun `salt, fibre and protein keep their behaviour`() {
        assertTrue(predicateFor("sel>=1,5")!!(item(salt = 1.5)))
        assertTrue(predicateFor("fibres>=3")!!(item(fiber = 3.0)))
        assertTrue(predicateFor("protéine")!!(item(protein = 20.0)))
        assertNull(predicateFor("banane"))
    }

    @Test
    fun `micronutrient thresholds work only with an explicit operator`() {
        assertTrue(predicateFor("potassium > 300")!!(item(potassium = 350.0)))
        assertFalse(predicateFor("potassium>300")!!(item(potassium = 100.0)))
        assertNull(predicateFor("potassium"))
    }
}
