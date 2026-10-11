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

class AdditiveTagsENumberTest {
    @Test
    fun `four-digit OFF additive tags are not truncated`() {
        assertEquals(listOf("E1422"), additiveTagsToIngredients(listOf("en:e1422"), emptyList()).map { it.eNumber })
    }

    @Test
    fun `nested parentheses keep their group together`() {
        assertEquals(
            listOf("sauce (tomate, oignon (frais, bio))", "sel"),
            parseIngredients("sauce (tomate, oignon (frais, bio)), sel").map { it.name },
        )
        assertEquals(
            listOf("chocolat (pâte de cacao, sucre)", "lait"),
            parseIngredients("chocolat (pâte de cacao, sucre), lait").map { it.name },
        )
    }

    @Test
    fun `unbalanced parentheses still split the rest of the list`() {
        assertEquals(
            listOf("farine (blé", "eau", "sel"),
            parseIngredients("farine (blé, eau, sel").map { it.name },
        )
    }
}
