package fr.scanneat.presentation.sleep

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.sleep.SleepEntry
import fr.scanneat.data.repository.sleep.SleepRepository
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/**
 * User-requested: a sleep tracker at the same level of polish as Poids/
 * Hydratation/Jeûne - bedtime/wake time (duration derived), a 1-5 quality
 * rating, a configurable nightly-hours goal, and a streak - see
 * SleepEntity/SleepRepository's own doc comments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SleepViewModel @Inject constructor(
    private val repo: SleepRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val entries: StateFlow<List<SleepEntry>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val goalHours: StateFlow<Double> = prefs.sleepGoalHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8.0)

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    fun setGoalHours(hours: Double) = guardedLaunch { prefs.setSleepGoalHours(hours) }

    val streak: StateFlow<Int> = goalHours.flatMapLatest { goal -> activeProfileId.flatMapLatest { id -> repo.streak(goal, id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreak: StateFlow<Int> = goalHours.flatMapLatest { goal -> activeProfileId.flatMapLatest { id -> repo.longestStreak(goal, id) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Last night's duration/quality for the summary card, null when nothing logged yet. */
    val lastNight: StateFlow<SleepEntry?> = entries.map { it.maxByOrNull { e -> e.date } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 7 nights of duration hours (date -> hours) for the weekly bar chart, same shape HydrationViewModel.weeklyIntake already uses. */
    val weeklyDuration: StateFlow<List<Pair<LocalDate, Double>>> = entries.map { list ->
        val byDate = list.associateBy { it.date }
        val today = LocalDate.now()
        (6 downTo 0).map { daysBack ->
            val d = today.minusDays(daysBack.toLong())
            d to (byDate[d]?.durationHours ?: 0.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Average duration over the last 7 logged nights (not the fixed calendar week above), null if none logged. */
    val avgDurationHours: StateFlow<Double?> = entries.map { list ->
        val recent = list.sortedByDescending { it.date }.take(7)
        if (recent.isEmpty()) null else recent.sumOf { it.durationHours } / recent.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun log(bedtimeMs: Long, wakeMs: Long, quality: Int, notes: String = "", date: LocalDate? = null) = guardedLaunch {
        repo.log(bedtimeMs, wakeMs, quality, notes, date, activeProfileId.value)
    }

    fun delete(id: String) = guardedLaunch { repo.delete(id) }
}
