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
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Which tracker(s) have data on a given day - drives the small per-source dots under each date. */
enum class CalendarSource { MEALS, WEIGHT, ACTIVITY, HYDRATION, FASTING }

data class CalendarDayDetail(
    val date: LocalDate,
    val mealCount: Int = 0,
    val kcal: Double = 0.0,
    val weightKg: Double? = null,
    val activities: List<ActivityEntry> = emptyList(),
    val hydrationMl: Int = 0,
    val fastCompletion: FastCompletion? = null,
) {
    val isEmpty: Boolean get() = mealCount == 0 && weightKg == null && activities.isEmpty() && hydrationMl == 0 && fastCompletion == null
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
 * Medication is deliberately not included - MedicationRepository tracks an
 * active list with a reminder schedule, not a dated "taken on this day" log,
 * so there is no real per-day event for it to mark here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val consumptionRepo: ConsumptionRepository,
    private val weightRepo: WeightRepository,
    private val activityRepo: ActivityRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
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

    /**
     * Per-source marker set for every day in the visible month. Activity's
     * range read is a one-shot suspend call (ActivityRepository has no
     * month-range Flow), refetched whenever [month] changes but not live
     * while the screen stays open on the same month - an acceptable trade-off
     * for an aggregated overview versus threading five more Flows through it.
     */
    val markers: StateFlow<Map<LocalDate, Set<CalendarSource>>> = _month.flatMapLatest { m ->
        val start = m.atDay(1)
        val end = m.atEndOfMonth()
        combine(
            consumptionRepo.observeRange(start, end),
            weightRepo.observeAll(),
            fastingRepo.history,
            flow { emit(activityRepo.getRange(start, end)) },
        ) { diaryEntries, weights, fastHistory, activities ->
            val out = mutableMapOf<LocalDate, MutableSet<CalendarSource>>()
            fun mark(date: LocalDate, source: CalendarSource) {
                if (date < start || date > end) return
                out.getOrPut(date) { mutableSetOf() } += source
            }
            diaryEntries.forEach { mark(it.date, CalendarSource.MEALS) }
            weights.forEach { mark(it.date, CalendarSource.WEIGHT) }
            activities.forEach { mark(it.date, CalendarSource.ACTIVITY) }
            fastHistory.forEach { f -> runCatching { LocalDate.parse(f.date) }.getOrNull()?.let { mark(it, CalendarSource.FASTING) } }
            out
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Aggregated detail for [selectedDate], reactive to any of the five sources changing. */
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarDayDetail(LocalDate.now()))
}
