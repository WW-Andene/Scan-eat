package fr.scanneat.domain.engine.dashboard

import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================================
// weeklyCrossTrackerInsight — checks whether this week's calorie deficit/
// surplus actually agrees with the real weight-trend direction.
//
// Every function in the sibling files in this package reports on exactly one
// metric in isolation (weeklyRollup on intake, weightForecast on the scale).
// Five trackers (nutrition, weight, activity, hydration, fasting) never
// cross-reference each other anywhere in the app, so a user has no way to see
// "am I actually eating the deficit I think I am, and does the scale agree?"
// without doing the arithmetic themselves. Requires at least [minLoggedDays]
// logged diary days this week (a near-empty week's average is too noisy to
// compare against anything) and a real weight-trend regression (WeightRepository
// already returns 0.0 when it has fewer than 2 points).
//
// Split out of DashboardAggregator.kt (pure structural move, no behavior
// change).
// ============================================================================

enum class InsightAgreement { CONSISTENT, MISMATCH, INCONCLUSIVE }

sealed class CrossTrackerInsight {
    data object InsufficientData : CrossTrackerInsight()
    data class WeightVsIntake(
        val avgDailyDeficitKcal: Int,   // positive = under target (deficit), negative = surplus
        val weightTrendKgPerWeek: Double,
        val weeklyActiveMinutes: Int,
        val agreement: InsightAgreement,
        // Fasting and hydration were tracked but never cross-referenced anywhere
        // (see this section's own doc comment: "five trackers... never cross-
        // reference each other"). Both optional/percent-of-goal rather than
        // folded into [agreement] - a genuine "does eating-under-target,
        // fasting adherence, AND hydration all agree" verdict needs its own
        // reasoned rules, not a guess bolted onto the existing weight-vs-intake
        // one. Null means the user isn't tracking that one (no history/no goal),
        // not "0% adherence".
        val weeklyFastingAdherencePct: Int? = null,
        val weeklyHydrationAdherencePct: Int? = null,
        // R&D audit finding, addressed conservatively rather than with a
        // fabricated 3-way verdict (see this class's own doc comment on why
        // a genuine deficit+fasting+hydration consistency check needs its
        // own reasoned rules): hydration adherence gets exactly one real,
        // physiologically-grounded connection to [agreement] - inconsistent
        // fluid intake is a well-established confounder of week-to-week
        // scale weight (hydration/water-retention swings, not fat-mass
        // change), so a poorly-hydrated week's weight-trend reading is
        // genuinely less reliable, not just a side-by-side coincidence.
        // True only when there's real hydration data to judge by (percent
        // not null) and it's low enough to matter.
        val hydrationReliabilityCaveat: Boolean = false,
    ) : CrossTrackerInsight()
}

// Below these, a normal day-to-day noise floor (scale water-weight swings,
// a single indulgent meal) is common enough that calling it a real signal
// either way would be misleading rather than insightful.
private const val DEFICIT_NOISE_FLOOR_KCAL = 50
private const val WEIGHT_TREND_NOISE_FLOOR_KG = 0.05
// Below this weekly hydration-goal adherence, day-to-day fluid intake swung
// enough that a chunk of the week's scale-weight movement plausibly reflects
// hydration variance rather than the intake-vs-target story alone.
private const val LOW_HYDRATION_ADHERENCE_PCT = 50

fun weeklyCrossTrackerInsight(
    weeklyAvgKcal: Double,
    kcalTarget: Double,
    daysLogged: Int,
    weightTrendKgPerWeek: Double?,
    weeklyActiveMinutes: Int,
    minLoggedDays: Int = 4,
    weeklyFastingAdherencePct: Int? = null,
    weeklyHydrationAdherencePct: Int? = null,
): CrossTrackerInsight {
    if (daysLogged < minLoggedDays || weightTrendKgPerWeek == null || kcalTarget <= 0) return CrossTrackerInsight.InsufficientData
    val avgDeficit = (kcalTarget - weeklyAvgKcal).roundToInt()
    val realDeficit = abs(avgDeficit) > DEFICIT_NOISE_FLOOR_KCAL
    val realTrend = abs(weightTrendKgPerWeek) > WEIGHT_TREND_NOISE_FLOOR_KG
    val agreement = when {
        !realDeficit || !realTrend -> InsightAgreement.INCONCLUSIVE
        avgDeficit > 0 && weightTrendKgPerWeek < 0 -> InsightAgreement.CONSISTENT // eating under target, scale trending down
        avgDeficit < 0 && weightTrendKgPerWeek > 0 -> InsightAgreement.CONSISTENT // eating over target, scale trending up
        else -> InsightAgreement.MISMATCH
    }
    val hydrationCaveat = weeklyHydrationAdherencePct != null && weeklyHydrationAdherencePct < LOW_HYDRATION_ADHERENCE_PCT
    return CrossTrackerInsight.WeightVsIntake(
        avgDeficit, weightTrendKgPerWeek, weeklyActiveMinutes, agreement,
        weeklyFastingAdherencePct, weeklyHydrationAdherencePct, hydrationCaveat,
    )
}
