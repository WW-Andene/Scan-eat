package fr.scanneat.presentation.biolism.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.domain.engine.biolism.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TrackerViewModel @Inject constructor(
    internal val repo: BiolismRepository,
    private val prefs: UserPreferences,
    private val fastingRepo: FastingRepository,
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

    /** Goal weight from the shared Profile — used to display a weight-goal ETA row in the tracker. */
    val goalWeightKg: StateFlow<Double?> = prefs.profile
        .map { it.goalWeightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Timer state from DataStore (source of truth on resume) ───────────────
    internal val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // ── Live elapsed — updated by coroutine ticker, not DataStore ─────────────
    internal val _elapsedMs   = MutableStateFlow(0L)
    internal val _ketoElapsedMs = MutableStateFlow(0L)
    val elapsedMs:     StateFlow<Long> = _elapsedMs.asStateFlow()
    val ketoElapsedMs: StateFlow<Long> = _ketoElapsedMs.asStateFlow()

    // ── Precision toggle ─────────────────────────────────────────────────────
    private val _heroPrecision = MutableStateFlow(false)
    val heroPrecision: StateFlow<Boolean> = _heroPrecision.asStateFlow()

    // ── Rate display: total kcal vs kcal/sec ─────────────────────────────────
    private val _showKcalPerSec = MutableStateFlow(false)
    val showKcalPerSec: StateFlow<Boolean> = _showKcalPerSec.asStateFlow()

    // ── Saved confirmation ────────────────────────────────────────────────────
    internal val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    // repo.saveTimerState()/repo.saveSession() below previously called their DataStore
    // writes completely unguarded - unlike every sibling tracker ViewModel (Weight/
    // Activity/Dashboard/MealPlan/Templates all wrap theirs in runCatching), so a
    // write failure here wasn't just silent, it was an uncaught exception that would
    // crash the app.
    internal val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // Fix 8: live metabolic state derived every 100ms in ViewModel, not in composition
    val liveMetabolic: StateFlow<LiveMetabolicState> = combine(
        profile, _timerState, _elapsedMs, _ketoElapsedMs, language,
    ) { p, s, elapsedMs, ketoMs, lang ->
        computeMetabolicSnapshot(p, s, elapsedMs, ketoMs, lang)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveMetabolicState())

    private fun computeMetabolicSnapshot(p: BiolismProfile, s: TimerState, elapsedMs: Long, ketoMs: Long, lang: String): LiveMetabolicState {
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
        val burn        = BiolismEngine.computeGlycogenFatBurn(kcalTotal, sub.carbFrac, s.ketosisOn)
        val glycoKcal   = burn.glycogenDepletedKcal
        val kcalPerKgFat = burn.kcalPerKgFat
        val fatLostKg   = fatKcal / kcalPerKgFat
        val glycoLostKg = (glycoKcal / 4.0) * (1.0 + WATER_PER_GLYC) / 1000.0
        val phase       = if (s.ketosisOn) BiolismEngine.ketoPhaseInfo(ketoHours, s.ketoAdapted, lang) else null

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


    internal var tickJob: Job? = null

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

    // Timer control (startOrPause/reset/startTicker/stopTicker) is implemented as
    // internal extension functions in TrackerTimerControl.kt (same package) —
    // see that file for the start/pause/ticker state machine.

    // Ketosis/fasting toggles (toggleKetosis/toggleKetoAdapted/toggleFastingActive/
    // logMealNow/importRealFast/addKetoHours/addFastingHours) are implemented as
    // internal extension functions in TrackerKetosisFastingLogic.kt (same package).

    // ── Bridge to the real Jeûne (Fasting tab) timer ─────────────────────────
    // Biolism's fasting toggle is a deliberately separate, manual metabolic
    // simulation input (see biolism_fasting_status_disabled: "Séparé du
    // minuteur de Journal") - a user exploring "what if I fasted 16h" isn't
    // necessarily running a real fast. So this doesn't force a live sync; it
    // only offers a one-tap import of the real fast's current elapsed time
    // when one happens to be running, so the two don't have to be re-entered
    // by hand and can't silently drift out of agreement if the user wants them
    // aligned.
    val realFastHours: StateFlow<Double?> = fastingRepo.state
        .map { it?.takeIf { f -> f.isActive }?.elapsedHours }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // importRealFast/addKetoHours/addFastingHours are implemented as internal
    // extension functions in TrackerKetosisFastingLogic.kt (same package).

    // ─────────────────────────────────────────────────────────────────────────
    // Session save
    // ─────────────────────────────────────────────────────────────────────────
    fun saveSession() {
        val s   = _timerState.value
        val p   = profile.value
        if (!p.isValid || _elapsedMs.value < 1000L) return

        val elapsedSec = _elapsedMs.value / 1000.0
        val snapshot   = computeMetabolicSnapshot(p, s, _elapsedMs.value, _ketoElapsedMs.value, language.value)

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
            // Previously the bare English label regardless of app language - persisted
            // verbatim, so this leaked English text into Session History and the CSV
            // export for French-language users even though `language` was already
            // available here (used by computeMetabolicSnapshot just above).
            activityLabel = p.activityMeta.label(language.value),
            ketosis       = s.ketosisOn,
            startWeightKg = p.weightKg,
            endWeightKg   = snapshot.liveWeightKg,
            fatFrac       = snapshot.fatFrac,
            fatLostKg     = snapshot.fatLostKg,
            ketoElapsedSec = _ketoElapsedMs.value / 1000.0,
        )
        viewModelScope.launch {
            runCatching { repo.saveSession(session) }
                .onSuccess { _saved.value = true }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────────────
    fun togglePrecision() { _heroPrecision.value = !_heroPrecision.value }
    fun toggleRateMode()  { _showKcalPerSec.value = !_showKcalPerSec.value }
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
