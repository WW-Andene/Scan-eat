package fr.scanneat.domain.engine.withdrawal

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.ProductCategory
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An alcohol-free challenge (7, 30 or 60 days), tracked from the diary - nothing extra to log.
 * [drinksPerWeek]: the user's usual intake, only used when the diary before the
 * challenge is too thin to say what they normally drink.
 */
data class AlcoholChallenge(val startDate: LocalDate, val days: Int, val drinksPerWeek: Int? = null) {
    val endDate: LocalDate get() = startDate.plusDays(days.toLong() - 1)
}

data class AlcoholChallengeStatus(
    val dayNumber: Int,
    val alcoholFreeDays: Int,
    val daysWithAlcohol: List<LocalDate>,
    val currentStreak: Int,
    val finished: Boolean,
    /** Kcal of alcoholic drinks avoided so far; null when no baseline is known. */
    val kcalAvoided: Int? = null,
    val baselineFromDiary: Boolean = false,
)

/** Days the diary must cover in the 4 weeks before the challenge for its alcohol total to be a usable baseline. */
internal const val BASELINE_WINDOW_DAYS = 28L
internal const val BASELINE_MIN_LOGGED_DAYS = 14
/** One standard drink = 10 g of pure alcohol x 7 kcal/g (alcohol alone, without the drink's sugar). */
internal const val KCAL_PER_STANDARD_DRINK = 70.0

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
    // Baseline: alcoholic-drink kcal per day over the 4 weeks before the start, if
    // the diary was kept on enough of those days; else the declared drinks/week.
    val before = entries.filter { it.date.isBefore(c.startDate) && !it.date.isBefore(c.startDate.minusDays(BASELINE_WINDOW_DAYS)) }
    val diaryBaseline = if (before.map { it.date }.toSet().size >= BASELINE_MIN_LOGGED_DAYS)
        before.filter { it.isAlcoholic() }.sumOf { it.consumed.energyKcal } / BASELINE_WINDOW_DAYS else null
    val perDay = diaryBaseline ?: c.drinksPerWeek?.let { it * KCAL_PER_STANDARD_DRINK / 7.0 }
    val duringKcal = entries.filter { !it.date.isBefore(c.startDate) && !it.date.isAfter(last) && it.isAlcoholic() }.sumOf { it.consumed.energyKcal }
    val avoided = perDay?.let { (it * elapsed.size - duringKcal).coerceAtLeast(0.0).toInt() }
    return AlcoholChallengeStatus(
        kcalAvoided = avoided,
        baselineFromDiary = diaryBaseline != null,
        dayNumber = elapsed.size,
        alcoholFreeDays = elapsed.size - withAlcohol.size,
        daysWithAlcohol = withAlcohol,
        currentStreak = streak,
        finished = today.isAfter(c.endDate),
    )
}
