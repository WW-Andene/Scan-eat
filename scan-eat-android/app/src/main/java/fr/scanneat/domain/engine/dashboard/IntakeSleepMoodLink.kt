package fr.scanneat.domain.engine.dashboard

import fr.scanneat.data.repository.mood.MoodEntry
import fr.scanneat.data.repository.sleep.SleepEntry
import fr.scanneat.domain.model.DiaryEntry
import java.time.LocalDate

// ============================================================================
// computeIntakeSleepMoodLink — "lien entre le score des produits scannés
// (caféine, sucre...) et les entrées sommeil/humeur du même jour". Diary
// (what was eaten), Sleep and Mood were three trackers with zero cross-
// reference between diet and how the user actually slept/felt that same
// day. Same "good vs. poor" average-comparison approach
// computeSleepMoodCorrelation already uses (not a real correlation
// coefficient - see that file's own doc comment for why), applied to two
// intuitive, well-evidenced pairs: caffeine intake vs. that night's sleep
// quality, and sugar intake vs. that day's mood.
// ============================================================================

data class IntakeSleepMoodLink(
    val highCaffeineAvgSleepQuality: Double,
    val normalCaffeineAvgSleepQuality: Double,
    val highCaffeineDays: Int,
    val normalCaffeineDays: Int,
    val highSugarAvgMood: Double,
    val normalSugarAvgMood: Double,
    val highSugarDays: Int,
    val normalSugarDays: Int,
)

/** ~2 cups of coffee - a commonly-cited threshold past which caffeine starts
 *  measurably disrupting sleep for a typical adult, not a clinical cutoff. */
private const val HIGH_CAFFEINE_MG = 200.0

/** WHO's "free sugars" daily ceiling for a ~2000 kcal diet - the same public
 *  health reference point already used elsewhere in this app's nutrition
 *  guidance, not an arbitrary round number. */
private const val HIGH_SUGAR_G = 50.0

private const val MIN_DAYS_PER_SIDE = 3

fun computeIntakeSleepMoodLink(
    diaryEntries: List<DiaryEntry>,
    sleepEntries: List<SleepEntry>,
    moodEntries: List<MoodEntry>,
    days: Int = 30,
    today: LocalDate = LocalDate.now(),
): IntakeSleepMoodLink? {
    val since = today.minusDays((days - 1).toLong())
    val inWindow = diaryEntries.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }
    val caffeineByDate = inWindow.groupBy { it.date }.mapValues { (_, es) -> es.sumOf { it.consumed.caffeineMg } }
    val sugarByDate = inWindow.groupBy { it.date }.mapValues { (_, es) -> es.sumOf { it.consumed.sugarsG } }
    val sleepByDate = sleepEntries.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }.associateBy { it.date }
    val moodByDate = moodEntries.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }.associateBy { it.date }

    val caffeineSleepDates = caffeineByDate.keys.intersect(sleepByDate.keys)
    val highCaffeine = caffeineSleepDates.filter { caffeineByDate.getValue(it) >= HIGH_CAFFEINE_MG }
    val normalCaffeine = caffeineSleepDates.filter { caffeineByDate.getValue(it) < HIGH_CAFFEINE_MG }

    val sugarMoodDates = sugarByDate.keys.intersect(moodByDate.keys)
    val highSugar = sugarMoodDates.filter { sugarByDate.getValue(it) >= HIGH_SUGAR_G }
    val normalSugar = sugarMoodDates.filter { sugarByDate.getValue(it) < HIGH_SUGAR_G }

    val caffeineUsable = highCaffeine.size >= MIN_DAYS_PER_SIDE && normalCaffeine.size >= MIN_DAYS_PER_SIDE
    val sugarUsable = highSugar.size >= MIN_DAYS_PER_SIDE && normalSugar.size >= MIN_DAYS_PER_SIDE
    if (!caffeineUsable && !sugarUsable) return null

    return IntakeSleepMoodLink(
        highCaffeineAvgSleepQuality   = if (caffeineUsable) highCaffeine.map { sleepByDate.getValue(it).quality }.average() else 0.0,
        normalCaffeineAvgSleepQuality = if (caffeineUsable) normalCaffeine.map { sleepByDate.getValue(it).quality }.average() else 0.0,
        highCaffeineDays   = if (caffeineUsable) highCaffeine.size else 0,
        normalCaffeineDays = if (caffeineUsable) normalCaffeine.size else 0,
        highSugarAvgMood   = if (sugarUsable) highSugar.map { moodByDate.getValue(it).mood }.average() else 0.0,
        normalSugarAvgMood = if (sugarUsable) normalSugar.map { moodByDate.getValue(it).mood }.average() else 0.0,
        highSugarDays   = if (sugarUsable) highSugar.size else 0,
        normalSugarDays = if (sugarUsable) normalSugar.size else 0,
    )
}
