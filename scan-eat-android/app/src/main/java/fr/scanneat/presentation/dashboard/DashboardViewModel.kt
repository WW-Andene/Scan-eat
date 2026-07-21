package fr.scanneat.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.FastingState
import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.result.defaultMealForHour
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val weightRepo: WeightRepository,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
    private val biolismRepo: BiolismRepository,
    private val activityRepo: ActivityRepository,
    private val customFoodRepo: CustomFoodRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val medicationRepo: MedicationRepository,
) : ViewModel() {

    // LocalDate.now() captured once at property-init time (the previous shape
    // of this ViewModel) would keep every combine() below observing the day
    // this ViewModel happened to be constructed on forever - a session left
    // open across midnight (the single most common way to use a home-screen
    // Dashboard) silently kept showing yesterday's totals/streaks/rollups,
    // and a diary entry logged after midnight wouldn't even count toward
    // "today". Polling + distinctUntilChanged re-subscribes every date-scoped
    // query to the new day exactly when it rolls over, same fix already
    // applied to HydrationViewModel/DiaryViewModel/ActivityViewModel.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    // Combine 5 flows — the 30-day range is now a Flow itself, so
    // DashboardViewModel never issues a suspend DB call on every upstream tick.
    // recentScans is deliberately NOT in this combine: it changes on every
    // single new scan, and merging it here would re-run the entire 30-day
    // weekly rollup / gap-closer / Biolism TDEE recomputation on every scan
    // even though none of that depends on the scan-history list. It's merged
    // in separately below, as a cheap .copy().
    private val heavyState: StateFlow<DashboardUiState> = today.flatMapLatest { date ->
        combine(
            consumptionRepo.observeDay(date),
            consumptionRepo.observeRange(date.minusDays(30), date),
            prefs.profile,
            weightRepo.observeAll(),
            biolismRepo.profile,
        ) { todayData, allEntries, profile, _, bioProfile ->
            // weightRepo.observeAll() (4th param, ignored) is a trigger-only input -
            // it makes this combine re-run when weight changes, but wSummary below
            // is fetched fresh via weightRepo.summarize() rather than threaded
            // through here. Kotlin's typed 5-flow combine() overload removes the
            // Array<*>-indexed unchecked casts the previous form needed.
            Quad(todayData, allEntries, profile, bioProfile)
        }
            // Nested rather than folded into the 5-way combine above (which is
            // already at Kotlin's typed-overload limit) - closeTheGap()/
            // chronicNutrientGaps() below documented their own foodDB param as
            // "FOOD_DB + custom foods" but this ViewModel only ever passed bare
            // FOOD_DB, so a user's own custom foods (e.g. "Lentilles maison",
            // high in iron) could never be suggested to close a real deficit.
            .combine(customFoodRepo.observeAll()) { quad, customFoods -> quad to customFoods }
            .flatMapLatest { (quad, customFoods) ->
                val (todayData, allEntries, profile, bioProfile) = quad
                val foodDb = FOOD_DB + customFoods
                flow {
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

                    emit(DashboardUiState(
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
                    ))
                }
            }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val state: StateFlow<DashboardUiState> = today.flatMapLatest { date ->
        combine(
            heavyState, scanRepo.observeHistory(limit = 20), activityRepo.observeByDate(date),
        ) { s, scans, activity ->
            // In-memory only, both lists already loaded for other purposes above - no
            // new DB query. Matched by the same "barcode when present, else lowercased
            // name" identity ScanRepository.matchKeyFor uses internally for its own
            // score-history dedup.
            fun DiaryEntry.matchKey() = barcode ?: productName.lowercase()
            fun ScanResult.matchKey() = barcode ?: product.name.lowercase()
            val loggedToday = s.todayEntries.mapTo(mutableSetOf()) { it.matchKey() }
            val neverLogged = scans.filter { scan ->
                Instant.ofEpochMilli(scan.scannedAt).atZone(ZoneId.systemDefault()).toLocalDate() == date &&
                    scan.matchKey() !in loggedToday
            }
            s.copy(
                recentScans      = scans,
                neverLoggedScans = neverLogged,
                calorieBalance   = s.calorieBalance?.copy(exerciseKcal = activity.sumOf { it.kcalBurned }),
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // In-app language can differ from the device locale — WeeklyBarsCard's day-of-week
    // labels must follow it explicitly, same as DiaryScreen/WeightScreen/MealPlanScreen.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Dashboard previously showed nutrition + weight only - a user relying on it as
    // their single daily view had no idea they were behind on water, mid-fast, or had
    // an unlogged dose due, despite all three already being tracked elsewhere (behind
    // Journal's Water/Fasting/Treatment tabs). Kept as its own StateFlow rather than
    // folded into the heavy combine above - these are all today-only cheap reads with
    // no dependency on the 30-day window that combine recomputes on every diary edit.
    val otherTrackers: StateFlow<OtherTrackersSnapshot> = today.flatMapLatest { date ->
        combine(
            hydrationRepo.observe(date),
            fastingRepo.state,
            medicationRepo.observeAll(),
            medicationRepo.observeLogByDate(date),
            prefs.profile,
        ) { hydrationMl, fasting, meds, todayLogs, profile ->
            val activeMeds = meds.filter { it.active }
            val takenIds = todayLogs.map { it.medicationId }.toSet()
            OtherTrackersSnapshot(
                hydrationMl     = hydrationMl,
                hydrationGoalMl = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions),
                fastingActive   = fasting?.takeIf { it.isActive },
                medsTakenCount  = activeMeds.count { it.id in takenIds },
                medsActiveCount = activeMeds.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OtherTrackersSnapshot())

    // ── Gap-closer suggestions: previously a dead end ────────────────────────
    // GapCloserCard rendered suggestion chips (e.g. "Lentilles, 80 g") with no
    // way to act on them — tapping did nothing. GapSuggestion.name is always
    // an exact FoodEntry.name from the same merged FOOD_DB+custom-foods list
    // closeTheGap/chronicNutrientGaps were given, so it's looked up directly
    // rather than fuzzy-searched.
    private val _gapLoggedName = MutableStateFlow<String?>(null)
    /** Non-null briefly after a successful log, for a one-shot confirmation snackbar. */
    val gapLoggedName: StateFlow<String?> = _gapLoggedName.asStateFlow()

    // logGapSuggestion/logNeverLoggedScan both guarded their Room write with runCatching
    // to avoid crashing on a disk-full/constraint failure, but neither had a failure
    // path at all - a save that failed produced zero visible effect, so the user
    // couldn't tell their tap hadn't actually logged anything.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed log, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // logGapSuggestion/logNeverLoggedScan both previously had no re-entrancy guard - a fast
    // double-tap (or a second tap landing before the first log's suspend DB write finished)
    // fired the whole function twice, silently creating two duplicate diary entries for the
    // same suggestion/scan. Keyed by the same suggestion.name/scan matchKey a tap is already
    // logically about, so a duplicate tap on the SAME item while it's still in flight is a
    // no-op, but a tap on a DIFFERENT item is never blocked by an unrelated one in progress.
    private val loggingKeys = mutableSetOf<String>()

    fun logGapSuggestion(suggestion: GapSuggestion) {
        if (!loggingKeys.add(suggestion.name)) return
        viewModelScope.launch {
            try {
                // Previously only ever searched raw FOOD_DB - a suggestion built from a
                // user's own custom food (now possible since closeTheGap/chronicNutrientGaps
                // are given FOOD_DB + custom foods) would silently no-op here otherwise.
                val food = (FOOD_DB + customFoodRepo.observeAll().first()).find { it.name == suggestion.name } ?: return@launch
                // Unguarded suspend DB write previously crashed the app on any Room insert
                // failure (disk-full, constraint violation) — ResultViewModel.log() guards
                // its equivalent call the same way.
                runCatching {
                    consumptionRepo.log(
                        DiaryEntry(
                            date        = LocalDate.now(),
                            mealSlot    = defaultMealForHour(LocalTime.now().hour),
                            productName = food.name,
                            barcode     = null,
                            portionG    = suggestion.grams.toDouble(),
                            nutrition   = food.toProduct(suggestion.grams.toDouble()).nutrition,
                            source      = ScanSource.MANUAL,
                        )
                    )
                }.onSuccess { _gapLoggedName.value = food.name }
                    .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
            } finally {
                loggingKeys.remove(suggestion.name)
            }
        }
    }

    fun clearGapLoggedMessage() { _gapLoggedName.value = null }

    /** Logs a never-logged scan straight from its real product/barcode/source — see [DashboardUiState.neverLoggedScans]. */
    fun logNeverLoggedScan(scan: ScanResult, portionG: Double, mealSlot: MealSlot) {
        val key = "scan:" + (scan.barcode ?: scan.product.name.lowercase())
        if (!loggingKeys.add(key)) return
        viewModelScope.launch {
            try {
                runCatching {
                    consumptionRepo.log(
                        DiaryEntry(
                            date        = LocalDate.now(),
                            mealSlot    = mealSlot,
                            productName = scan.product.name,
                            barcode     = scan.barcode,
                            portionG    = portionG,
                            nutrition   = scan.product.nutrition,
                            source      = scan.source,
                            ingredients = scan.product.ingredients,
                        )
                    )
                }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
            } finally {
                loggingKeys.remove(key)
            }
        }
    }

    // Local tuple to carry 4 values cleanly through flatMapLatest
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
