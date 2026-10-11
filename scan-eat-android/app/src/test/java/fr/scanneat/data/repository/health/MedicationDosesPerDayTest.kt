package fr.scanneat.data.repository.health

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationDosesPerDayTest {
    @Test
    fun `one dose per distinct reminder time`() {
        assertEquals(1, Medication(id = "a", name = "x").dosesPerDay)
        assertEquals(2, Medication(id = "a", name = "x", reminderTime = "08:00", extraReminderTimes = "20:00").dosesPerDay)
        assertEquals(2, Medication(id = "a", name = "x", reminderTime = "08:00", extraReminderTimes = "20:00, 08:00").dosesPerDay)
    }
}
