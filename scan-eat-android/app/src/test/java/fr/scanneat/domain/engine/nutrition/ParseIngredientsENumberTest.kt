package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseIngredientsENumberTest {
    private fun eNumbers(text: String) = parseIngredients(text).mapNotNull { it.eNumber }.toSet()

    @Test
    fun `four-digit E-numbers are not truncated`() {
        // E1422 is modified starch; truncation read it as E142 (Green S colorant).
        assertEquals(setOf("E1422"), eNumbers("amidon modifié E1422, sucre"))
    }

    @Test
    fun `every E-number inside a grouped ingredient is kept`() {
        assertEquals(setOf("E322", "E471"), eNumbers("farine de blé, émulsifiants (E322, E471)"))
    }

    @Test
    fun `spaced and hyphenated E-numbers are recognised`() {
        assertEquals(setOf("E330", "E202", "E150d"), eNumbers("acidifiant : E 330, conservateur E-202, colorant E150d"))
    }
}
