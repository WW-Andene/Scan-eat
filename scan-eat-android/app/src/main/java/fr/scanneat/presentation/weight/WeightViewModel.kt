package fr.scanneat.presentation.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.WeightEntry
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.health.WeightSummary
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.domain.engine.dashboard.weightForecast
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repo: WeightRepository,
    private val prefs: UserPreferences,
) : ViewModel() {
    init {
        // Sync was previously write-only (log() mirrors into Health Connect, but
        // nothing ever read external data back) — a smart scale writing its own
        // readings straight into Health Connect never reached this screen. Runs
        // once per screen open rather than on a timer; no-ops entirely if Health
        // Connect isn't available/permitted, so this is always safe to call.
        viewModelScope.launch { repo.syncFromHealthConnect() }
    }

    val entries: StateFlow<List<WeightEntry>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<WeightSummary?> = repo.observeSummary(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val forecast: StateFlow<WeightForecast> = combine(summary, prefs.profile) { s, p ->
        if (s != null && p.goalWeightKg != null)
            weightForecast(s.latestKg, p.goalWeightKg, s.trendKgPerWeek)
        else WeightForecast.InsufficientData
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightForecast.InsufficientData)

    val goalWeightKg: StateFlow<Double?> = prefs.profile.map { it.goalWeightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Previously plain Compose `remember` state in WeightScreen with no backing
    // store — every screen reopen (or process recreation) silently reset the
    // unit to kg, forcing a re-toggle to lb every single visit.
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setUseImperial(v: Boolean) {
        viewModelScope.launch { prefs.setUseImperialWeight(v) }
    }

    fun log(kg: Double, notes: String = "") {
        viewModelScope.launch { repo.log(LocalDate.now(), kg, notes) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    /** Re-creates a deleted entry (used by the "Undo" snackbar action) with its original date/weight/notes. */
    fun restore(entry: WeightEntry) {
        viewModelScope.launch { repo.log(entry.date, entry.weightKg, entry.notes) }
    }
}
