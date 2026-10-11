package fr.scanneat.domain.engine.dashboard

import fr.scanneat.data.repository.mood.MoodEntry
import fr.scanneat.data.repository.sleep.SleepEntry
import java.time.LocalDate

// ============================================================================
// computeSleepMoodCorrelation — Sleep and Mood are tracked separately
// (SleepRepository/MoodRepository, both date-keyed) but nothing in the app
// ever crossed them, despite both being logged for the same day and an
// obvious real-world link between the two. Not a real Pearson correlation
// coefficient - a simple, honestly-labeled "average mood on good-sleep
// nights vs. poor-sleep nights" comparison instead, since a correlation
// coefficient over what's usually a handful of paired days would overclaim
// statistical confidence this data genuinely doesn't support.
// ============================================================================

data class SleepMoodCorrelation(
    val goodSleepAvgMood: Double,
    val poorSleepAvgMood: Double,
    val goodSleepNights: Int,
    val poorSleepNights: Int,
)

/** Quality 4-5 counts as "good", 1-2 as "poor" - 3 is neither, same
 *  deliberately-excluded-middle the comparison needs to stay a real signal
 *  rather than washed out by borderline nights. */
private const val GOOD_QUALITY_MIN = 4
private const val POOR_QUALITY_MAX = 2

/** Below this many paired days on either side, the average is too noisy
 *  (a single unusually good/bad mood day) to present as a real comparison. */
private const val MIN_NIGHTS_PER_SIDE = 3

fun computeSleepMoodCorrelation(
    sleepEntries: List<SleepEntry>,
    moodEntries: List<MoodEntry>,
    days: Int = 30,
    today: LocalDate = LocalDate.now(),
): SleepMoodCorrelation? {
    val since = today.minusDays((days - 1).toLong())
    val sleepByDate = sleepEntries.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }.associateBy { it.date }
    val moodByDate = moodEntries.filter { !it.date.isBefore(since) && !it.date.isAfter(today) }.associateBy { it.date }
    val pairedDates = sleepByDate.keys.intersect(moodByDate.keys)

    val goodNights = pairedDates.filter { sleepByDate.getValue(it).quality >= GOOD_QUALITY_MIN }
    val poorNights = pairedDates.filter { sleepByDate.getValue(it).quality <= POOR_QUALITY_MAX }
    if (goodNights.size < MIN_NIGHTS_PER_SIDE || poorNights.size < MIN_NIGHTS_PER_SIDE) return null

    return SleepMoodCorrelation(
        goodSleepAvgMood = goodNights.map { moodByDate.getValue(it).mood }.average(),
        poorSleepAvgMood = poorNights.map { moodByDate.getValue(it).mood }.average(),
        goodSleepNights = goodNights.size,
        poorSleepNights = poorNights.size,
    )
}
