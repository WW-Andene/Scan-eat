package fr.scanneat.presentation.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.FastingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FastingViewModel @Inject constructor(
    private val repo: FastingRepository,
    prefs: UserPreferences,
) : ViewModel() {
    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real. FastingRepository is DataStore-backed
    // and previously had no profileId concept at all - now namespaced per profile.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val fastingState: StateFlow<FastingState?> = activeProfileId.flatMapLatest { id -> repo.state(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val history: StateFlow<List<FastCompletion>> = activeProfileId.flatMapLatest { id -> repo.history(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streak: StateFlow<Int> = activeProfileId.flatMapLatest { id -> repo.streak(id) }
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

    fun start(hours: Int) = viewModelScope.launch { runCatching { repo.start(hours, activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    fun stop()            = viewModelScope.launch { runCatching { repo.stop(activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    fun cancel()           = viewModelScope.launch { runCatching { repo.cancel(activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }

    /** Removes a single mis-logged history entry — previously only clearHistory() (nuke-all) existed. */
    fun deleteHistoryEntry(id: String) = viewModelScope.launch { runCatching { repo.deleteEntry(id, activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
}

