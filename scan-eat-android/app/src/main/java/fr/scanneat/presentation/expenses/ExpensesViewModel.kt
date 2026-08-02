package fr.scanneat.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.data.repository.expense.PriceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
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

    // ISO week (Monday start) - same week-boundary convention DashboardAggregator
    // already uses for its own weekly rollups, so "this week" means the same
    // thing here as it does everywhere else spend/activity is summarized.
    val weekTotal: StateFlow<Double> = entries.map { list ->
        val today = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekStart = today.minusDays(((today.dayOfWeek.value - weekFields.firstDayOfWeek.value) + 7) % 7L)
        list.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }.sumOf { it.priceEuros }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** Average price paid per logged purchase this week - a rough stand-in for
     *  "per meal" since price_log isn't tied to a specific diary meal slot. */
    val avgPerEntryThisWeek: StateFlow<Double?> = entries.map { list ->
        val today = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekStart = today.minusDays(((today.dayOfWeek.value - weekFields.firstDayOfWeek.value) + 7) % 7L)
        val thisWeek = list.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(today) }
        if (thisWeek.isEmpty()) null else thisWeek.sumOf { it.priceEuros } / thisWeek.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setBudgetWeekly(v: Double?) { viewModelScope.launch { prefs.setBudgetWeeklyEuros(v) } }
    fun setBudgetPerMeal(v: Double?) { viewModelScope.launch { prefs.setBudgetPerMealEuros(v) } }
    fun deleteEntry(id: String) { viewModelScope.launch { runCatching { priceRepo.delete(id) } } }
}
