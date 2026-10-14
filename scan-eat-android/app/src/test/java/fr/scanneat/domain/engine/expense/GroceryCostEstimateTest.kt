package fr.scanneat.domain.engine.expense

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Roadmap D3. */
class GroceryCostEstimateTest {
    @Test fun `latest whole-word price per item, unknown items unpriced`() {
        val d = LocalDate.of(2026, 10, 1)
        val prices = listOf(
            Triple("Tomates cerises bio", d, 2.50), Triple("Tomates cerises bio", d.plusDays(5), 2.90),
            Triple("Lait demi-écrémé", d, 1.10), Triple("Pommes de terre", d, 3.00),
        )
        val e = estimateGroceryCost(listOf("tomate", "lait", "riz", "pomme"), prices)
        assertEquals(2.90 + 1.10 + 3.00, e.total, 1e-9)
        assertEquals(3, e.pricedCount); assertEquals(1, e.unpricedCount)
    }
}
