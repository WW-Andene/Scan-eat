package fr.scanneat.presentation.dashboard

import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The full per-tick computation behind [DashboardViewModel.heavyState] - split out of the
 * ViewModel so the flatMapLatest wiring in that file stays readable; this is a pure
 * (aside from the suspend repo reads it was already making in-place) continuation of that
 * combine/flatMapLatest chain, moved verbatim rather than restructured.
 */
internal suspend fun buildHeavyDashboardState(
    date: LocalDate,
    todayData: DailySummary,
    allEntries: List<DiaryEntry>,
    profile: Profile,
    bioProfile: BiolismProfile,
    foodDb: List<FoodEntry>,
    consumptionRepo: ConsumptionRepository,
    weightRepo: WeightRepository,
    activityRepo: ActivityRepository,
    fastingRepo: FastingRepository,
    hydrationRepo: HydrationRepository,
): DashboardUiState {
    // WeeklyBarsCard/gap engines below all read targets.kcal directly, but
    // only calorieBalance further down ever substituted the richer
    // Biolism TDEE for it - so this same screen could show a Biolism-based
    // "1850/2400 kcal today" balance right next to a WeeklyBarsCard target
    // line still drawn from the plain PAL-based estimate. Overriding once,
    // at the source, keeps every consumer of `targets` in agreement.
    val bioTdeePreview = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
    // withKcalOverride rescales fat/carbs targets onto the Biolism kcal too -
    // a plain kcal swap left TodayMacroCard's macro rings computed from the
    // stale profile-only kcal, so they no longer summed to the balance above.
    val targets = (if (hasMinimalProfile(profile)) dailyTargets(profile) else null)
        ?.let { if (bioTdeePreview != null) it.withKcalOverride(bioTdeePreview, profile.goal) else it }
    val thisWeek  = weeklyRollup(allEntries, date)
    val priorWeek = weeklyRollup(allEntries, date.minusDays(7))
    val thisMonth = monthlyRollup(allEntries, date)
    // allEntries only ever covers the last 30 days (observeRange above) -
    // a prior-30-day comparison needs its own one-shot fetch of days
    // 31-60 ago, not widening the primary reactive window every other
    // computation in this block reads from.
    val priorMonthEnd = date.minusDays(31)
    val priorMonthEntries = consumptionRepo.observeRange(priorMonthEnd.minusDays(29), priorMonthEnd).first()
    val monthDelta = monthOverMonthDelta(thisMonth, monthlyRollup(priorMonthEntries, priorMonthEnd))
    val wSummary  = weightRepo.summarize(30)
    val forecast  = if (wSummary != null && profile.goalWeightKg != null)
        weightForecast(wSummary.latestKg, profile.goalWeightKg, wSummary.trendKgPerWeek)
    else WeightForecast.InsufficientData
    val gaps = if (targets != null && todayData.entries.isNotEmpty())
        closeTheGap(todayData.totals, targets, foodDb)
    else emptyList()
    // chronicNutrientGaps() was fully built (7-day recurring-deficit
    // scan) but never called from any ViewModel - closeTheGap() above
    // only ever looks at today, so a real ongoing shortfall (e.g. low
    // fiber 5 of the last 7 days) never surfaced unless it also
    // happened to be true today.
    val chronic = if (targets != null) chronicNutrientGaps(allEntries, targets, foodDb) else emptyList()

    // Weekly active minutes for the cross-tracker insight below - a
    // fresh range query (not the single-day observeByDate used elsewhere
    // on Dashboard) since no 7-day activity window was already loaded here.
    val weeklyActiveMinutes = activityRepo.getRange(date.minusDays(6), date).sumOf { it.minutes }
    val weekStart = date.minusDays(6)
    // "five trackers... never cross-reference each other" (see
    // weeklyCrossTrackerInsight's own doc comment) - fasting/hydration
    // were tracked but excluded from this insight entirely. Fasting
    // adherence mirrors FastingScreen's own "successCount/completed.size"
    // convention (% of *attempted* fasts that hit target, not % of the
    // week, since fasting is often deliberately not a daily practice) -
    // hydration is expected daily, so it divides by the fixed 7-day week.
    val weeklyFastCompletions = fastingRepo.history.first().filter { c ->
        runCatching { LocalDate.parse(c.date) }.getOrNull()?.let { it in weekStart..date } == true
    }
    val weeklyFastingAdherencePct = weeklyFastCompletions.takeIf { it.isNotEmpty() }
        ?.let { it.count { c -> c.reached } * 100 / it.size }
    val weeklyHydrationEntries = hydrationRepo.exportAll().filter { (d, _) -> d in weekStart..date }
    val hydrationGoal = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions)
    val weeklyHydrationAdherencePct = weeklyHydrationEntries.takeIf { it.isNotEmpty() && hydrationGoal > 0 }
        ?.let { entries -> entries.count { (_, ml) -> ml >= hydrationGoal } * 100 / 7 }
    val crossInsight = weeklyCrossTrackerInsight(
        weeklyAvgKcal         = thisWeek.avg.kcal,
        kcalTarget            = targets?.kcal ?: 0.0,
        daysLogged            = thisWeek.daysLogged,
        weightTrendKgPerWeek  = wSummary?.trendKgPerWeek,
        weeklyActiveMinutes   = weeklyActiveMinutes,
        weeklyFastingAdherencePct   = weeklyFastingAdherencePct,
        weeklyHydrationAdherencePct = weeklyHydrationAdherencePct,
    )

    val calorieBalance = targets?.kcal?.let {
        CalorieBalance(
            kcalIn          = todayData.totals.energyKcal,
            tdee            = it,
            tdeeFromBiolism = bioTdeePreview != null,
            net             = todayData.totals.energyKcal - it,
        )
    }

    // allEntries is only ever a 30-day window (observeRange above) - passing
    // it to logStreakDays/longestLogStreak silently capped both at 30 even
    // when the user's real streak/record ran longer. getAllLoggedDates() is
    // a cheap DISTINCT-date query (no row hydration), so this stays correct
    // no matter how long the actual streak or logging history is.
    val loggedDates = consumptionRepo.getAllLoggedDates()

    return DashboardUiState(
        todayTotals    = todayData.totals,
        targets        = targets,
        calorieBalance = calorieBalance,
        streak         = logStreakDays(loggedDates, date),
        longestStreak  = longestLogStreak(loggedDates),
        weekly         = thisWeek,
        monthly        = thisMonth,
        weekDelta      = weekOverWeekDelta(thisWeek, priorWeek),
        monthDelta     = monthDelta,
        weightSummary  = wSummary,
        weightForecast = forecast,
        gapSuggestions = gaps,
        chronicGaps    = chronic,
        todayEntries   = todayData.entries,
        crossInsight   = crossInsight,
    )
}
