package fr.scanneat.domain.engine.nutrition

import org.junit.Assert.*
import org.junit.Test

class ProductNameCandidatesTest {
    private fun l(t: String, top: Int, h: Int) = OcrLine(t, 0, top, 300, top + h)

    @Test fun `biggest text first, two stacked lines joined`() {
        val lines = listOf(
            l("Maison Dupont", 10, 20),
            l("Pâtes à la", 50, 60), l("bolognaise", 115, 58),
            l("Poids net : 350 g", 200, 15),
            l("Valeurs nutritionnelles pour 100 g", 230, 12),
        )
        val c = productNameCandidates(lines)
        assertEquals("Pâtes à la bolognaise", c.first())
        assertFalse(c.any { "Poids" in it || "Valeurs" in it })
    }

    @Test fun `digits and short text are not names`() {
        assertTrue(productNameCandidates(listOf(l("3 760 123 456 789", 0, 40), l("Bio", 50, 40))).isEmpty())
    }
}
