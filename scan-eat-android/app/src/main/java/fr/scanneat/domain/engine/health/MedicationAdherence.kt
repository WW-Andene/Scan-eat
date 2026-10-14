package fr.scanneat.domain.engine.health

import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Doses logged vs doses expected over a period (Roadmap D5/D6). */
data class Adherence(val taken: Int, val expected: Int) {
    val percent: Int? get() = if (expected == 0) null else (taken * 100 / expected)
}

/**
 * Expected = scheduled days in [from, to] (not before the medication was
 * added, not after it was stopped) × doses per day. Taken = logged doses,
 * at most the scheduled number per day (extra logs never push it over 100%).
 */
fun medicationAdherence(med: Medication, logs: List<MedicationLogEntry>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Adherence {
    fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val start = if (med.createdAt > 0) maxOf(from, day(med.createdAt)) else from
    val end = med.deactivatedAt?.let { minOf(to, day(it)) } ?: to
    if (end.isBefore(start)) return Adherence(0, 0)
    val perDay = med.dosesPerDay
    var expected = 0
    var d = start
    while (!d.isAfter(end)) { if (med.isScheduledOn(d.dayOfWeek)) expected += perDay; d = d.plusDays(1) }
    val taken = logs.filter { it.medicationId == med.id && !it.date.isBefore(start) && !it.date.isAfter(end) }
        .groupingBy { it.date }.eachCount().values.sumOf { minOf(it, perDay) }
    return Adherence(taken, expected)
}
