package fr.scanneat.notifications

import fr.scanneat.data.repository.health.Medication
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Roadmap C9: which dose slot the exact alarm is armed for. */
class MedicationAlarmsTest {
    private val now = LocalDateTime.of(2026, 10, 14, 9, 30) // Wednesday

    @Test fun `next slot later today`() {
        val med = Medication(id = "a", name = "A", reminderOn = true, reminderTime = "08:00", extraReminderTimes = "20:00")
        assertEquals(LocalDateTime.of(2026, 10, 14, 20, 0), MedicationAlarms.nextDoseTrigger(listOf(med), now))
    }

    @Test fun `all slots passed rolls over to tomorrow`() {
        val med = Medication(id = "a", name = "A", reminderOn = true, reminderTime = "08:00")
        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 0), MedicationAlarms.nextDoseTrigger(listOf(med), now))
    }

    @Test fun `earliest across medications, inactive and reminder-off ignored`() {
        val meds = listOf(
            Medication(id = "a", name = "A", reminderOn = true, reminderTime = "12:00"),
            Medication(id = "b", name = "B", reminderOn = true, reminderTime = "10:00", active = false),
            Medication(id = "c", name = "C", reminderOn = false, reminderTime = "09:45"),
        )
        assertEquals(LocalDateTime.of(2026, 10, 14, 12, 0), MedicationAlarms.nextDoseTrigger(meds, now))
        assertNull(MedicationAlarms.nextDoseTrigger(emptyList(), now))
    }
}
