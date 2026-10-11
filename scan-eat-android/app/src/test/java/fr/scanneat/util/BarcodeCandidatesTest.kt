package fr.scanneat.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeCandidatesTest {
    @Test
    fun `GTIN-14 case code yields the retail EAN-13`() {
        // Case code (indicator 1) of EAN-13 3017620422003.
        val c = barcodeCandidates("13017620422000")
        assertEquals("13017620422000", c.first())
        assertTrue("3017620422003" in c)
        assertTrue(c.none { it.length == 14 && it != "13017620422000" })
    }

    @Test
    fun `GTIN-14 of a UPC-A product also yields the UPC-A form`() {
        // Case code of UPC-A 049000028911 (EAN-13 0049000028911).
        val c = barcodeCandidates("10049000028918")
        assertTrue("0049000028911" in c)
        assertTrue("049000028911" in c)
    }

    @Test
    fun `GS1 check digit validation`() {
        assertTrue(hasValidGs1CheckDigit("3017620422003"))
        assertTrue(!hasValidGs1CheckDigit("3017620422004"))
    }
}

class UpcEExpansionTest {
    @Test
    fun `UPC-E expands to its UPC-A and EAN-13 forms`() {
        // GS1 reference pair: UPC-E 04252614 <-> UPC-A 042100005264.
        val c = barcodeCandidates("04252614")
        assertTrue(c.toString(), "042100005264" in c)
        assertTrue(c.toString(), "0042100005264" in c)
    }
}
