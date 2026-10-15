package fr.scanneat.domain.engine.withdrawal

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Gradual caffeine reduction chosen by the user: the daily cap goes down in a
 * straight line from [startMg] to [targetMg] over [weeks]. A gradual cut is
 * meant to soften the headaches and tiredness an abrupt stop can bring; the
 * cap never goes above the EFSA limit the diary card already applies.
 */
data class CaffeineTaper(val startDate: LocalDate, val startMg: Int, val targetMg: Int, val weeks: Int) {
    val endDate: LocalDate get() = startDate.plusWeeks(weeks.toLong())

    fun capOn(date: LocalDate): Int {
        if (!date.isAfter(startDate)) return startMg
        if (!date.isBefore(endDate)) return targetMg
        val total = ChronoUnit.DAYS.between(startDate, endDate).toDouble()
        val done = ChronoUnit.DAYS.between(startDate, date).toDouble()
        return (startMg + (targetMg - startMg) * done / total).roundToInt()
    }
}
