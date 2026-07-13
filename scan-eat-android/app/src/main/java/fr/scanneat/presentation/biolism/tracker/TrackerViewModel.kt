package fr.scanneat.presentation.biolism.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.domain.model.MS_PER_HOUR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    private val repo: BiolismRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    // ── Profile ───────────────────────────────────────────────────────────────
    val profile: StateFlow<BiolismProfile> = repo.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())

    // BiolismProfile (above) is Biolism's own sex/age/weight/... profile and has
    // no healthConditions field — the app-wide Profile (UserPreferences) is the
    // only place diabetes/pregnancy/kidney_disease/etc. live. Extended fasting
    // and ketogenic states carry real, documented risk for some of these
    // conditions (see HealthConditionCaution.kt), the same personalization gap
    // the food-scoring/hint-panel path already closed.
    val healthConditions: StateFlow<Set<String>> = prefs.profile
        .map { it.healthConditions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // ── Timer state from DataStore (source of truth on resume) ───────────────
    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // ── Live elapsed — updated by coroutine ticker, not DataStore ─────────────
    private val _elapsedMs   = MutableStateFlow(0L)
    private val _ketoElapsedMs = MutableStateFlow(0L)
    val elapsedMs:     StateFlow<Long> = _elapsedMs.asStateFlow()
    val ketoElapsedMs: StateFlow<Long> = _ketoElapsedMs.asStateFlow()

    // ── Manual HR ────────────────────────────────────────────────────────────
    val manualHR: StateFlow<Int?> = repo.manualHR
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Precision toggle ─────────────────────────────────────────────────────
    private val _heroPrecision = MutableStateFlow(false)
    val heroPrecision: StateFlow<Boolean> = _heroPrecision.asStateFlow()

    // ── Rate display: total kcal vs kcal/sec ─────────────────────────────────
    private val _showKcalPerSec = MutableStateFlow(false)
    val showKcalPerSec: StateFlow<Boolean> = _showKcalPerSec.asStateFlow()

    // ── Saved confirmation ────────────────────────────────────────────────────
    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    // Fix 8: live metabolic state derived every 100ms in ViewModel, not in composition
    val liveMetabolic: StateFlow<LiveMetabolicState> = combine(
        profile, _timerState, _elapsedMs, _ketoElapsedMs,
    ) { p, s, elapsedMs, ketoMs ->
        computeMetabolicSnapshot(p, s, elapsedMs, ketoMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveMetabolicState())

    private fun computeMetabolicSnapshot(p: BiolismProfile, s: TimerState, elapsedMs: Long, ketoMs: Long): LiveMetabolicState {
        if (!p.isValid) return LiveMetabolicState()
        val elapsedSec  = elapsedMs / 1000.0
        val ketoHours   = ketoMs / 3_600_000.0
        val fastingHours = s.fastingHours
        val ctxHours    = if (s.ketosisOn) ketoHours else fastingHours.coerceAtLeast(0.0)
        val npRq        = if (s.ketosisOn) BiolismEngine.computeKetoRQ(ketoHours, s.ketoAdapted) else 0.858
        val sub         = BiolismEngine.computeSubstrates(npRq, ctxHours)
        val m           = BiolismEngine.computeMetabolics(p, npRq, ctxHours, s.ketoAdapted) ?: return LiveMetabolicState()

        val kcalTotal   = m.kcalSec * elapsedSec
        val fatKcal     = kcalTotal * sub.fatFrac
        val glycoKcal   = if (s.ketosisOn) kotlin.math.min(kcalTotal * sub.carbFrac, GLYCOGEN_KCAL) else 0.0
        val glycoFrac   = if (s.ketosisOn) kotlin.math.min(1.0, glycoKcal / GLYCOGEN_KCAL) else 0.0
        val kcalPerKgFat = if (s.ketosisOn) 7700.0 + (9300.0 - 7700.0) * glycoFrac else 7700.0
        val fatLostKg   = fatKcal / kcalPerKgFat
        val glycoLostKg = (glycoKcal / 4.0) * (1.0 + WATER_PER_GLYC) / 1000.0
        val phase       = if (s.ketosisOn) BiolismEngine.ketoPhaseInfo(ketoHours, s.ketoAdapted) else null

        return LiveMetabolicState(
            npRq        = npRq,
            fatFrac     = sub.fatFrac,
            carbFrac    = sub.carbFrac,
            protFrac    = sub.protFrac,
            kcalSec     = m.kcalSec,
            watts       = m.watts,
            kcalTotal   = kcalTotal,
            fatLostKg   = fatLostKg,
            glycoLostKg = glycoLostKg,
            liveWeightKg = p.weightKg - fatLostKg - glycoLostKg,
            phase       = phase,
            bmrDay      = m.bmrDay,
            tdeeDay     = m.tdeeDay,
        )
    }


    private var tickJob: Job? = null

    init {
        // Restore from DataStore on cold start
        viewModelScope.launch {
            val s = repo.timerState.first()
            _timerState.value = s
            _elapsedMs.value    = s.elapsedMs
            _ketoElapsedMs.value = s.ketoElapsedMs
            if (s.running) startTicker()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Timer control
    // ─────────────────────────────────────────────────────────────────────────

    fun startOrPause() {
        val s = _timerState.value
        if (s.running) pauseSession(s) else startSession(s)
    }

    private fun startSession(s: TimerState) {
        val now = System.currentTimeMillis()
        val next = s.copy(
            running       = true,
            wallStartMs   = now,
            ketoRunning   = s.ketosisOn,
            ketoWallStartMs = if (s.ketosisOn) now else s.ketoWallStartMs,
        )
        _timerState.value = next
        _saved.value = false
        viewModelScope.launch { repo.saveTimerState(next) }
        startTicker()
    }

    private fun pauseSession(s: TimerState) {
        val now = System.currentTimeMillis()
        val accMs     = s.accumulatedMs + (if (s.wallStartMs > 0) now - s.wallStartMs else 0L)
        val ketoAccMs = s.ketoAccumulatedMs + (if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L)
        val next = s.copy(running = false, wallStartMs = 0L, accumulatedMs = accMs,
                          ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = ketoAccMs)
        _timerState.value = next
        _elapsedMs.value    = accMs
        _ketoElapsedMs.value = ketoAccMs
        stopTicker()
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    fun reset() {
        stopTicker()
        val next = _timerState.value.copy(
            running = false, wallStartMs = 0L, accumulatedMs = 0L,
            ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = 0L,
        )
        _timerState.value = next
        _elapsedMs.value = 0L
        _ketoElapsedMs.value = 0L
        _saved.value = false
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val s   = _timerState.value
                val wall     = if (s.wallStartMs > 0) now - s.wallStartMs else 0L
                val ketoWall = if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L
                _elapsedMs.value     = s.accumulatedMs + wall
                _ketoElapsedMs.value = s.ketoAccumulatedMs + ketoWall
                delay(100L)  // 10 fps — smooth enough for a display, battery-friendly
            }
        }
    }

    private fun stopTicker() { tickJob?.cancel(); tickJob = null }

    // ─────────────────────────────────────────────────────────────────────────
    // Ketosis / fasting toggles
    // ─────────────────────────────────────────────────────────────────────────

    fun toggleKetosis() {
        val s = _timerState.value
        val now = System.currentTimeMillis()
        val next = if (s.ketosisOn) {
            // turning off — reset keto timer
            val ketoAcc = s.ketoAccumulatedMs + (if (s.ketoRunning && s.ketoWallStartMs > 0) now - s.ketoWallStartMs else 0L)
            s.copy(ketosisOn = false, ketoRunning = false, ketoWallStartMs = 0L, ketoAccumulatedMs = ketoAcc)
        } else {
            // turning on
            s.copy(ketosisOn = true,
                   ketoRunning = s.running,
                   ketoWallStartMs = if (s.running) now else 0L,
                   ketoAccumulatedMs = 0L)
        }
        _timerState.value = next
        _ketoElapsedMs.value = next.ketoElapsedMs
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    fun toggleKetoAdapted() {
        val s = _timerState.value
        val next = s.copy(ketoAdapted = !s.ketoAdapted)
        _timerState.value = next
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    fun toggleFastingActive() {
        val s = _timerState.value
        val next = s.copy(fastingActive = !s.fastingActive)
        _timerState.value = next
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    fun logMealNow() {
        val s = _timerState.value
        val next = s.copy(fastingActive = true, lastMealTs = System.currentTimeMillis())
        _timerState.value = next
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    // ── Add time to keto/fasting timers ──────────────────────────────────────
    fun addKetoHours(hours: Double) {
        val s = _timerState.value
        val addMs = (hours * MS_PER_HOUR).toLong()
        val next  = s.copy(ketoAccumulatedMs = (s.ketoAccumulatedMs + addMs).coerceAtLeast(0L))
        _timerState.value = next
        _ketoElapsedMs.value = next.ketoElapsedMs
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    fun addFastingHours(hours: Double) {
        val s = _timerState.value
        val deltaMs = (hours * MS_PER_HOUR).toLong()
        // Upper-bounded at "now" too — unlike addKetoHours' single coerceAtLeast(0),
        // repeatedly tapping the "-" stepper here could otherwise push lastMealTs into
        // the future, making fastingHours negative and silently blanking the badge.
        val newTs   = ((s.lastMealTs.takeIf { it > 0L } ?: System.currentTimeMillis()) - deltaMs)
            .coerceIn(0L, System.currentTimeMillis())
        val next    = s.copy(fastingActive = true, lastMealTs = newTs)
        _timerState.value = next
        viewModelScope.launch { repo.saveTimerState(next) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Session save
    // ─────────────────────────────────────────────────────────────────────────
    fun saveSession() {
        val s   = _timerState.value
        val p   = profile.value
        if (!p.isValid || _elapsedMs.value < 1000L) return

        val elapsedSec = _elapsedMs.value / 1000.0
        val snapshot   = computeMetabolicSnapshot(p, s, _elapsedMs.value, _ketoElapsedMs.value)

        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        val session = BiolismSession(
            id            = System.currentTimeMillis(),
            timestamp     = fmt.format(Instant.now()),
            elapsedSec    = elapsedSec,
            kcalBurned    = snapshot.kcalTotal,
            kcalPerMin    = if (elapsedSec > 0) snapshot.kcalTotal / (elapsedSec / 60.0) else 0.0,
            bmrDay        = snapshot.bmrDay,
            tdeeDay       = snapshot.tdeeDay,
            activityLabel = p.activityMeta.label,
            ketosis       = s.ketosisOn,
            startWeightKg = p.weightKg,
            endWeightKg   = snapshot.liveWeightKg,
            fatFrac       = snapshot.fatFrac,
            fatLostKg     = snapshot.fatLostKg,
        )
        viewModelScope.launch { repo.saveSession(session) }
        _saved.value = true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────
    fun togglePrecision() { _heroPrecision.value = !_heroPrecision.value }
    fun toggleRateMode()  { _showKcalPerSec.value = !_showKcalPerSec.value }
    fun saveManualHR(bpm: Int?) = viewModelScope.launch { repo.saveManualHR(bpm) }
    fun clearSaved() { _saved.value = false }

    override fun onCleared() {
        super.onCleared()
        stopTicker()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fix 8: Live derived metabolic state — computed in ViewModel, not in composition
// TrackerScreen consumes these StateFlows instead of calling BiolismEngine directly.
// ─────────────────────────────────────────────────────────────────────────────

data class LiveMetabolicState(
    val npRq: Double            = 0.858,
    val fatFrac: Double         = 0.40,
    val carbFrac: Double        = 0.43,
    val protFrac: Double        = 0.17,
    val kcalSec: Double         = 0.0,
    val watts: Double           = 0.0,
    val kcalTotal: Double       = 0.0,
    val fatLostKg: Double       = 0.0,
    val glycoLostKg: Double     = 0.0,
    val liveWeightKg: Double    = 0.0,
    val phase: fr.scanneat.domain.engine.biolism.KetoPhaseInfo? = null,
    val bmrDay: Double          = 0.0,
    val tdeeDay: Double         = 0.0,
)
