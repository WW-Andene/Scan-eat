package fr.scanneat.presentation.weight

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.common.ActionFailureViewModel
import fr.scanneat.data.repository.health.WeightEntry
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.health.WeightSummary
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.domain.engine.dashboard.longestLogStreak
import fr.scanneat.domain.engine.dashboard.weightForecast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repo: WeightRepository,
    private val prefs: UserPreferences,
) : ActionFailureViewModel() {
    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real. Declared before init below so the
    // Health Connect sync call can read its value.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    init {
        // Sync was previously write-only (log() mirrors into Health Connect, but
        // nothing ever read external data back) — a smart scale writing its own
        // readings straight into Health Connect never reached this screen. Runs
        // once per screen open rather than on a timer; no-ops entirely if Health
        // Connect isn't available/permitted, so this is always safe to call.
        // A revoked Health Connect permission mid-session, or the provider app
        // itself missing/outdated, makes readExternalWeights() throw rather than
        // return an empty list - previously unguarded, unlike every other Room/
        // DataStore write in this ViewModel layer. Background best-effort sync
        // with nothing for the user to retry, so failures are swallowed rather
        // than surfaced as an error snackbar.
        //
        // Previously called with no profileId, silently defaulting to
        // syncFromHealthConnect's profileId = "default" - the 4th instance this
        // session of the same bug pattern found in Biolism/TodayWidget/
        // ActivityViewModel: every Health Connect weigh-in got permanently
        // misfiled to the "default" profile regardless of which was active.
        // HydrationViewModel/ActivityViewModel's identical init calls already
        // thread activeProfileId.value through correctly - mirrored here.
        viewModelScope.launch { runCatching { repo.syncFromHealthConnect(profileId = activeProfileId.value) }.onFailure { e -> if (e is CancellationException) throw e } }
    }

    val entries: StateFlow<List<WeightEntry>> = activeProfileId.flatMapLatest { id -> repo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val summary: StateFlow<WeightSummary?> = activeProfileId.flatMapLatest { id -> repo.observeSummary(30, id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val forecast: StateFlow<WeightForecast> = combine(summary, prefs.profile) { s, p ->
        if (s != null && p.goalWeightKg != null)
            weightForecast(s.latestKg, p.goalWeightKg, s.trendKgPerWeek)
        else WeightForecast.InsufficientData
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeightForecast.InsufficientData)

    val goalWeightKg: StateFlow<Double?> = prefs.profile.map { it.goalWeightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val heightCm: StateFlow<Double?> = prefs.profile.map { it.heightCm }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Previously plain Compose `remember` state in WeightScreen with no backing
    // store — every screen reopen (or process recreation) silently reset the
    // unit to kg, forcing a re-toggle to lb every single visit.
    val useImperial: StateFlow<Boolean> = prefs.useImperialWeight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // LocalDate.now() called directly inside a .map on `entries` (the previous
    // shape of both properties below) only re-evaluates when the weight table
    // itself changes - a screen/tab left open across midnight with no new
    // weigh-in kept both the streak and the week-over-week comparison pinned
    // to yesterday's date. Polling + distinctUntilChanged re-subscribes exactly
    // when the day rolls over, same fix already applied to HydrationViewModel.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /**
     * Consecutive days ending today that have at least one log entry - 1-day
     * grace, same rule DashboardAggregator.logStreakDays() already uses for the
     * diary streak (today not yet logged doesn't break the streak by itself,
     * only today AND yesterday both missing does). Previously required today
     * specifically to already have an entry just to start counting at all - a
     * genuine multi-week streak reset to 0 every single morning before that
     * day's weigh-in, rather than only after a real gap.
     */
    val loggingStreakDays: StateFlow<Int> = combine(entries, today) { all, todayDate ->
        if (all.isEmpty()) return@combine 0
        val dates = all.map { it.date }.toSet()
        var day = todayDate
        if (!dates.contains(day)) {
            day = day.minusDays(1)
            if (!dates.contains(day)) return@combine 0
        }
        var streak = 0
        while (dates.contains(day)) { streak++; day = day.minusDays(1) }
        streak
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // app-audit §X: longestLogStreak() was already generic (built for the
    // diary, works on any Set<LocalDate>) but never called from any sibling
    // tracker - this is the "record" counterpart to loggingStreakDays above,
    // same relationship DashboardHeavyState's longestStreak already has to
    // its own current-streak.
    val longestStreak: StateFlow<Int> = entries
        .map { all -> longestLogStreak(all.mapTo(mutableSetOf()) { it.date }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // New: weekly average comparison (this week vs. last week) — individual weigh-ins
    // have a lot of noise; averaging by week shows the real trend more clearly.
    // Returns Pair(thisWeekAvg, lastWeekAvg) or null if either week has no data.
    val weeklyAvg: StateFlow<Pair<Double, Double>?> = combine(entries, today) { all, todayDate ->
        val thisWeekStart = todayDate.minusDays(6)
        val lastWeekStart = todayDate.minusDays(13)
        val lastWeekEnd   = todayDate.minusDays(7)
        val thisWeek = all.filter { !it.date.isBefore(thisWeekStart) }.map { it.weightKg }
        val lastWeek = all.filter { !it.date.isBefore(lastWeekStart) && !it.date.isAfter(lastWeekEnd) }.map { it.weightKg }
        if (thisWeek.isEmpty() || lastWeek.isEmpty()) null
        else thisWeek.average() to lastWeek.average()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setUseImperial(v: Boolean) {
        viewModelScope.launch { prefs.setUseImperialWeight(v) }
    }

    /**
     * User-requested: "develop the tool" for Weight - goalWeightKg was only
     * ever settable from Profile > body section, even though it's read and
     * displayed prominently on this exact screen (WeightSummaryCard's goal
     * row, the forecast). A user whose whole workflow is "Journal > Poids"
     * had to navigate all the way to a different screen just to set or
     * adjust the one number this screen already builds its progress
     * indicator around. No dedicated setter existed on UserPreferences for
     * a single profile field - saveProfile() only re-persists a full Profile,
     * so this reads the current one and re-saves it with just this field
     * changed, same shape ProfileViewModel's own save path already uses.
     * Null clears the goal (same "no goal set" state ProfileScreen's blank
     * field already produces).
     */
    fun setGoalWeightKg(kg: Double?) = guardedLaunch {
        val current = prefs.profile.first()
        prefs.saveProfile(current.copy(goalWeightKg = kg))
    }

    // app-audit §L2: now extends ActionFailureViewModel (see its own doc comment)
    // instead of hand-rolling the identical _actionFailed/actionFailed/
    // clearActionFailed trio - log()/restore() previously called repo.log()
    // completely unguarded before that trio was first added here, unlike every
    // sibling ViewModel's equivalent write (Result/Dashboard/MealPlan/Templates
    // all wrap theirs in runCatching), so a Room write failure here wasn't just
    // silent, it was an uncaught exception that would crash the app.
    fun log(kg: Double, notes: String = "", date: LocalDate = LocalDate.now()) = guardedLaunch {
        repo.log(date, kg, notes, activeProfileId.value)
    }

    fun delete(id: String) = guardedLaunch { repo.delete(id) }

    /** Re-creates a deleted entry (used by the "Undo" snackbar action) with its original date/weight/notes. */
    fun restore(entry: WeightEntry) = guardedLaunch {
        repo.log(entry.date, entry.weightKg, entry.notes, activeProfileId.value)
    }
}
