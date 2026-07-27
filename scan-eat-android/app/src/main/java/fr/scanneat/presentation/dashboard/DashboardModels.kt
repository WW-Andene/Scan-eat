package fr.scanneat.presentation.dashboard

import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.FastingState
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*

/**
 * The single, merged calorie-balance readout (real Diary intake vs TDEE).
 * TDEE prefers Biolism's richer estimate when a valid Biolism profile exists,
 * falling back to the main Profile's PAL-based TDEE otherwise.
 */
data class CalorieBalance(
    val kcalIn: Double,
    val tdee: Double,
    val tdeeFromBiolism: Boolean,
    val net: Double,
    // Logged Activité kcal for today — previously computed and stored
    // (ActivityRepository.dailyBurned) but never read anywhere near the
    // Dashboard, so a logged workout had zero visible effect on the day's
    // calorie readout. Kept informational rather than folded into tdee/net:
    // Biolism's TDEE is already computed off a general PAL/activity-level
    // input, so silently adding logged-workout kcal on top risks double-
    // counting the same activity twice rather than showing something new.
    val exerciseKcal: Int = 0,
)

data class DashboardUiState(
    val todayTotals: ConsumedNutrition = ConsumedNutrition.ZERO,
    val targets: DailyTargets? = null,
    val calorieBalance: CalorieBalance? = null,
    val streak: Int = 0,
    // longestLogStreak() was fully built (scans full history for the longest-
    // ever unbroken logging run) but had zero callers - a user who logged 30
    // days straight last month and then missed a day saw that record vanish
    // entirely, since only the *current* streak (logStreakDays) was ever shown.
    val longestStreak: Int = 0,
    val weekly: RollupResult? = null,
    // monthlyRollup() was fully implemented (same shape as weeklyRollup, which
    // already has a card) but had zero callers - there was no way to see
    // anything past a single week anywhere on the Dashboard.
    val monthly: RollupResult? = null,
    val weekDelta: WeekOverWeekDelta? = null,
    // monthOverMonthDelta() existed alongside weekOverWeekDelta() but had no
    // Dashboard caller - MonthlyTrendCard only ever plotted 30 daily bars
    // against a flat target line, no delta number the way WeekDeltaCard has.
    val monthDelta: WeekOverWeekDelta? = null,
    val weightSummary: fr.scanneat.data.repository.health.WeightSummary? = null,
    val weightForecast: WeightForecast = WeightForecast.InsufficientData,
    val gapSuggestions: List<GapEntry> = emptyList(),
    val chronicGaps: List<ChronicGap> = emptyList(),
    val recentScans: List<ScanResult> = emptyList(),
    /** Today's diary entries - kept around purely to derive [neverLoggedScans] below. */
    val todayEntries: List<DiaryEntry> = emptyList(),
    // Cross-references this week's calorie deficit/surplus against the real
    // weight-trend direction - see weeklyCrossTrackerInsight()'s own doc
    // comment for why this didn't exist anywhere before.
    val crossInsight: CrossTrackerInsight = CrossTrackerInsight.InsufficientData,
    // DashboardViewModel already injected both ConsumptionRepository and
    // ScanRepository but never cross-referenced them - the app's core loop
    // (scan -> decide -> log) had no follow-through nudge, so a user who scans
    // several products at the store and only logs some of them got no signal
    // that the rest were never actually recorded to their diary.
    val neverLoggedScans: List<ScanResult> = emptyList(),
)

/** Today-only glance snapshot of the trackers Dashboard otherwise never surfaces - see [DashboardViewModel.otherTrackers]. */
data class OtherTrackersSnapshot(
    val hydrationMl: Int = 0,
    val hydrationGoalMl: Int = HYD_DEFAULT_GOAL_ML,
    val fastingActive: FastingState? = null,
    val medsTakenCount: Int = 0,
    val medsActiveCount: Int = 0,
)
