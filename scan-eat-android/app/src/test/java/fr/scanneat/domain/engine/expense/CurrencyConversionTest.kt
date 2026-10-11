package fr.scanneat.domain.engine.expense

import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// CURRENCY CONVERSION UNIT TESTS
// No prior test coverage existed for this file (code-audit §D2.6 finding) -
// added to lock in the conversion-factor math since it silently rescales a
// user's entire logged price history (see PriceRepository.convertAllPrices)
// and a sign/inversion error here would corrupt real financial data with no
// error thrown anywhere.
// ============================================================================

class CurrencyConversionTest {

    @Test
    fun `same symbol returns factor 1`() {
        assertEquals(1.0, currencyConversionFactor("€", "€")!!, 0.0001)
        assertEquals(1.0, currencyConversionFactor("$", "$")!!, 0.0001)
        // Even a symbol with no known rate should short-circuit to 1.0 when
        // from == to, since no actual conversion is needed either way.
        assertEquals(1.0, currencyConversionFactor("£", "£")!!, 0.0001)
    }

    @Test
    fun `EUR to USD divides by the USD rate`() {
        // RATES_TO_EUR: "$" -> 0.92 (1 USD = 0.92 EUR), so 1 EUR = 1/0.92 USD.
        val factor = currencyConversionFactor("€", "$")!!
        assertEquals(1.0 / 0.92, factor, 0.0001)
        // A 10€ price should become ~10.87$, not ~9.2$ (that would be the
        // inverted/wrong-direction bug: multiplying by the EUR-per-USD rate
        // instead of dividing by it).
        assertEquals(10.87, 10.0 * factor, 0.01)
    }

    @Test
    fun `USD to EUR multiplies by the USD rate`() {
        val factor = currencyConversionFactor("$", "€")!!
        assertEquals(0.92, factor, 0.0001)
        assertEquals(9.2, 10.0 * factor, 0.0001)
    }

    @Test
    fun `round trip EUR to USD to EUR returns to the original amount`() {
        val toUsd = currencyConversionFactor("€", "$")!!
        val backToEur = currencyConversionFactor("$", "€")!!
        val original = 42.50
        val roundTripped = original * toUsd * backToEur
        assertEquals(original, roundTripped, 0.0001)
    }

    @Test
    fun `unknown symbol on either side returns null so the caller falls back to a plain relabel`() {
        assertNull(currencyConversionFactor("£", "€"))
        assertNull(currencyConversionFactor("€", "£"))
        assertNull(currencyConversionFactor("Autre", "€"))
        // Both unknown - still null, not an accidental 1.0 pass-through.
        assertNull(currencyConversionFactor("£", "¥"))
    }
}
