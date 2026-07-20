package fr.scanneat.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import kotlinx.coroutines.CancellationException
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

    init {
        // Sync was previously write-only for Activité too (log() mirrors into
        // Health Connect, but nothing ever read external data back) - a
        // fitness tracker writing its own sessions straight into Health
        // Connect never reached this screen, unlike weight which already
        // reads back. Same as WeightViewModel's identical init call: runs
        // once per screen open, no-ops entirely if Health Connect isn't
        // available/permitted, so always safe to call.
        viewModelScope.launch { repo.syncFromHealthConnect() }
    }

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

    // Improvement: 7-day burn chart data — kcal burned per day for the last 7 days
    val weeklyBurn: StateFlow<List<Pair<LocalDate, Int>>> = yearRange
        .map { all ->
            val today = LocalDate.now()
            (0..6).map { dayBack ->
                val d = today.minusDays(dayBack.toLong())
                val kcal = all.filter { it.date == d }.sumOf { it.kcalBurned }
                d to kcal
            }.reversed()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New: weekly active minutes (current week, Mon–today) vs WHO 150 min goal
    val weeklyMinutes: StateFlow<Int> = yearRange
        .map { all ->
            val today = LocalDate.now()
            val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            all.filter { it.date >= monday && it.date <= today }.sumOf { it.minutes }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** New: week-over-week trend — positive = more active this week than last.
     *  Computed from same yearRange read, no extra query. */
    val weekTrendPct: StateFlow<Int?> = yearRange
        .map { all ->
            val today = LocalDate.now()
            val thisMonday  = today.minusDays(today.dayOfWeek.value.toLong() - 1)
            val lastMonday  = thisMonday.minusDays(7)
            val lastSunday  = thisMonday.minusDays(1)
            val thisMin  = all.filter { it.date >= thisMonday && it.date <= today }.sumOf { it.minutes }
            val lastMin  = all.filter { it.date >= lastMonday && it.date <= lastSunday }.sumOf { it.minutes }
            if (lastMin == 0) null else ((thisMin - lastMin).toDouble() / lastMin * 100).toInt()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Improvement: activity types sorted by most-recently-used first, so the
     *  most common workout type appears at the top of the add-entry chip row. */
    val sortedActivityTypes: StateFlow<List<ActivityType>> = yearRange
        .map { all ->
            val lastUsed = all.groupBy { it.type }
                .mapValues { (_, entries) -> entries.maxOf { it.date } }
            ActivityType.entries.sortedByDescending { lastUsed[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityType.entries)

    // New: consecutive-days activity streak — counts backwards from yesterday
    // (today is in progress, so it's excluded to avoid "1-day streak" resetting
    // every morning before the first workout, same logic as HydrationViewModel.streak).
    val streak: StateFlow<Int> = yearRange.map { all ->
        val activityDates = all.map { it.date }.toSet()
        var count = 0
        var date = LocalDate.now().minusDays(1)
        while (activityDates.contains(date)) {
            count++
            date = date.minusDays(1)
        }
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Refresh to today if the date has changed since last foreground (e.g. app kept alive overnight)
    fun refreshDate() {
        val today = LocalDate.now()
        if (_date.value != today) _date.value = today
    }

    // log()/delete()/restore() previously called repo.log()/repo.delete() completely
    // unguarded - unlike every sibling ViewModel's equivalent write (Result/Dashboard/
    // MealPlan/Templates/Weight all wrap theirs in runCatching), so a Room write
    // failure here wasn't just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun log(
        type: ActivityType, minutes: Int,
        subType: String? = null, sets: Int? = null, reps: Int? = null,
        distanceKm: Double? = null, weightUsedKg: Double? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.log(
                    type, minutes, weightKg.value ?: 70.0,
                    subType = subType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg,
                )
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { repo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }

    /** Re-creates a deleted entry (used by the "Undo" snackbar action) with its original stats. */
    fun restore(entry: ActivityEntry) {
        viewModelScope.launch {
            runCatching {
                repo.log(
                    entry.type, entry.minutes, weightKg.value ?: 70.0, kcalOverride = entry.kcalBurned, date = entry.date,
                    subType = entry.subType, sets = entry.sets, reps = entry.reps,
                    distanceKm = entry.distanceKm, weightUsedKg = entry.weightUsedKg,
                )
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
