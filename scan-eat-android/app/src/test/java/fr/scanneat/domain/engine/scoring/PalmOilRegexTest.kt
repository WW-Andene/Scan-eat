package fr.scanneat.domain.engine.scoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalmOilRegexTest {
    @Test
    fun `matches palm oil and derivatives`() {
        for (n in listOf("Huile de palme", "huile de palmiste", "Graisse de palme", "stéarine de palme",
            "oléine de palme", "palm oil", "Palm kernel oil", "huiles végétales (palme, colza)", "palme", "palm fat")) {
            assertTrue(n, PALM_OIL_REGEX.containsMatchIn(n))
        }
    }

    @Test
    fun `does not match coconut or palm sweeteners`() {
        for (n in listOf("huile de coprah", "huile de coco", "coconut oil", "sucre de palme", "sirop de palme",
            "palm sugar", "palm syrup", "coeur de palmier", "palmitate")) {
            assertFalse(n, PALM_OIL_REGEX.containsMatchIn(n))
        }
    }
}
