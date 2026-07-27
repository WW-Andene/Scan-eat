package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.model.DiaryEntry
import java.time.LocalDate

// ============================================================================
// Log-streak math — split out of DashboardAggregator.kt (pure structural
// move, no behavior change). Port of logStreakDays() from presenters.js, plus
// longestLogStreak() (all-time record, not just the current streak).
// ============================================================================

// ============================================================================
// logStreakDays — consecutive days with at least one logged entry (1-day grace)
// Port of logStreakDays() from presenters.js
// ============================================================================

fun logStreakDays(entries: List<DiaryEntry>, today: LocalDate = LocalDate.now()): Int =
    logStreakDays(entries.mapTo(mutableSetOf()) { it.date }, today)

/**
 * Same as the [List]<[DiaryEntry]> overload above, but takes the logged-date set directly -
 * callers computing "current streak" should pass every date ever logged (e.g.
 * ConsumptionRepository.getAllLoggedDates()), not a fixed-size window's entries. A streak
 * longer than whatever window the caller happened to query would otherwise silently cap at
 * that window's length (e.g. a real 45-day streak reported as 30 by a 30-day-windowed caller).
 */
fun logStreakDays(loggedDates: Set<LocalDate>, today: LocalDate = LocalDate.now()): Int {
    if (loggedDates.isEmpty()) return 0
    var cursor = today
    if (!loggedDates.contains(cursor)) {
        cursor = cursor.minusDays(1)
        if (!loggedDates.contains(cursor)) return 0
    }
    var streak = 0
    while (loggedDates.contains(cursor)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

// ============================================================================
// longestLogStreak — the longest-ever run of consecutive logged days
//
// logStreakDays() above only reports the *current* streak (anchored at
// `today`, with a 1-day grace period). It can't answer "what's my record?"
// once a streak breaks — a user who logged 30 days in a row last month and
// then missed a day sees that achievement disappear entirely. This scans
// the full set of logged dates and returns the longest unbroken run found
// anywhere in the history (no grace period — a genuine gap ends the run).
// ============================================================================

fun longestLogStreak(entries: List<DiaryEntry>): Int = longestLogStreak(entries.mapTo(mutableSetOf()) { it.date })

/**
 * Same as the [List]<[DiaryEntry]> overload above, but takes the logged-date set directly.
 * Same window-capping risk as [logStreakDays]'s overload - callers should pass every date
 * ever logged, not a fixed-window's entries, or this can never report a record longer than
 * that window even though its whole purpose is finding the longest run anywhere in history.
 */
fun longestLogStreak(loggedDates: Set<LocalDate>): Int {
    if (loggedDates.isEmpty()) return 0
    val days = loggedDates.toSortedSet()
    var best = 1
    var current = 1
    var prev: LocalDate? = null
    for (day in days) {
        if (prev != null && day == prev.plusDays(1)) {
            current++
        } else {
            current = 1
        }
        if (current > best) best = current
        prev = day
    }
    return best
}
