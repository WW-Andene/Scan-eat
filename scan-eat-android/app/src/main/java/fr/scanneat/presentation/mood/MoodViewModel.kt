package fr.scanneat.presentation.mood

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.mood.MoodEntry
import fr.scanneat.data.repository.mood.MoodRepository
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
 * User-requested: a Mood/Stress tab at the same level of polish as
 * Sommeil/Poids/Hydratation/Jeûne - a daily 1-5 mood rating, a daily 1-5
 * stress rating, and a streak - see MoodEntity/MoodRepository's own doc
 * comments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MoodViewModel @Inject constructor(
    private val repo: MoodRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {

    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val entries: StateFlow<List<MoodEntry>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    val streak: StateFlow<Int> = activeProfileId.flatMapLatest { id -> repo.streak(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreak: StateFlow<Int> = activeProfileId.flatMapLatest { id -> repo.longestStreak(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Today's entry for the summary card, null when nothing logged yet today. */
    val today: StateFlow<MoodEntry?> = entries.map { list -> list.find { it.date == LocalDate.now() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 7 days of mood/stress (date -> Pair(mood, stress)) for the weekly chart, same shape
     *  SleepViewModel.weeklyDuration already uses - 0 means "not logged that day", not "mood/stress of 0". */
    val weeklyMoodStress: StateFlow<List<Triple<LocalDate, Int, Int>>> = entries.map { list ->
        val byDate = list.associateBy { it.date }
        val today = LocalDate.now()
        (6 downTo 0).map { daysBack ->
            val d = today.minusDays(daysBack.toLong())
            val e = byDate[d]
            Triple(d, e?.mood ?: 0, e?.stress ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Average mood/stress over the last 7 logged days (not the fixed calendar week above), null if none logged. */
    val avgMood: StateFlow<Double?> = entries.map { list ->
        val recent = list.sortedByDescending { it.date }.take(7)
        if (recent.isEmpty()) null else recent.sumOf { it.mood } / recent.size.toDouble()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val avgStress: StateFlow<Double?> = entries.map { list ->
        val recent = list.sortedByDescending { it.date }.take(7)
        if (recent.isEmpty()) null else recent.sumOf { it.stress } / recent.size.toDouble()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun log(mood: Int, stress: Int, notes: String = "", date: LocalDate? = null) = guardedLaunch {
        repo.log(mood, stress, notes, date, activeProfileId.value)
    }

    fun delete(id: String) = guardedLaunch { repo.delete(id) }
}
