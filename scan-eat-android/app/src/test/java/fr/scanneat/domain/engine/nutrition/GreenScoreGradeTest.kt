package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.*
import org.junit.Test

/** Roadmap A10: Green-Score A+ and F must no longer be dropped. */
class GreenScoreGradeTest {

    @Test
    fun `A+ and F are kept`() {
        assertEquals("a-plus", normalizeGreenScoreGrade("a-plus"))
        assertEquals("a-plus", normalizeGreenScoreGrade("A+"))
        assertEquals("f", normalizeGreenScoreGrade("F"))
    }

    @Test
    fun `a to e still accepted, non-grades rejected`() {
        ('a'..'e').forEach { assertEquals(it.toString(), normalizeGreenScoreGrade(it.toString())) }
        assertNull(normalizeGreenScoreGrade("unknown"))
        assertNull(normalizeGreenScoreGrade("not-applicable"))
        assertNull(normalizeGreenScoreGrade(null))
        assertNull(normalizeGreenScoreGrade("g"))
    }

    @Test
    fun `environmental_score field preferred, legacy ecoscore as fallback`() {
        assertEquals("a-plus", pickGreenScoreGrade("a-plus", "a"))
        assertEquals("b", pickGreenScoreGrade("unknown", "b"))
        assertEquals("c", pickGreenScoreGrade(null, "c"))
    }

    @Test
    fun `display labels`() {
        assertEquals("A+", greenScoreDisplay("a-plus"))
        assertEquals("F", greenScoreDisplay("f"))
    }
}
