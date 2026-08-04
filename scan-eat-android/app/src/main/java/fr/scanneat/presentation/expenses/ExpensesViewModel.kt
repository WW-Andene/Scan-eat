package fr.scanneat.presentation.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.backup.CsvExportRepository
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
    private val csvExportRepository: CsvExportRepository,
) : ViewModel() {

    val entries: StateFlow<List<PriceEntry>> = priceRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetWeeklyEuros: StateFlow<Double?> = prefs.budgetWeeklyEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetPerMealEuros: StateFlow<Double?> = prefs.budgetPerMealEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetDailyEuros: StateFlow<Double?> = prefs.budgetDailyEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val budgetMonthlyEuros: StateFlow<Double?> = prefs.budgetMonthlyEuros
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // In-app language (Settings) can differ from the device locale - every sibling
    // date-heavy screen (Weight/Diary/MealPlan/etc.) already threads this through
    // instead of defaulting to Locale.getDefault(), which would show entry dates
    // in the wrong language for a user whose in-app and device languages differ.
    val language: StateFlow<String> = prefs.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "fr")

    // Settings > Devise - previously every amount in this screen hardcoded "€".
    val currencySymbol: StateFlow<String> = prefs.currencySymbol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "€")

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

    /** Total/average/category-breakdown for a single [start]..[end] window - the
     *  Jour/Semaine/Mois cards previously each recomputed this same
     *  filter+sum+groupBy triple independently (9 near-identical combine()
     *  blocks below), so a change to one window's logic (e.g. the ISO-week fix
     *  documented on [weekTotal]) had to be applied to all three by hand. */
    private data class SpendWindow(val total: Double, val avg: Double?, val byCategory: List<Pair<ProductCategory, Double>>)

    private fun windowedStats(list: List<PriceEntry>, start: LocalDate, end: LocalDate): SpendWindow {
        val inWindow = list.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val total = inWindow.sumOf { it.priceEuros }
        val avg = if (inWindow.isEmpty()) null else total / inWindow.size
        val byCategory = inWindow.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.priceEuros } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }
        return SpendWindow(total, avg, byCategory)
    }

    /** Today only (a single calendar day) - the finest-grained window of the
     *  Jour/Semaine/Mois toggle, added alongside the daily budget target. */
    private val dayStats = combine(entries, today) { list, todayDate -> windowedStats(list, todayDate, todayDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendWindow(0.0, null, emptyList()))

    val dayTotal: StateFlow<Double> = dayStats.map { it.total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val spendByCategoryDay: StateFlow<List<Pair<ProductCategory, Double>>> = dayStats.map { it.byCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Trailing 7-day window ending today (today-6..today), NOT an ISO calendar
    // week - matches the "this week" convention every other feature in the app
    // already uses (DashboardAggregator.weeklyRollup, the cross-tracker insight,
    // WeightViewModel.weeklyAvg). An earlier version of this file used a
    // Monday/Sunday-aligned ISO week instead, which meant "this week" silently
    // meant a different span here than everywhere else spend/intake/activity is
    // summarized - e.g. on a Wednesday, Dashboard's cross-tracker window and
    // this screen's own header covered different date ranges.
    private val weekStats = combine(entries, today) { list, todayDate -> windowedStats(list, todayDate.minusDays(6), todayDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendWindow(0.0, null, emptyList()))

    val weekTotal: StateFlow<Double> = weekStats.map { it.total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    /** Average price paid per logged purchase this week - a rough stand-in for
     *  "per meal" since price_log isn't tied to a specific diary meal slot. */
    val avgPerEntryThisWeek: StateFlow<Double?> = weekStats.map { it.avg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    // Category breakdown for the current trailing-7-day window, sorted highest-
    // spend first - price_log already stores each entry's category (populated
    // from the scanned product, or OTHER for a manually-added entry, see
    // addEntry() below), but nothing in this ViewModel ever aggregated it: the
    // week card previously showed only a single total, with no way to see
    // which category actually drove the spend.
    val spendByCategory: StateFlow<List<Pair<ProductCategory, Double>>> = weekStats.map { it.byCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Calendar month-to-date (1st of the current month through today), unlike
     *  weekTotal's trailing 7-day window - "this month" is naturally understood as
     *  the current calendar month, not a rolling 30-day span, and resets cleanly
     *  on the 1st the way a user budgeting month to month expects. */
    private val monthStats = combine(entries, today) { list, todayDate -> windowedStats(list, todayDate.withDayOfMonth(1), todayDate) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpendWindow(0.0, null, emptyList()))

    val monthTotal: StateFlow<Double> = monthStats.map { it.total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val avgPerEntryThisMonth: StateFlow<Double?> = monthStats.map { it.avg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val spendByCategoryMonth: StateFlow<List<Pair<ProductCategory, Double>>> = monthStats.map { it.byCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    fun setBudgetDaily(v: Double?) {
        viewModelScope.launch { runCatching { prefs.setBudgetDailyEuros(v) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }
    fun setBudgetMonthly(v: Double?) {
        viewModelScope.launch { runCatching { prefs.setBudgetMonthlyEuros(v) }.onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true } }
    }
    // Same undo-delete pattern as WeightViewModel/MedicationViewModel/DiaryViewModel -
    // snapshots the row right before deleting it so a snackbar "Undo" can restore it.
    // Previously delete was confirm-only with no way back afterward, unlike Weight/
    // Medication's confirm-then-undo pair.
    private var lastDeleted: PriceEntry? = null

    fun deleteEntry(id: String) {
        val entry = entries.value.firstOrNull { it.id == id }
        viewModelScope.launch {
            runCatching { priceRepo.delete(id) }
                .onSuccess { lastDeleted = entry }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Re-logs a deleted purchase (used by the "Undo" snackbar action) with its original date/name/price/weight. */
    fun undoDeleteEntry() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch {
            runCatching { priceRepo.log(entry.date, entry.productName, barcode = entry.barcode, category = entry.category, priceEuros = entry.priceEuros, weightG = entry.weightG) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
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
    fun addEntry(date: LocalDate, productName: String, category: ProductCategory, priceEuros: Double, weightG: Double?) {
        if (productName.isBlank()) return
        viewModelScope.launch {
            runCatching { priceRepo.log(date, productName.trim(), barcode = null, category = category, priceEuros = priceEuros, weightG = weightG) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    /** Corrects an already-logged entry - see PriceRepository.update's own doc
     *  comment on why this preserves the row's id/loggedAt/barcode instead of a
     *  delete-then-re-add round trip. */
    fun editEntry(id: String, date: LocalDate, productName: String, category: ProductCategory, priceEuros: Double, weightG: Double?) {
        if (productName.isBlank()) return
        viewModelScope.launch {
            runCatching { priceRepo.update(id, date, productName.trim(), category, priceEuros, weightG) }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }

    // Same CsvExportReady-then-SAF-picker split as SettingsViewModel's own CSV
    // export functions (the CSV is built here, testable/no Android dependency;
    // ExpensesScreen launches the system "save file" picker once it's ready) -
    // exposed directly on this screen instead of only reachable via Settings >
    // Sauvegarde, since a user reviewing their spending here is the one most
    // likely to want to export it on the spot.
    private val _csvExportReady = MutableStateFlow<String?>(null)
    val csvExportReady: StateFlow<String?> = _csvExportReady.asStateFlow()
    fun prepareCsvExport() {
        viewModelScope.launch {
            runCatching { csvExportRepository.exportPricesCsv() }
                .onSuccess { _csvExportReady.value = it }
                .onFailure { e -> if (e is CancellationException) throw e; _actionFailed.value = true }
        }
    }
    fun clearCsvExport() { _csvExportReady.value = null }
    /** The SAF "save file" picker succeeded but the write itself failed (disk full,
     *  provider error) - same shape as SettingsViewModel.reportBackupIoFailed(). */
    fun reportCsvExportIoFailed() { _csvExportReady.value = null; _actionFailed.value = true }
}
