package fr.scanneat.presentation.biolism.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.health.HYD_DEFAULT_GOAL_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.WeightEntry
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.domain.engine.dashboard.RollupResult
import fr.scanneat.domain.engine.dashboard.customRollup
import fr.scanneat.domain.model.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Trailing window every Evolution-tab card charts, so the tab tells one coherent "last 3 months" story. */
internal const val EVOLUTION_WINDOW_DAYS = 90

@HiltViewModel
class EvolutionViewModel @Inject constructor(
    biolismRepo: BiolismRepository,
    private val weightRepo: WeightRepository,
    private val hydrationRepo: HydrationRepository,
    consumptionRepo: ConsumptionRepository,
    prefs: UserPreferences,
) : ViewModel() {

    val language = prefs.language.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val profile  = biolismRepo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())
    val mainProfile = prefs.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())
    val useImperial = prefs.useImperialWeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val sessions = biolismRepo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val windowStart get() = LocalDate.now().minusDays((EVOLUTION_WINDOW_DAYS - 1).toLong())

    // ── Weight — real, unbounded Room history, same source WeightScreen charts ──
    val weightEntries: StateFlow<List<WeightEntry>> = weightRepo.observeAll()
        .map { all -> all.filter { !it.date.isBefore(windowStart) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Body composition — Fat%/lean mass have no dated history of their own
    // (BiolismProfile overwrites in place, MetabolicResult is recomputed live
    // every tick), so this recomputes computeMetabolics() for each historical
    // weight entry using that date's real weight against the CURRENT waist/
    // hip/neck/height — an estimate ("what your body-fat% would be at that
    // weight, given your measurements today"), not a measured per-day value.
    // Immediately useful from day one instead of an empty chart, and clearly
    // captioned as such in BodyCompositionEvolutionCard. ──
    val bodyCompositionSeries: StateFlow<List<BodyCompPoint>> = combine(profile, weightEntries) { p, entries ->
        if (!p.isValid) return@combine emptyList()
        entries.mapNotNull { entry ->
            val m = BiolismEngine.computeMetabolics(p.copy(weightKg = entry.weightKg)) ?: return@mapNotNull null
            BodyCompPoint(entry.date, m.bfPct, m.ffm)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Hormones — same "no dated history" gap as body composition, same fix:
    // recompute a non-fasted baseline (ketoHours=0, fastingHours=0) per
    // historical weight entry, isolating how the estimate shifts with body
    // composition alone, independent of any given day's actual fasting state
    // (which isn't recorded historically). "Today" uses the live current
    // profile the same way — a baseline, not the live Tracker/Data-tab value. ──
    val hormonesToday: StateFlow<HormoneResult?> = profile.map { p ->
        if (!p.isValid) return@map null
        val m = BiolismEngine.computeMetabolics(p) ?: return@map null
        BiolismEngine.computeHormones(p, m, 0.0, 0.0, false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hormoneTrends: StateFlow<HormoneTrends> = combine(profile, weightEntries) { p, entries ->
        if (!p.isValid) return@combine HormoneTrends()
        val cortisol = mutableListOf<Pair<LocalDate, Double>>()
        val sexPrimary = mutableListOf<Pair<LocalDate, Double>>()
        entries.forEach { entry ->
            val ep = p.copy(weightKg = entry.weightKg)
            val m = BiolismEngine.computeMetabolics(ep) ?: return@forEach
            val h = BiolismEngine.computeHormones(ep, m, 0.0, 0.0, false) ?: return@forEach
            cortisol += entry.date to h.cortisol.value
            when (p.sex) {
                BiolismSex.MALE   -> sexPrimary += entry.date to h.testosterone.value
                BiolismSex.FEMALE -> sexPrimary += entry.date to h.estradiol.value
                BiolismSex.NOT_SPECIFIED -> {}
            }
        }
        val kind = when (p.sex) {
            BiolismSex.MALE          -> SexPrimaryHormone.TESTOSTERONE
            BiolismSex.FEMALE        -> SexPrimaryHormone.ESTRADIOL
            BiolismSex.NOT_SPECIFIED -> null
        }
        HormoneTrends(cortisol, sexPrimary, kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HormoneTrends())

    // ── Hydration — real per-day history (capped at 90 days at the source) ──
    private val _hydrationHistory = MutableStateFlow<List<Pair<LocalDate, Int>>>(emptyList())
    val hydrationHistory: StateFlow<List<Pair<LocalDate, Int>>> = _hydrationHistory.asStateFlow()
    val hydrationGoalMl: StateFlow<Int> = mainProfile.map { p ->
        hydrationRepo.goalMl(p.sex, p.activityLevel, p.healthConditions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HYD_DEFAULT_GOAL_ML)

    // ── Macro intake — real per-day history via the Diary, same source Dashboard uses ──
    val macroRollup: StateFlow<RollupResult?> = consumptionRepo
        .observeRange(windowStart, LocalDate.now())
        .map { entries -> customRollup(entries, LocalDate.now(), EVOLUTION_WINDOW_DAYS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            runCatching { _hydrationHistory.value = hydrationRepo.exportAll().filter { !it.first.isBefore(windowStart) } }
                .onFailure { e -> if (e is CancellationException) throw e }
        }
    }
}

data class BodyCompPoint(val date: LocalDate, val bfPct: Double, val ffmKg: Double)

enum class SexPrimaryHormone { TESTOSTERONE, ESTRADIOL }

data class HormoneTrends(
    val cortisol: List<Pair<LocalDate, Double>> = emptyList(),
    val sexPrimary: List<Pair<LocalDate, Double>> = emptyList(),
    val sexPrimaryKind: SexPrimaryHormone? = null,
)
