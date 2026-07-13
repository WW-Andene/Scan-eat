package fr.scanneat.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repo: ActivityRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    // Fix #6: use a reactive date so entries refresh automatically after midnight
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val entries: StateFlow<List<ActivityEntry>> = _date
        .flatMapLatest { repo.observeByDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightKg: StateFlow<Double?> = prefs.profile.map { it.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Both markedDates and pastSubTypes are derived from the same 365-day
    // range read — computed together so logging a new entry only re-triggers
    // one repo.getRange() call, not two.
    private val yearRange: StateFlow<List<ActivityEntry>> = entries.map {
        repo.getRange(LocalDate.now().minusDays(365), LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dates with at least one logged activity — drives the calendar marker dots.
    // Re-derived off `entries` (not a one-shot fetch) so logging an activity
    // today updates today's dot immediately instead of only after the screen
    // is left and reopened (WhileSubscribed would otherwise cache a stale set).
    val markedDates: StateFlow<Set<LocalDate>> = yearRange
        .map { it.map { e -> e.date }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Past custom sub-types the user has typed for each activity type — the
    // add-entry dialog only offered a fixed, hard-coded sub-type list (e.g.
    // "bench_press", "freestyle") with no way to enter something like
    // "rowing" or "pilates" at all. Surfaced as autocomplete suggestions
    // alongside a free-text field, not a replacement for it.
    val pastSubTypes: StateFlow<Map<ActivityType, List<String>>> = yearRange
        .map { list ->
            list.mapNotNull { e -> e.subType?.let { e.type to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, subs) -> subs.distinct().sorted() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Refresh to today if the date has changed since last foreground (e.g. app kept alive overnight)
    fun refreshDate() {
        val today = LocalDate.now()
        if (_date.value != today) _date.value = today
    }

    fun log(
        type: ActivityType, minutes: Int,
        subType: String? = null, sets: Int? = null, reps: Int? = null,
        distanceKm: Double? = null, weightUsedKg: Double? = null,
    ) {
        viewModelScope.launch {
            repo.log(
                type, minutes, weightKg.value ?: 70.0,
                subType = subType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg,
            )
        }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }

    /** Re-creates a deleted entry (used by the "Undo" snackbar action) with its original stats. */
    fun restore(entry: ActivityEntry) {
        viewModelScope.launch {
            repo.log(
                entry.type, entry.minutes, weightKg.value ?: 70.0, kcalOverride = entry.kcalBurned, date = entry.date,
                subType = entry.subType, sets = entry.sets, reps = entry.reps,
                distanceKm = entry.distanceKm, weightUsedKg = entry.weightUsedKg,
            )
        }
    }
}
