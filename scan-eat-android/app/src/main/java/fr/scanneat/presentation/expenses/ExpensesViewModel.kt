package fr.scanneat.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.domain.model.ProductCategory
import kotlinx.coroutines.CancellationException
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

    // Category breakdown for the current trailing-7-day window (same window as
    // weekTotal/avgPerEntryThisWeek above), sorted highest-spend first - price_log
    // already stores each entry's category (populated from the scanned product, or
    // OTHER for a manually-added entry, see addEntry() below), but nothing in this
    // ViewModel ever aggregated it: the week card previously showed only a single
    // total, with no way to see which category actually drove the spend.
    val spendByCategory: StateFlow<List<Pair<ProductCategory, Double>>> = combine(entries, today) { list, todayDate ->
        val weekStart = todayDate.minusDays(6)
        list.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(todayDate) }
            .groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.priceEuros } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _actionFailed = MutableStateFlow(false)
    /** True briefly after a failed write, for a one-shot error snackbar. */
    val actionFailed: StateFlow<Boolean> = _actionFailed.asStateFlow()
    fun clearActionFailed() { _actionFailed.value = false }

    fun setBudgetWeekly(v: Double?) {
        viewModelScope.launch { runCatching { prefs.setBudgetWeeklyEuros(v) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }
    fun setBudgetPerMeal(v: Double?) {
        viewModelScope.launch { runCatching { prefs.setBudgetPerMealEuros(v) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }
    fun deleteEntry(id: String) {
        viewModelScope.launch { runCatching { priceRepo.delete(id) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }

    /**
     * Logs a purchase directly, with no scanned product behind it - previously the
     * ONLY way to add a price_log row at all was ResultViewModel's barcode-scan
     * flow (see PriceRepository.log's callers), so a cash purchase, a recipe
     * ingredient bought at a market, or literally anything without a barcode could
     * never be logged, silently defeating "track my spending" for a large share of
     * real grocery shopping. Manual entries have no scanned product to classify
     * them, so they're logged as [ProductCategory.OTHER] - still counted in
     * weekTotal/spendByCategory, just not attributed to a specific food category.
     */
    fun addEntry(date: LocalDate, productName: String, priceEuros: Double, weightG: Double?) {
        if (productName.isBlank()) return
        viewModelScope.launch {
            runCatching { priceRepo.log(date, productName.trim(), barcode = null, category = ProductCategory.OTHER, priceEuros = priceEuros, weightG = weightG) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
}
