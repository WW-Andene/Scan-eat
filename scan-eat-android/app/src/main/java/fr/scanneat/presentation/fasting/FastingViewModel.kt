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

    // start()/stop()/cancel() previously called repo's DataStore writes completely
    // unguarded - unlike every sibling tracker (Weight/Activity/Dashboard/MealPlan/
    // Templates all wrap theirs in runCatching), so a write failure here wasn't
    // just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun start(hours: Int) = viewModelScope.launch { runCatching { repo.start(hours) }.onFailure { _actionFailed.value = true } }
    fun stop()            = viewModelScope.launch { runCatching { repo.stop() }.onFailure { _actionFailed.value = true } }
    fun cancel()           = viewModelScope.launch { runCatching { repo.cancel() }.onFailure { _actionFailed.value = true } }
}

