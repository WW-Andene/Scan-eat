package fr.scanneat.data

import fr.scanneat.util.offContributionUrl
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N1. */
class OffContributionUrlTest {
    @Test fun `valid GTIN gives the product page, anything else nothing`() {
        assertEquals("https://world.openfoodfacts.org/product/3017620422003", offContributionUrl("3017620422003"))
        assertNull(offContributionUrl("123"))
        assertNull(offContributionUrl(""))
    }
}
