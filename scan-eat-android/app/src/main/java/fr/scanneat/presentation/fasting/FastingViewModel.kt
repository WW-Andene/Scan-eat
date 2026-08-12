package fr.scanneat.presentation.fasting

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.FastingState
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FastingViewModel @Inject constructor(
    private val repo: FastingRepository,
    prefs: UserPreferences,
) : ActionFailureViewModel() {
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

    /**
     * User-requested: "develop the tool" for Fasting - StartFastForm's target-
     * hours chip always defaulted to a hardcoded 16h, so a user who
     * consistently does 18h (or any protocol other than 16h) had to reselect
     * it on every single visit. Most-frequently-used targetHours in history,
     * ties broken by most recent use - the user's actual habit, not just
     * their latest (possibly one-off) fast. Null when there's no history yet,
     * so FastingScreen's own hardcoded 16h default still applies for a new user.
     */
    val preferredTargetHours: StateFlow<Int?> = history.map { list ->
        list.groupBy { it.targetHours }
            .entries
            .maxWithOrNull(
                compareBy<Map.Entry<Int, List<FastCompletion>>> { it.value.size }
                    .thenBy { entry -> entry.value.maxOf { it.endMs } },
            )
            ?.key
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Tick every second so the UI re-draws the elapsed counter
    val tick: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    // start()/stop()/cancel() previously called repo's DataStore writes completely
    // unguarded - unlike every sibling tracker (Weight/Activity/Dashboard/MealPlan/
    // Templates all wrap theirs in runCatching), so a write failure here wasn't
    // just silent, it was an uncaught exception that would crash the app.
    fun start(hours: Int) = guardedLaunch { repo.start(hours, activeProfileId.value) }
    fun stop()            = guardedLaunch { repo.stop(activeProfileId.value) }
    fun cancel()           = guardedLaunch { repo.cancel(activeProfileId.value) }

    /** Removes a single mis-logged history entry — previously only clearHistory() (nuke-all) existed. */
    fun deleteHistoryEntry(id: String) = guardedLaunch { repo.deleteEntry(id, activeProfileId.value) }
}

