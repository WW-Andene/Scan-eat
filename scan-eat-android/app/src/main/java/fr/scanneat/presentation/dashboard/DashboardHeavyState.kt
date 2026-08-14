package fr.scanneat.presentation.dashboard

import fr.scanneat.data.repository.health.ActivityEntry
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** Bundles the 5 mutually-independent suspend reads awaited in parallel below. */
private data class HeavyStateReads(
    val priorMonthEntries: List<DiaryEntry>,
    val weightSummary: fr.scanneat.data.repository.health.WeightSummary?,
    val weeklyActiveMinutesEntries: List<ActivityEntry>,
    val weeklyFastCompletionsRaw: List<fr.scanneat.data.repository.health.FastCompletion>,
    val weeklyHydrationEntriesRaw: List<Pair<LocalDate, Int>>,
)

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
    todayActivity: List<ActivityEntry>,
    profileId: String = "default",
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
        ?.let { if (bioTdeePreview != null) it.withKcalOverride(bioTdeePreview, profile.goal, currentPregnancyTrimester(profile)) else it }
    val thisWeek  = weeklyRollup(allEntries, date)
    val priorWeek = weeklyRollup(allEntries, date.minusDays(7))
    val thisMonth = monthlyRollup(allEntries, date)
    // allEntries only ever covers the last 30 days (observeRange above) -
    // a prior-30-day comparison needs its own one-shot fetch of days
    // 31-60 ago, not widening the primary reactive window every other
    // computation in this block reads from.
    val priorMonthEnd = date.minusDays(31)
    val weekStart = date.minusDays(6)
    // These five suspend reads are otherwise independent of each other -
    // previously awaited one at a time, so every heavy-state recompute paid
    // for 5 sequential round-trips instead of the slowest of the 5 in parallel.
    val (priorMonthEntries, wSummary, weeklyActiveMinutesEntries, weeklyFastCompletionsRaw, weeklyHydrationEntriesRaw) = coroutineScope {
        val priorMonthEntriesD = async { consumptionRepo.observeRange(priorMonthEnd.minusDays(29), priorMonthEnd, profileId).first() }
        val wSummaryD = async { weightRepo.summarize(30, profileId) }
        val activeMinutesD = async { activityRepo.getRange(date.minusDays(6), date, profileId) }
        val fastingD = async { fastingRepo.history(profileId).first() }
        val hydrationD = async { hydrationRepo.observeAll(profileId).first() }
        HeavyStateReads(priorMonthEntriesD.await(), wSummaryD.await(), activeMinutesD.await(), fastingD.await(), hydrationD.await())
    }
    val monthDelta = monthOverMonthDelta(thisMonth, monthlyRollup(priorMonthEntries, priorMonthEnd))
    val forecast  = if (wSummary != null && profile.goalWeightKg != null)
        weightForecast(wSummary.latestKg, profile.goalWeightKg, wSummary.trendKgPerWeek)
    else WeightForecast.InsufficientData
    // User-requested: an outdoor activity is a real (if rough) vitamin D source
    // via sun exposure - see VITD_OUTDOOR_UG's own doc comment. [todayActivity]
    // now comes in as a param, computed by the caller from a reactive
    // ActivityRepository Flow rather than fetched here as a one-shot read - this
    // suspend function's own inputs (todayData, allEntries, profile, bioProfile)
    // were already the combine()'s recompute triggers, but activityRepo wasn't
    // one of them, so logging a new outdoor activity never actually re-ran this
    // function; the credit only appeared next time some other input happened to
    // change (e.g. logging food). Also doubles as the source for exerciseKcal/
    // extraExerciseKcal below, replacing a second, separate activityRepo
    // subscription DashboardViewModel previously needed just for that.
    val totalsWithOutdoorVitD = todayData.totals.withOutdoorVitD(todayActivity.any { it.wasOutdoors })
    val gaps = if (targets != null && todayData.entries.isNotEmpty())
        closeTheGap(totalsWithOutdoorVitD, targets, foodDb, todaysEntries = todayData.entries)
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
    val weeklyActiveMinutes = weeklyActiveMinutesEntries.sumOf { it.minutes }
    // "five trackers... never cross-reference each other" (see
    // weeklyCrossTrackerInsight's own doc comment) - fasting/hydration
    // were tracked but excluded from this insight entirely. Fasting
    // adherence mirrors FastingScreen's own "successCount/completed.size"
    // convention (% of *attempted* fasts that hit target, not % of the
    // week, since fasting is often deliberately not a daily practice) -
    // hydration is expected daily, so it divides by the fixed 7-day week.
    val weeklyFastCompletions = weeklyFastCompletionsRaw.filter { c ->
        runCatching { LocalDate.parse(c.date) }.getOrNull()?.let { it in weekStart..date } == true
    }
    // Rounded, not truncated - plain integer division previously biased both
    // adherence percentages down, which could hide a real caveat right at
    // the LOW_FASTING_ADHERENCE_PCT/LOW_HYDRATION_ADHERENCE_PCT boundary
    // (see weeklyCrossTrackerInsight's own thresholds).
    val weeklyFastingAdherencePct = weeklyFastCompletions.takeIf { it.isNotEmpty() }
        ?.let { (it.count { c -> c.reached } * 100.0 / it.size).roundToInt() }
    val weeklyHydrationEntries = weeklyHydrationEntriesRaw.filter { (d, _) -> d in weekStart..date }
    val hydrationGoal = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions, weightKg = profile.weightKg)
    val weeklyHydrationAdherencePct = weeklyHydrationEntries.takeIf { it.isNotEmpty() && hydrationGoal > 0 }
        ?.let { entries -> (entries.count { (_, ml) -> ml >= hydrationGoal } * 100.0 / 7).roundToInt() }
    val crossInsight = weeklyCrossTrackerInsight(
        weeklyAvgKcal         = thisWeek.avg.kcal,
        kcalTarget            = targets?.kcal ?: 0.0,
        daysLogged            = thisWeek.daysLogged,
        weightTrendKgPerWeek  = wSummary?.trendKgPerWeek,
        weeklyActiveMinutes   = weeklyActiveMinutes,
        weeklyFastingAdherencePct   = weeklyFastingAdherencePct,
        weeklyHydrationAdherencePct = weeklyHydrationAdherencePct,
    )

    // User-requested: are logged activities connected to metabolism? Previously
    // no - exerciseKcal was purely informational (see CalorieBalance's own
    // prior doc comment on why: double-counting risk against the declared PAL
    // already baked into tdee). extraExerciseKcal() closes that gap the way
    // the user asked - only the excess beyond what the declared activity
    // level already implies for a typical day is added to today's budget, see
    // its own doc comment for the full rationale.
    val exerciseKcalToday = todayActivity.sumOf { it.kcalBurned }
    val extraKcal = extraExerciseKcal(profile.activityLevel, exerciseKcalToday)
    val calorieBalance = targets?.kcal?.let {
        CalorieBalance(
            kcalIn          = todayData.totals.energyKcal,
            tdee            = it,
            tdeeFromBiolism = bioTdeePreview != null,
            net             = todayData.totals.energyKcal - it - extraKcal,
            exerciseKcal    = exerciseKcalToday,
            extraExerciseKcal = extraKcal,
        )
    }

    // allEntries is only ever a 30-day window (observeRange above) - passing
    // it to logStreakDays/longestLogStreak silently capped both at 30 even
    // when the user's real streak/record ran longer. getAllLoggedDates() is
    // a cheap DISTINCT-date query (no row hydration), so this stays correct
    // no matter how long the actual streak or logging history is.
    val loggedDates = consumptionRepo.getAllLoggedDates(profileId)

    return DashboardUiState(
        todayTotals    = totalsWithOutdoorVitD,
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
        foodDiversity  = foodDiversityScore(allEntries, since = date.minusDays(6), until = date),
    )
}
