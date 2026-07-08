package fr.scanneat.presentation.biolism.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.repository.biolism.BiolismRepository
import fr.scanneat.data.repository.biolism.BiolismRepository.MealEntry
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    private val repo: BiolismRepository,
) : ViewModel() {

    val profile  = repo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BiolismProfile())
    val meals    = repo.todayMeals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = repo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manualHR = repo.manualHR.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Fix 12: tick drives wall-clock recalculation of elapsed/keto/fasting times
    // so DataScreen shows live values even while the tracker is running.
    private val _tick = MutableStateFlow(System.currentTimeMillis())
    val tick: StateFlow<Long> = _tick.asStateFlow()
    init {
        viewModelScope.launch {
            while (true) { delay(1000L); _tick.value = System.currentTimeMillis() }
        }
    }

    // Live timer state: persisted base + wall-clock delta on each tick
    val timer: StateFlow<TimerState> = combine(repo.timerState, tick) { stored, now ->
        // Re-expose the stored state unchanged — the TimerState.elapsedMs / ketoHours getters
        // already compute from System.currentTimeMillis() vs wallStartMs, so they're live.
        stored
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerState())

    // ── Derived metabolics — recompute every tick while timer runs ────────────
    val metabolics: StateFlow<MetabolicResult?> = combine(profile, timer, tick) { p, s, _ ->
        if (!p.isValid) return@combine null
        val ketoHours = s.ketoHours
        val fh        = s.fastingHours
        val npRq      = if (s.ketosisOn) BiolismEngine.computeKetoRQ(ketoHours, s.ketoAdapted) else 0.858
        val ctxHours  = if (s.ketosisOn) ketoHours else if (fh > 0) fh else 0.0
        BiolismEngine.computeMetabolics(p, npRq, ctxHours, s.ketoAdapted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hormones: StateFlow<HormoneResult?> = combine(profile, timer, metabolics) { p, s, m ->
        if (m == null) return@combine null
        BiolismEngine.computeHormones(p, m, s.ketoHours, s.fastingHours, s.ketoAdapted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Caloric balance ───────────────────────────────────────────────────────
    fun saveMeal(entry: MealEntry) = viewModelScope.launch { repo.saveMeal(entry) }
    fun clearMeal(slotId: String)  = viewModelScope.launch { repo.clearMeal(slotId) }
    fun saveManualHR(bpm: Int?)    = viewModelScope.launch { repo.saveManualHR(bpm) }
}
