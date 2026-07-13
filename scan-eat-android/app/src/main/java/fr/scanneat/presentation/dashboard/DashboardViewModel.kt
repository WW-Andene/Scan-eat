package fr.scanneat.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.domain.engine.biolism.BiolismEngine
import fr.scanneat.domain.engine.biolism.computeMetabolics
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
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
)

data class DashboardUiState(
    val todayTotals: ConsumedNutrition = ConsumedNutrition.ZERO,
    val targets: DailyTargets? = null,
    val calorieBalance: CalorieBalance? = null,
    val streak: Int = 0,
    val weekly: RollupResult? = null,
    val weekDelta: WeekOverWeekDelta? = null,
    val weightSummary: fr.scanneat.data.repository.health.WeightSummary? = null,
    val weightForecast: WeightForecast = WeightForecast.InsufficientData,
    val gapSuggestions: List<GapEntry> = emptyList(),
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
                val targets   = if (hasMinimalProfile(profile)) dailyTargets(profile) else null
                val thisWeek  = weeklyRollup(allEntries, LocalDate.now())
                val priorWeek = weeklyRollup(allEntries, LocalDate.now().minusDays(7))
                val wSummary  = weightRepo.summarize(30)
                val forecast  = if (wSummary != null && profile.goalWeightKg != null)
                    weightForecast(wSummary.latestKg, profile.goalWeightKg, wSummary.trendKgPerWeek)
                else WeightForecast.InsufficientData
                val gaps = if (targets != null && today.entries.isNotEmpty())
                    closeTheGap(today.totals, targets, FOOD_DB)
                else emptyList()

                val bioTdee = if (bioProfile.isValid) BiolismEngine.computeMetabolics(bioProfile)?.tdeeDay else null
                val tdee = bioTdee ?: targets?.kcal
                val calorieBalance = tdee?.let {
                    CalorieBalance(
                        kcalIn          = today.totals.energyKcal,
                        tdee            = it,
                        tdeeFromBiolism = bioTdee != null,
                        net             = today.totals.energyKcal - it,
                    )
                }

                emit(DashboardUiState(
                    todayTotals    = today.totals,
                    targets        = targets,
                    calorieBalance = calorieBalance,
                    streak         = logStreakDays(allEntries),
                    weekly         = thisWeek,
                    weekDelta      = weekOverWeekDelta(thisWeek, priorWeek),
                    weightSummary  = wSummary,
                    weightForecast = forecast,
                    gapSuggestions = gaps,
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    val state: StateFlow<DashboardUiState> = combine(
        heavyState, scanRepo.observeHistory(limit = 20),
    ) { s, scans -> s.copy(recentScans = scans) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // In-app language can differ from the device locale — WeeklyBarsCard's day-of-week
    // labels must follow it explicitly, same as DiaryScreen/WeightScreen/MealPlanScreen.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Local tuple to carry 4 values cleanly through flatMapLatest
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
