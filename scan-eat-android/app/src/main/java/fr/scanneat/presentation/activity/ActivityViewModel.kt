package fr.scanneat.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.domain.engine.health.OvertrainingWarning
import fr.scanneat.domain.engine.health.checkDailyOvertraining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Groups an ActivityEntry by the fields that define "the same workout" for [ActivityViewModel.quickLogSuggestions]. */
private data class QuickLogKey(val type: ActivityType, val subType: String?, val minutes: Int, val sets: Int?, val reps: Int?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val repo: ActivityRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    // In-app language (Settings) can differ from the device locale - the weekly
    // burn chart's weekday labels/content description previously built off
    // Locale.getDefault() instead, unlike every sibling date-heavy screen.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real. Declared before init below so the
    // Health Connect sync call can read its value.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    init {
        // Sync was previously write-only for Activité too (log() mirrors into
        // Health Connect, but nothing ever read external data back) - a
        // fitness tracker writing its own sessions straight into Health
        // Connect never reached this screen, unlike weight which already
        // reads back. Same as WeightViewModel's identical init call: runs
        // once per screen open, no-ops entirely if Health Connect isn't
        // available/permitted, so always safe to call.
        // A revoked Health Connect permission mid-session, or the provider app
        // itself missing/outdated, makes readExternalActivity() throw rather than
        // return an empty list - previously unguarded here, unlike every other
        // Room/DataStore write in this ViewModel layer. This is a background
        // best-effort sync with nothing for the user to retry, so failures are
        // swallowed rather than surfaced as an error snackbar.
        //
        // Previously called with no profileId, silently defaulting to
        // syncFromHealthConnect's profileId = "default" - every imported workout
        // got permanently stamped to the "default" profile regardless of which
        // profile was actually active, invisible on the real active profile and
        // leaking into whoever's "default" instead (same class of bug this
        // session already found and fixed for Biolism and TodayWidget).
        // HydrationViewModel's identical init call already threads
        // activeProfileId.value through correctly - mirrored here.
        viewModelScope.launch { runCatching { repo.syncFromHealthConnect(profileId = activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e } }
    }

    // Polling + distinctUntilChanged, not a fixed `val` set once at construction
    // and refreshed only via ActivityScreen's ON_RESUME observer calling
    // refreshDate() - a screen left foregrounded straight through midnight (no
    // pause/resume in between) never got that call at all, so `entries` could
    // silently go a day stale with no lifecycle event to catch it. Same fix
    // already applied to Weight/Hydration/Medication/Diary/MealPlan for this
    // exact bug.
    val date: StateFlow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDate.now())

    val entries: StateFlow<List<ActivityEntry>> = combine(date, activeProfileId) { d, id -> d to id }
        .flatMapLatest { (d, id) -> repo.observeByDate(d, id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightKg: StateFlow<Double?> = prefs.profile.map { it.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // User-requested: does the overtraining check personalize on age/health
    // conditions, or is it a flat threshold for everyone? Previously the
    // latter - these feed checkDailyOvertraining's own age/healthConditions
    // params (see its doc comment) so an older user or one with a
    // cardiac-relevant condition gets a tightened threshold and a
    // risk-specific message, not the same generic cutoff as everyone else.
    val ageYears: StateFlow<Int?> = prefs.profile.map { it.ageYears }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val healthConditions: StateFlow<Set<String>> = prefs.profile.map { it.healthConditions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Both markedDates and pastSubTypes are derived from the same 365-day
    // range read — computed together so logging a new entry only re-triggers
    // one repo.getRange() call, not two.
    private val yearRange: StateFlow<List<ActivityEntry>> = combine(entries, activeProfileId) { _, id -> id }.map { id ->
        repo.getRange(LocalDate.now().minusDays(365), LocalDate.now(), id)
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

    // User-requested: one-tap re-log of a frequently repeated workout (same
    // type + sub-type + duration + sets/reps combo, seen at least twice in
    // the last 90 days) instead of re-filling the whole Add dialog every
    // time - most real workout routines repeat week to week. Same "seen at
    // least twice" bar and shape as Grocery's frequentSuggestions
    // (GroceryViewModel.kt) - a one-off entry isn't a real routine worth
    // surfacing as a shortcut. Excludes combos already logged today, same
    // reason Grocery's own suggestions exclude names already on the list -
    // no point one-tap-suggesting something the user just did.
    val quickLogSuggestions: StateFlow<List<ActivityEntry>> = combine(yearRange, entries) { all, today ->
        val cutoff = LocalDate.now().minusDays(90)
        val todayKeys = today.map { QuickLogKey(it.type, it.subType, it.minutes, it.sets, it.reps) }.toSet()
        all.filter { it.date >= cutoff }
            .groupBy { QuickLogKey(it.type, it.subType, it.minutes, it.sets, it.reps) }
            .filterKeys { it !in todayKeys }
            .mapNotNull { (_, group) ->
                if (group.size < 2) return@mapNotNull null
                // Most recent instance of the combo - carries the representative
                // distanceKm/weightUsedKg/wasOutdoors values for the quick-log tap.
                group.maxByOrNull { it.date }
            }
            .sortedWith(compareByDescending<ActivityEntry> { entry -> all.count { QuickLogKey(it.type, it.subType, it.minutes, it.sets, it.reps) == QuickLogKey(entry.type, entry.subType, entry.minutes, entry.sets, entry.reps) } }.thenByDescending { it.date })
            .take(5)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Logs [entry]'s type/sub-type/duration/sets/reps/distance/weight/outdoors again, dated today - see [quickLogSuggestions]. */
    fun quickLog(entry: ActivityEntry) = log(
        type = entry.type, minutes = entry.minutes, subType = entry.subType,
        sets = entry.sets, reps = entry.reps, distanceKm = entry.distanceKm,
        weightUsedKg = entry.weightUsedKg, wasOutdoors = entry.wasOutdoors,
    )

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

    // R&D improvement: configurable weekly goal - previously a flat WHO 150min
    // figure hardcoded in ActivityWeeklyMinutesCard with no way to override it,
    // unlike Hydration's own customGoalMl. Null means "use the WHO default".
    val customWeeklyGoalMinutes: StateFlow<Int?> = prefs.activityWeeklyGoalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setWeeklyGoalMinutes(minutes: Int?) = viewModelScope.launch {
        runCatching { prefs.setActivityWeeklyGoalMinutes(minutes) }
            .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
    }

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

    // One-time celebration the moment the current streak sets a new all-time
    // record - Fasting already has this exact acknowledgment for personalRecord
    // (a distinct moment, not just ActivityStreakRow's persistent badge), Activité
    // didn't. Emits the new record's day count; UI shows a one-shot snackbar.
    private val _newStreakRecord = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val newStreakRecord: SharedFlow<Int> = _newStreakRecord.asSharedFlow()

    init {
        viewModelScope.launch {
            streak.collect { current ->
                if (current <= 0) return@collect
                val best = prefs.activityBestStreak.first()
                if (current > best) {
                    prefs.setActivityBestStreak(current)
                    _newStreakRecord.emit(current)
                }
            }
        }
    }

    // log()/delete()/restore() previously called repo.log()/repo.delete() completely
    // unguarded - unlike every sibling ViewModel's equivalent write (Result/Dashboard/
    // MealPlan/Templates/Weight all wrap theirs in runCatching), so a Room write
    // failure here wasn't just silent, it was an uncaught exception that would crash the app.
    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed save, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    // User-requested: is the app "aware" of an excessive single-day training
    // volume (e.g. 6h running, 3h strength) and does it warn about it? See
    // checkDailyOvertraining's own doc comment - AddActivityDialog already
    // shows this live while typing, but a user could still log via quickLog()
    // (no dialog at all) or simply not notice the inline text before tapping
    // Add. Emitted here too as a one-shot snackbar cue, same
    // shape as [newStreakRecord] above.
    private val _overtrainingWarning = MutableSharedFlow<OvertrainingWarning>(extraBufferCapacity = 1)
    val overtrainingWarning: SharedFlow<OvertrainingWarning> = _overtrainingWarning.asSharedFlow()

    fun log(
        type: ActivityType, minutes: Int,
        subType: String? = null, sets: Int? = null, reps: Int? = null,
        distanceKm: Double? = null, weightUsedKg: Double? = null, wasOutdoors: Boolean = false,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.log(
                    type, minutes, weightKg.value ?: 70.0,
                    subType = subType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg,
                    wasOutdoors = wasOutdoors, profileId = activeProfileId.value,
                )
            }.onSuccess {
                // entries.value is the pre-write snapshot (Room's Flow re-emit
                // hasn't necessarily landed yet) - the same total AddActivityDialog's
                // own live preview would have shown, computed here so quickLog()
                // (which never goes through that dialog at all) still gets it.
                val total = entries.value.filter { it.type == type }.sumOf { it.minutes } + minutes
                checkDailyOvertraining(type, total, ageYears.value, healthConditions.value)?.let { _overtrainingWarning.emit(it) }
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Edits an already-logged entry in place — the only tracker (with Weight/Medication)
     *  that previously supported only delete-and-recreate for a mis-logged entry. */
    fun update(
        id: String, type: ActivityType, minutes: Int,
        subType: String? = null, sets: Int? = null, reps: Int? = null,
        distanceKm: Double? = null, weightUsedKg: Double? = null, wasOutdoors: Boolean = false,
    ) {
        viewModelScope.launch {
            runCatching {
                repo.update(
                    id, type, minutes, weightKg.value ?: 70.0,
                    subType = subType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg,
                    wasOutdoors = wasOutdoors, profileId = activeProfileId.value,
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
                    distanceKm = entry.distanceKm, weightUsedKg = entry.weightUsedKg, wasOutdoors = entry.wasOutdoors,
                    profileId = activeProfileId.value,
                )
            }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
