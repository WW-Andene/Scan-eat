package fr.scanneat.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.scan.ScanRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import fr.scanneat.domain.model.DiaryEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class DashboardUiState(
    val todayTotals: ConsumedNutrition = ConsumedNutrition.ZERO,
    val targets: DailyTargets? = null,
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
) : ViewModel() {

    // Combine 4 flows with the type-safe 4-parameter lambda (not the array form).
    // flatMapLatest wraps the suspend calls cleanly.
    private val from = LocalDate.now().minusDays(30)

    // Combine 5 flows — the 30-day range is now a Flow itself,
    // so DashboardViewModel never issues a suspend DB call on every upstream tick.
    val state: StateFlow<DashboardUiState> = combine(
        consumptionRepo.observeDay(LocalDate.now()),
        consumptionRepo.observeRange(from, LocalDate.now()),
        prefs.profile,
        weightRepo.observeAll(),
        scanRepo.observeHistory(limit = 20),
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val today      = args[0] as DailySummary
        val allEntries = args[1] as List<DiaryEntry>
        val profile    = args[2] as Profile
        val scans      = args[4] as List<ScanResult>
        Quadruple(today, allEntries, profile, scans)
    }
        .flatMapLatest { (today, allEntries, profile, scans) ->
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

                emit(DashboardUiState(
                    todayTotals    = today.totals,
                    targets        = targets,
                    streak         = logStreakDays(allEntries),
                    weekly         = thisWeek,
                    weekDelta      = weekOverWeekDelta(thisWeek, priorWeek),
                    weightSummary  = wSummary,
                    weightForecast = forecast,
                    gapSuggestions = gaps,
                    recentScans    = scans,
                ))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    // Local Quadruple to carry 4 values cleanly through flatMapLatest
    private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
