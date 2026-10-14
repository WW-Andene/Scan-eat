package fr.scanneat.data.repository.health

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Roadmap D6: stock and prescription renewal. */
class MedicationSupplyTest {
    private val med = Medication(id = "m", name = "X", reminderTime = "08:00", extraReminderTimes = "20:00")

    @Test fun `days left divides by doses per day`() {
        assertEquals(10, med.copy(stockCount = 20).daysOfStockLeft())
        assertNull(med.daysOfStockLeft())
    }

    @Test fun `low stock at a week or less`() {
        assertTrue(med.copy(stockCount = 14).isLowStock())
        assertFalse(med.copy(stockCount = 16).isLowStock())
        assertFalse(med.isLowStock())
    }

    @Test fun `renewal due within a week`() {
        val today = LocalDate.of(2026, 10, 14)
        assertTrue(med.copy(renewalDate = today.plusDays(7)).renewalDue(today))
        assertTrue(med.copy(renewalDate = today.minusDays(1)).renewalDue(today))
        assertFalse(med.copy(renewalDate = today.plusDays(8)).renewalDue(today))
    }
}
