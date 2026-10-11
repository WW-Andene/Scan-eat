package fr.scanneat.domain.engine.dashboard

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.roundTo1Decimal
import java.time.LocalDate

// ============================================================================
// DASHBOARD AGGREGATOR — port of public/core/presenters.js
//
// Pure functions — no I/O, no side effects.
// Ported: weeklyRollup, monthlyRollup, logStreakDays, closeTheGap,
//         weightForecast, weekOverWeekDelta.
//
// This file now holds only the day-bucket rollup core (weekly/monthly/custom
// windows + week-over-week/month-over-month deltas). The other concerns that
// used to live in this single file have moved to sibling files in this same
// package: log-streak math (DashboardStreaks.kt), nutrient-gap suggestions
// (DashboardGapAnalysis.kt), weight-goal forecasting (DashboardWeightForecast.kt),
// and the cross-tracker weight-vs-intake insight (DashboardCrossTrackerInsight.kt).
// Pure structural split — no behavior changed.
// ============================================================================

// ---- Data shapes ----

data class DayBucket(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val satFatG: Double,
    val sugarsG: Double,
    val saltG: Double,
    val count: Int,
    val fiberG: Double = 0.0,
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
)

data class NutrientTotals(
    val kcal: Double      = 0.0,
    val proteinG: Double  = 0.0,
    val carbsG: Double    = 0.0,
    val fatG: Double      = 0.0,
    val satFatG: Double   = 0.0,
    val sugarsG: Double   = 0.0,
    val saltG: Double     = 0.0,
    val ironMg: Double    = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double    = 0.0,
    val b12Ug: Double     = 0.0,
    val fiberG: Double    = 0.0,
    val count: Int        = 0,
)

data class RollupResult(
    val days: List<DayBucket>,
    val total: NutrientTotals,
    val avg: NutrientTotals,
    val daysLogged: Int,
)

// ============================================================================
// weeklyRollup — 7-day window ending at [end]
// Port of weeklyRollup() from presenters.js
// ============================================================================

fun weeklyRollup(entries: List<DiaryEntry>, end: LocalDate = LocalDate.now()): RollupResult =
    rollup(entries, end, windowDays = 7)

// ============================================================================
// monthlyRollup — 30-day trailing window ending at [end]
// Port of monthlyRollup() from presenters.js
// ============================================================================

fun monthlyRollup(entries: List<DiaryEntry>, end: LocalDate = LocalDate.now()): RollupResult =
    rollup(entries, end, windowDays = 30)

// ============================================================================
// customRollup — same day-bucketing as weekly/monthlyRollup, arbitrary window.
// Used by the Biolism Evolution tab's longer-range macro-intake trends.
// ============================================================================

fun customRollup(entries: List<DiaryEntry>, end: LocalDate = LocalDate.now(), windowDays: Int): RollupResult =
    rollup(entries, end, windowDays)

// Internal (not private) so chronicNutrientGaps() in DashboardGapAnalysis.kt,
// a sibling file in this same package, can reuse the exact same day-bucketing
// logic instead of duplicating it. No behavior change - same visibility rules
// apply within one package/module either way.
internal fun rollup(entries: List<DiaryEntry>, end: LocalDate, windowDays: Int): RollupResult {
    val days = (windowDays - 1 downTo 0).map { i ->
        end.minusDays(i.toLong())
    }

    val byDate = days.associateWith { date ->
        entries.filter { it.date == date }
    }

    val buckets = days.map { date ->
        val dayEntries = byDate[date] ?: emptyList()
        if (dayEntries.isEmpty()) {
            DayBucket(date, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        } else {
            DayBucket(
                date      = date,
                kcal      = dayEntries.sumOf { it.consumed.energyKcal },
                proteinG  = dayEntries.sumOf { it.consumed.proteinG },
                carbsG    = dayEntries.sumOf { it.consumed.carbsG },
                fatG      = dayEntries.sumOf { it.consumed.fatG },
                satFatG   = dayEntries.sumOf { it.consumed.saturatedFatG },
                sugarsG   = dayEntries.sumOf { it.consumed.sugarsG },
                saltG     = dayEntries.sumOf { it.consumed.saltG },
                count     = dayEntries.size,
                fiberG    = dayEntries.sumOf { it.consumed.fiberG },
                ironMg    = dayEntries.sumOf { it.consumed.ironMg },
                calciumMg = dayEntries.sumOf { it.consumed.calciumMg },
                vitDUg    = dayEntries.sumOf { it.consumed.vitDUg },
                b12Ug     = dayEntries.sumOf { it.consumed.b12Ug },
            )
        }
    }

    val daysLogged = buckets.count { it.count > 0 }
    val denom = daysLogged.coerceAtLeast(1).toDouble()

    fun r1(v: Double) = v.roundTo1Decimal()

    val total = NutrientTotals(
        kcal      = buckets.sumOf { it.kcal },
        proteinG  = buckets.sumOf { it.proteinG },
        carbsG    = buckets.sumOf { it.carbsG },
        fatG      = buckets.sumOf { it.fatG },
        satFatG   = buckets.sumOf { it.satFatG },
        sugarsG   = buckets.sumOf { it.sugarsG },
        saltG     = buckets.sumOf { it.saltG },
        count     = buckets.sumOf { it.count },
        fiberG    = buckets.sumOf { it.fiberG },
        ironMg    = buckets.sumOf { it.ironMg },
        calciumMg = buckets.sumOf { it.calciumMg },
        vitDUg    = buckets.sumOf { it.vitDUg },
        b12Ug     = buckets.sumOf { it.b12Ug },
    )

    val avg = NutrientTotals(
        kcal      = r1(total.kcal / denom),
        proteinG  = r1(total.proteinG / denom),
        carbsG    = r1(total.carbsG / denom),
        fatG      = r1(total.fatG / denom),
        satFatG   = r1(total.satFatG / denom),
        sugarsG   = r1(total.sugarsG / denom),
        saltG     = r1(total.saltG / denom),
        fiberG    = r1(total.fiberG / denom),
        ironMg    = r1(total.ironMg / denom),
        calciumMg = r1(total.calciumMg / denom),
        vitDUg    = r1(total.vitDUg / denom),
        b12Ug     = r1(total.b12Ug / denom),
    )

    return RollupResult(buckets, total, avg, daysLogged)
}

// ============================================================================
// weekOverWeekDelta — compare this week to the prior week
// Port of weekOverWeekDelta() from presenters.js
// ============================================================================

data class WeekOverWeekDelta(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

fun weekOverWeekDelta(current: RollupResult, prior: RollupResult): WeekOverWeekDelta {
    // Plain subtraction, not a ratio — a zero-guard here isn't protecting against
    // division by zero, it was silently reporting "no change" whenever the prior
    // week had no logged data at all, hiding a real (and often large) jump.
    fun delta(a: Double, b: Double) = (a - b).roundTo1Decimal()
    return WeekOverWeekDelta(
        kcal     = delta(current.avg.kcal,     prior.avg.kcal),
        proteinG = delta(current.avg.proteinG, prior.avg.proteinG),
        carbsG   = delta(current.avg.carbsG,   prior.avg.carbsG),
        fatG     = delta(current.avg.fatG,     prior.avg.fatG),
    )
}

/**
 * Same delta math as [weekOverWeekDelta], for a 30-day window instead of 7 -
 * MonthlyTrendCard only ever plotted 30 daily bars against a flat target line,
 * with no comparison number to the prior 30-day window the way WeekDeltaCard
 * already has for weeks. Reuses [WeekOverWeekDelta]'s shape (it's just a kcal/
 * protein/carbs/fat delta struct, not actually week-specific) rather than
 * introducing a parallel type for the exact same four fields.
 */
fun monthOverMonthDelta(current: RollupResult, prior: RollupResult): WeekOverWeekDelta {
    fun delta(a: Double, b: Double) = (a - b).roundTo1Decimal()
    return WeekOverWeekDelta(
        kcal     = delta(current.avg.kcal,     prior.avg.kcal),
        proteinG = delta(current.avg.proteinG, prior.avg.proteinG),
        carbsG   = delta(current.avg.carbsG,   prior.avg.carbsG),
        fatG     = delta(current.avg.fatG,     prior.avg.fatG),
    )
}

// DailyTargets (from PersonalScoreEngine) now includes all micronutrient targets.
