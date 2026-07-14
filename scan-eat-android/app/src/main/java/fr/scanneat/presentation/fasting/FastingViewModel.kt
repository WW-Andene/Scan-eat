package fr.scanneat.presentation.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.FastingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FastingViewModel @Inject constructor(
    private val repo: FastingRepository,
    prefs: UserPreferences,
) : ViewModel() {
    val fastingState: StateFlow<FastingState?> = repo.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val history: StateFlow<List<FastCompletion>> = repo.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streak: StateFlow<Int> = repo.streak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")
    /** Longest achieved fast in hours — used to surface a personal-record alert. */
    val personalRecord: StateFlow<Double> = history.map { list -> list.maxOfOrNull { it.achievedHours } ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Tick every second so the UI re-draws the elapsed counter
    val tick: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    fun start(hours: Int) = viewModelScope.launch { repo.start(hours) }
    fun stop()            = viewModelScope.launch { repo.stop() }
    fun cancel()          = viewModelScope.launch { repo.cancel() }
}

