package fr.scanneat.domain.engine.withdrawal

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.ProductCategory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** An alcohol-free challenge (7, 30 or 60 days), tracked from the diary - nothing extra to log. */
data class AlcoholChallenge(val startDate: LocalDate, val days: Int) {
    val endDate: LocalDate get() = startDate.plusDays(days.toLong() - 1)
}

data class AlcoholChallengeStatus(
    val dayNumber: Int,
    val alcoholFreeDays: Int,
    val daysWithAlcohol: List<LocalDate>,
    val currentStreak: Int,
    val finished: Boolean,
)

internal fun DiaryEntry.isAlcoholic(): Boolean =
    (nutrition.alcoholPercentVol ?: 0.0) > 0.5 || (category == ProductCategory.ALCOHOLIC_BEVERAGE && nutrition.alcoholPercentVol == null)

/** Days of the challenge so far (up to [today]); a day without any alcoholic entry counts as alcohol-free. */
fun alcoholChallengeStatus(c: AlcoholChallenge, entries: List<DiaryEntry>, today: LocalDate): AlcoholChallengeStatus {
    val last = minOf(today, c.endDate)
    if (last.isBefore(c.startDate)) return AlcoholChallengeStatus(0, 0, emptyList(), 0, false)
    val elapsed = (0..ChronoUnit.DAYS.between(c.startDate, last)).map { c.startDate.plusDays(it) }
    val alcoholDays = entries.filter { it.isAlcoholic() }.map { it.date }.toSet()
    val withAlcohol = elapsed.filter { it in alcoholDays }
    var streak = 0
    for (d in elapsed.asReversed()) { if (d in alcoholDays) break; streak++ }
    return AlcoholChallengeStatus(
        dayNumber = elapsed.size,
        alcoholFreeDays = elapsed.size - withAlcohol.size,
        daysWithAlcohol = withAlcohol,
        currentStreak = streak,
        finished = today.isAfter(c.endDate),
    )
}
