package fr.scanneat.domain.engine.health

import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Roadmap D5: adherence for the doctor report. */
class MedicationAdherenceTest {
    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 10)
    private fun log(d: LocalDate, id: String = "m") = MedicationLogEntry(d.toString() + id + Math.random(), id, "M", d, 0)

    @Test fun `twice daily over 10 days, extra logs capped`() {
        val med = Medication(id = "m", name = "M", reminderTime = "08:00", extraReminderTimes = "20:00")
        val logs = (0 until 10).flatMap { listOf(log(from.plusDays(it.toLong())), log(from.plusDays(it.toLong()))) } +
            List(3) { log(from) } // over-logging on day 1
        val a = medicationAdherence(med, logs, from, to, ZoneOffset.UTC)
        assertEquals(20, a.expected); assertEquals(20, a.taken); assertEquals(100, a.percent)
    }

    @Test fun `counted only from the day it was added`() {
        val added = LocalDate.of(2026, 10, 6).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val med = Medication(id = "m", name = "M", createdAt = added)
        val a = medicationAdherence(med, listOf(log(LocalDate.of(2026, 10, 7))), from, to, ZoneOffset.UTC)
        assertEquals(5, a.expected); assertEquals(1, a.taken)
    }
}
