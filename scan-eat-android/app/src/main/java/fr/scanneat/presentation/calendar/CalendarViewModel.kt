package fr.scanneat.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.nutrition.DayNotesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import javax.inject.Inject
import kotlin.math.roundToInt

data class MonthSummary(val totalKcal: Int, val activeMinutes: Int, val hydrationMl: Int, val activeDays: Int)

data class WeekSummary(
    val weekStart: LocalDate,
    val totalKcal: Int,
    val activeMinutes: Int,
    val hydrationMl: Int,
    val activeDays: Int,
)

/** Which tracker(s) have data on a given day - drives the small per-source dots under each date. */
enum class CalendarSource { MEALS, WEIGHT, ACTIVITY, HYDRATION, FASTING, MEDICATION, NOTE }

data class CalendarDayDetail(
    val date: LocalDate,
    val mealCount: Int = 0,
    val kcal: Double = 0.0,
    val weightKg: Double? = null,
    val activities: List<ActivityEntry> = emptyList(),
    val hydrationMl: Int = 0,
    val fastCompletion: FastCompletion? = null,
    val medicationsTaken: List<MedicationLogEntry> = emptyList(),
    // DayNotesRepository.listDates() was already wired into the month grid's NOTE dot,
    // but nothing surfaced the actual note text anywhere - tapping a day with that dot
    // to read the note found nothing, a dead end.
    val note: String = "",
) {
    val isEmpty: Boolean get() = mealCount == 0 && weightKg == null && activities.isEmpty() &&
        hydrationMl == 0 && fastCompletion == null && medicationsTaken.isEmpty() && note.isBlank()
}

/**
 * Previously there was no single place to see everything logged on a given
 * day — Diary/Weight/Activity/Hydration each embedded their own siloed
 * mini-calendar (single-domain marker dots, own month/selection state), so
 * reconstructing "what happened on March 12th" meant opening four different
 * screens and flipping each one to the same date by hand. This combines all
 * of them into one month grid (multi-source dots per day) plus a day-detail
 * panel for whichever date is selected.
 *
 * Medication is included via MedicationRepository's adherence log (a dated
 * "I took this" event, separate from the active list + reminder schedule).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val weightRepo: WeightRepository,
    private val activityRepo: ActivityRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    private val medicationRepo: MedicationRepository,
    private val dayNotesRepo: DayNotesRepository,
    prefs: UserPreferences,
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    fun setMonth(m: YearMonth) { _month.value = m }
    fun selectDate(date: LocalDate) { _selectedDate.value = date }

    // _month/_selectedDate were previously set once at construction and never
    // revisited - a session left open on "today" across midnight (this screen
    // has no lifecycle hook forcing a re-check) left the day-detail panel and
    // month grid's selection frozen on yesterday, while MultiMarkerMonthGrid's
    // own isToday check (recomputed fresh every recomposition) correctly moved
    // the bold "today" cell forward - a visible split-brain state on the same
    // screen. Only advances when the user was actually viewing "today" (not
    // after they've deliberately browsed to a different day/month), so this
    // never yanks someone back mid-browse.
    init {
        viewModelScope.launch {
            var previousToday = LocalDate.now()
            while (true) {
                delay(60_000)
                val newToday = LocalDate.now()
                if (newToday != previousToday) {
                    if (_selectedDate.value == previousToday) _selectedDate.value = newToday
                    if (_month.value == YearMonth.from(previousToday)) _month.value = YearMonth.from(newToday)
                    previousToday = newToday
                }
            }
        }
    }

    /**
     * Per-source marker set for every day in the visible month. Activity now
     * uses ActivityRepository.observeRange (a real Flow, not a one-shot
     * suspend read), so activity logged elsewhere while Calendar stays open
     * on the same month refreshes its dots immediately instead of only on
     * the next month change.
     */
    val markers: StateFlow<Map<LocalDate, Set<CalendarSource>>> = _month.flatMapLatest { m ->
        val start = m.atDay(1)
        val end = m.atEndOfMonth()
        combine(
            consumptionRepo.observeRange(start, end),
            weightRepo.observeAll(),
            fastingRepo.history,
            activityRepo.observeRange(start, end),
            // Was a one-shot flow{ emit(getLogRange(...)) } - unlike activityRepo.observeRange
            // above (a real Flow), medication dots never refreshed while Calendar stayed
            // open on the same month; dayDetail elsewhere in this file already correctly
            // uses medicationRepo.observeLogByDate for the same reason.
            medicationRepo.observeLogRange(start, end),
        ) { diaryEntries, weights, fastHistory, activities, medicationLog ->
            val out = mutableMapOf<LocalDate, MutableSet<CalendarSource>>()
            fun mark(date: LocalDate, source: CalendarSource) {
                if (date < start || date > end) return
                out.getOrPut(date) { mutableSetOf() } += source
            }
            diaryEntries.forEach { mark(it.date, CalendarSource.MEALS) }
            weights.forEach { mark(it.date, CalendarSource.WEIGHT) }
            activities.forEach { mark(it.date, CalendarSource.ACTIVITY) }
            fastHistory.forEach { f -> runCatching { LocalDate.parse(f.date) }.getOrNull()?.let { mark(it, CalendarSource.FASTING) } }
            medicationLog.forEach { mark(it.date, CalendarSource.MEDICATION) }
            out
        // DayNotesRepository.listDates() was fully implemented (a real "list all dates
        // with a note" query) with zero callers - there was no visual signal anywhere
        // that a given day even had a note, so finding one meant opening Diary and
        // paging through days by hand. Nested rather than folded into the 5-arg
        // combine above since kotlinx.coroutines' typed combine() overloads stop at 5.
        }.combine(flow { emit(dayNotesRepo.listDates()) }) { out, noteDates ->
            noteDates.filter { it in start..end }.forEach { d -> out.getOrPut(d) { mutableSetOf() } += CalendarSource.NOTE }
            out
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // New: per-week summaries for the visible month — aggregated kcal, active minutes,
    // hydration, and active days. Previously the calendar showed only per-day dots with
    // no way to see a week-level roll-up without mentally summing seven day panels.
    val weekSummaries: StateFlow<Map<LocalDate, WeekSummary>> = _month.flatMapLatest { m ->
        val start = m.atDay(1)
        val end = m.atEndOfMonth()
        combine(
            consumptionRepo.observeRange(start, end),
            activityRepo.observeRange(start, end),
            flow { emit(hydrationRepo.exportAll().toMap()) },
            // markers already correctly tracks all six sources (meals, weight,
            // activity, hydration, fasting, medication) - activeDays below
            // previously recomputed a narrower 2-source subset from scratch
            // (diary entries + activities only), so a user who reliably logged
            // weight/hydration but not every meal/workout saw colored dots on
            // the month grid above for days this week-summary counted as
            // inactive - two parts of the same screen visibly disagreeing about
            // the same data.
            markers,
        ) { diaryEntries, activities, hydrationMap, markerMap ->
            val isoWeek = WeekFields.ISO
            val result = mutableMapOf<LocalDate, WeekSummary>()
            // Group days in the month by ISO week start (Monday)
            val weekStarts = (0 until m.lengthOfMonth())
                .map { m.atDay(it + 1) }
                .groupBy { date ->
                    val dow = date.dayOfWeek.value // Mon=1
                    date.minusDays(dow.toLong() - 1)
                }
            weekStarts.forEach { (weekStart, days) ->
                val kcal = diaryEntries.filter { it.date in days }.sumOf { it.nutrition.energyKcal * it.portionG / 100 }.roundToInt()
                val activeMin = activities.filter { it.date in days }.sumOf { it.minutes }
                val hydration = days.sumOf { hydrationMap[it] ?: 0 }
                val activeDays = days.count { d -> markerMap[d]?.isNotEmpty() == true }
                result[weekStart] = WeekSummary(weekStart, kcal, activeMin, hydration, activeDays)
            }
            result
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Month-level totals derived from the already-computed week summaries — no extra query. */
    val monthSummary: StateFlow<MonthSummary?> = weekSummaries.map { weeks ->
        if (weeks.isEmpty()) null
        else MonthSummary(
            totalKcal     = weeks.values.sumOf { it.totalKcal },
            activeMinutes = weeks.values.sumOf { it.activeMinutes },
            hydrationMl   = weeks.values.sumOf { it.hydrationMl },
            activeDays    = weeks.values.sumOf { it.activeDays },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Aggregated detail for [selectedDate], reactive to any of the six sources changing. */
    val dayDetail: StateFlow<CalendarDayDetail> = _selectedDate.flatMapLatest { date ->
        combine(
            consumptionRepo.observeDay(date),
            weightRepo.observeAll(),
            activityRepo.observeByDate(date),
            hydrationRepo.observe(date),
            fastingRepo.history,
        ) { daily, weights, activities, hydrationMl, fastHistory ->
            CalendarDayDetail(
                date           = date,
                mealCount      = daily.entries.size,
                kcal           = daily.totals.energyKcal,
                weightKg       = weights.find { it.date == date }?.weightKg,
                activities     = activities,
                hydrationMl    = hydrationMl,
                fastCompletion = fastHistory.find { it.date == date.toString() },
            )
        }.combine(medicationRepo.observeLogByDate(date)) { detail, medsTaken ->
            detail.copy(medicationsTaken = medsTaken)
        }.combine(dayNotesRepo.observe(date)) { detail, note ->
            detail.copy(note = note)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarDayDetail(LocalDate.now()))
}
