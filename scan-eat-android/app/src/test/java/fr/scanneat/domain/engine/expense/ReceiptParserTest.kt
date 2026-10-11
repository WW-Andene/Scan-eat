package fr.scanneat.domain.engine.expense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun `parses plain item lines and skips totals and payment lines`() {
        val r = parseReceiptLines("LAIT DEMI ECREME 1,05\nPAIN 1.20 E\nTOTAL TTC 2,25\nCARTE BANCAIRE 2,25\nTVA 5,5% 0,12")
        assertEquals(listOf("LAIT DEMI ECREME" to 1.05, "PAIN" to 1.20), r.map { it.rawName to it.priceEuros })
    }

    // Regression lock: "total"/"carte" were bare substring exclusions.
    @Test
    fun `products named Total or Carte d'Or are kept`() {
        val r = parseReceiptLines("FAGE TOTAL 0% 2,19\nCARTE D'OR VANILLE 3,49\nTOTAL 5,68")
        assertEquals(listOf("FAGE TOTAL 0%", "CARTE D'OR VANILLE"), r.map { it.rawName })
    }

    // Regression lock: a negative amount is a discount, not a 0,50 EUR purchase.
    @Test
    fun `discount lines are not purchases`() {
        val r = parseReceiptLines("REMISE FIDELITE -0,50\nPROMO PATES -0,30\nPATES 1,40")
        assertEquals(listOf("PATES"), r.map { it.rawName })
        assertTrue(r.none { it.priceEuros < 1.0 })
    }
}
