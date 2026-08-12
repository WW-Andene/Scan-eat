package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.model.DiaryEntry
import java.time.LocalDate

// ============================================================================
// foodDiversityScore — user-requested: a count of distinct foods eaten over a
// trailing window, independent of any single product's own score. A user can
// eat well-scored food every day and still have a nutritionally narrow diet
// (the same 3-4 products on repeat) - nothing on Dashboard measured variety
// itself before this, only per-product/per-day quality.
// ============================================================================

enum class DiversityLevel { LOW, MODERATE, GOOD, HIGH }

data class FoodDiversityResult(
    val distinctCount: Int,
    val level: DiversityLevel,
)

// Thresholds are for the 7-day window specifically (3 meals/day x 7 = up to
// 21 slots, so even a fairly repetitive eater clears LOW without much
// intent) - callers using a longer window should treat the level as
// approximate, [distinctCount] is the number that actually matters there.
private const val LOW_MAX = 6
private const val MODERATE_MAX = 12
private const val GOOD_MAX = 20

fun foodDiversityScore(entries: List<DiaryEntry>, since: LocalDate, until: LocalDate): FoodDiversityResult {
    val distinct = entries
        .filter { !it.date.isBefore(since) && !it.date.isAfter(until) }
        // Same normalizeKey-free lowercase-trim identity DashboardViewModel's
        // own matchKey() convention uses for "same product" elsewhere on this
        // screen - two logs of "Yaourt nature" and "yaourt nature" are the
        // same food for diversity purposes, not two distinct ones.
        .mapTo(mutableSetOf()) { it.productName.trim().lowercase() }
        .size
    val level = when {
        distinct <= LOW_MAX      -> DiversityLevel.LOW
        distinct <= MODERATE_MAX -> DiversityLevel.MODERATE
        distinct <= GOOD_MAX     -> DiversityLevel.GOOD
        else                     -> DiversityLevel.HIGH
    }
    return FoodDiversityResult(distinct, level)
}
