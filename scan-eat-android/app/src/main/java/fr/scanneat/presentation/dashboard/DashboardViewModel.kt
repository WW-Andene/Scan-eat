package fr.scanneat.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.result.defaultMealForHour
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
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
    val weightSummary: fr.scanneat.data.repository.health.WeightSummary? = null,
    val weightForecast: WeightForecast = WeightForecast.InsufficientData,
    val gapSuggestions: List<GapEntry> = emptyList(),
    val chronicGaps: List<ChronicGap> = emptyList(),
    val recentScans: List<ScanResult> = emptyList(),
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
) : ViewModel() {

    // Combine 4 flows with the type-safe 4-parameter lambda (not the array form).
    // flatMapLatest wraps the suspend calls cleanly.
    private val from = LocalDate.now().minusDays(30)

    // Combine 5 flows — the 30-day range is now a Flow itself, so
    // DashboardViewModel never issues a suspend DB call on every upstream tick.
    // recentScans is deliberately NOT in this combine: it changes on every
    // single new scan, and merging it here would re-run the entire 30-day
    // weekly rollup / gap-closer / Biolism TDEE recomputation on every scan
    // even though none of that depends on the scan-history list. It's merged
    // in separately below, as a cheap .copy().
    private val heavyState: StateFlow<DashboardUiState> = combine(
        consumptionRepo.observeDay(LocalDate.now()),
        consumptionRepo.observeRange(from, LocalDate.now()),
        prefs.profile,
        weightRepo.observeAll(),
        biolismRepo.profile,
    ) { today, allEntries, profile, _, bioProfile ->
        // weightRepo.observeAll() (4th param, ignored) is a trigger-only input -
        // it makes this combine re-run when weight changes, but wSummary below
        // is fetched fresh via weightRepo.summarize() rather than threaded
        // through here. Kotlin's typed 5-flow combine() overload removes the
        // Array<*>-indexed unchecked casts the previous form needed.
        Quad(today, allEntries, profile, bioProfile)
    }
        .flatMapLatest { (today, allEntries, profile, bioProfile) ->
            flow {
                // WeeklyBarsCard/gap engines below all read targets.kcal directly, but
                // only calorieBalance further down ever substituted the richer
                // Biolism TDEE for it - so this same screen could show a Biolism-based
                // "1850/2400 kcal today" balance right next to a WeeklyBarsCard target
                // line still drawn from the plain PAL-based estimate. Overriding once,
                // at the source, keeps every consumer of `targets` in agreement.
                val bioTdeePreview = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
                val targets = (if (hasMinimalProfile(profile)) dailyTargets(profile) else null)
                    ?.let { if (bioTdeePreview != null) it.copy(kcal = bioTdeePreview) else it }
                val thisWeek  = weeklyRollup(allEntries, LocalDate.now())
                val priorWeek = weeklyRollup(allEntries, LocalDate.now().minusDays(7))
                val thisMonth = monthlyRollup(allEntries, LocalDate.now())
                val wSummary  = weightRepo.summarize(30)
                val forecast  = if (wSummary != null && profile.goalWeightKg != null)
                    weightForecast(wSummary.latestKg, profile.goalWeightKg, wSummary.trendKgPerWeek)
                else WeightForecast.InsufficientData
                val gaps = if (targets != null && today.entries.isNotEmpty())
                    closeTheGap(today.totals, targets, FOOD_DB)
                else emptyList()
                // chronicNutrientGaps() was fully built (7-day recurring-deficit
                // scan) but never called from any ViewModel - closeTheGap() above
                // only ever looks at today, so a real ongoing shortfall (e.g. low
                // fiber 5 of the last 7 days) never surfaced unless it also
                // happened to be true today.
                val chronic = if (targets != null) chronicNutrientGaps(allEntries, targets, FOOD_DB) else emptyList()

                val calorieBalance = targets?.kcal?.let {
                    CalorieBalance(
                        kcalIn          = today.totals.energyKcal,
                        tdee            = it,
                        tdeeFromBiolism = bioTdeePreview != null,
                        net             = today.totals.energyKcal - it,
                    )
                }

                emit(DashboardUiState(
                    todayTotals    = today.totals,
                    targets        = targets,
                    calorieBalance = calorieBalance,
                    streak         = logStreakDays(allEntries),
                    longestStreak  = longestLogStreak(allEntries),
                    weekly         = thisWeek,
                    monthly        = thisMonth,
                    weekDelta      = weekOverWeekDelta(thisWeek, priorWeek),
                    weightSummary  = wSummary,
                    weightForecast = forecast,
                    gapSuggestions = gaps,
                    chronicGaps    = chronic,
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val state: StateFlow<DashboardUiState> = combine(
        heavyState, scanRepo.observeHistory(limit = 20), activityRepo.observeByDate(LocalDate.now()),
    ) { s, scans, activity -> s.copy(recentScans = scans, calorieBalance = s.calorieBalance?.copy(exerciseKcal = activity.sumOf { it.kcalBurned })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // In-app language can differ from the device locale — WeeklyBarsCard's day-of-week
    // labels must follow it explicitly, same as DiaryScreen/WeightScreen/MealPlanScreen.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // ── Gap-closer suggestions: previously a dead end ────────────────────────
    // GapCloserCard rendered suggestion chips (e.g. "Lentilles, 80 g") with no
    // way to act on them — tapping did nothing. GapSuggestion.name is always
    // an exact FOOD_DB entry name (see chronicNutrientGaps/closeTheGap, which
    // build it from FoodEntry.name), so it's looked up directly rather than
    // fuzzy-searched.
    private val _gapLoggedName = MutableStateFlow<String?>(null)
    /** Non-null briefly after a successful log, for a one-shot confirmation snackbar. */
    val gapLoggedName: StateFlow<String?> = _gapLoggedName.asStateFlow()

    fun logGapSuggestion(suggestion: GapSuggestion) {
        val food = FOOD_DB.find { it.name == suggestion.name } ?: return
        viewModelScope.launch {
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
            _gapLoggedName.value = food.name
        }
    }

    fun clearGapLoggedMessage() { _gapLoggedName.value = null }

    // Local tuple to carry 4 values cleanly through flatMapLatest
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
