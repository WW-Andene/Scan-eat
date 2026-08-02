package fr.scanneat.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.data.repository.expense.PriceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val priceRepo: PriceRepository,
    private val prefs: UserPreferences,
) : ViewModel() {

    val entries: StateFlow<List<PriceEntry>> = priceRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetWeeklyEuros: StateFlow<Double?> = prefs.budgetWeeklyEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetPerMealEuros: StateFlow<Double?> = prefs.budgetPerMealEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // In-app language (Settings) can differ from the device locale - every sibling
    // date-heavy screen (Weight/Diary/MealPlan/etc.) already threads this through
    // instead of defaulting to Locale.getDefault(), which would show entry dates
    // in the wrong language for a user whose in-app and device languages differ.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // LocalDate.now() called directly inside a .map on `entries` (the previous
    // shape of both properties below) only re-evaluates when the price list
    // itself changes - a user who goes a day or more without logging a new
    // purchase kept both figures pinned to whatever "today" was on their last
    // purchase, past midnight. Same bug class WeightViewModel.weeklyAvg already
    // guards against by combining with a ticking Flow<LocalDate>, applied here too.
    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    // Trailing 7-day window ending today (today-6..today), NOT an ISO calendar
    // week - matches the "this week" convention every other feature in the app
    // already uses (DashboardAggregator.weeklyRollup, the cross-tracker insight,
    // WeightViewModel.weeklyAvg). An earlier version of this file used a
    // Monday/Sunday-aligned ISO week instead, which meant "this week" silently
    // meant a different span here than everywhere else spend/intake/activity is
    // summarized - e.g. on a Wednesday, Dashboard's cross-tracker window and
    // this screen's own header covered different date ranges.
    val weekTotal: StateFlow<Double> = combine(entries, today) { list, todayDate ->
        val weekStart = todayDate.minusDays(6)
        list.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(todayDate) }.sumOf { it.priceEuros }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Average price paid per logged purchase this week - a rough stand-in for
     *  "per meal" since price_log isn't tied to a specific diary meal slot. */
    val avgPerEntryThisWeek: StateFlow<Double?> = combine(entries, today) { list, todayDate ->
        val weekStart = todayDate.minusDays(6)
        val thisWeek = list.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(todayDate) }
        if (thisWeek.isEmpty()) null else thisWeek.sumOf { it.priceEuros } / thisWeek.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setBudgetWeekly(v: Double?) { viewModelScope.launch { prefs.setBudgetWeeklyEuros(v) } }
    fun setBudgetPerMeal(v: Double?) { viewModelScope.launch { prefs.setBudgetPerMealEuros(v) } }
    fun deleteEntry(id: String) { viewModelScope.launch { runCatching { priceRepo.delete(id) } } }
}
