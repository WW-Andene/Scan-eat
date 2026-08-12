package fr.scanneat.presentation.expenses

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.backup.CsvExportRepository
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.nutrition.CustomFoodRepository
import fr.scanneat.data.repository.planning.ManualGroceryRepository
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import fr.scanneat.domain.engine.scoring.inferCategoryFromName
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.common.ActionFailureViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val priceRepo: PriceRepository,
    private val prefs: UserPreferences,
    private val csvExportRepository: CsvExportRepository,
    private val groceryRepo: ManualGroceryRepository,
    private val customFoodRepo: CustomFoodRepository,
) : ActionFailureViewModel() {

    // R&D audit finding, phase 2: profileId was dead scaffolding until
    // multi-profile support made it real.
    private val activeProfileId: StateFlow<String> = prefs.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "default")

    val entries: StateFlow<List<PriceEntry>> = activeProfileId.flatMapLatest { id -> priceRepo.observeAll(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // R&D audit finding: PriceRepository.deductStock's "remaining stock" concept
    // (drawn down as ConsumptionRepository logs portions from a purchased lot)
    // never surfaced anywhere as a "running low" nudge - a user tracking stock
    // had to notice the number themselves. Below 15% of the originally-purchased
    // weight, not yet fully depleted (remainingG > 0 - a fully-used lot is just
    // "gone", not "running low", and re-adding it is exactly what the button
    // below is for regardless).
    val lowStockItems: StateFlow<List<PriceEntry>> = entries.map { list ->
        list.filter { e ->
            val weight = e.weightG
            val remaining = e.remainingG
            weight != null && weight > 0.0 && remaining != null && remaining > 0.0 && remaining / weight <= 0.15
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Adds a running-low item back onto the grocery list, defaulting to the
     *  quantity originally purchased (a reasonable "buy the same again" default,
     *  editable afterward like any other grocery item). */
    fun addToGroceryList(entry: PriceEntry) {
        val grams = entry.weightG ?: return
        guardedLaunch { groceryRepo.add(entry.productName, grams, activeProfileId.value) }
    }

    // User-reported: the Expenses "add product" name field had no search-as-
    // you-type, unlike Diary/Meals' AddDiaryEntryDialog - same debounced
    // search-over-observeAll() pattern as DiaryViewModel.searchResults, reused
    // here for the same reason (results can't go stale after a save/delete the
    // way a one-shot repo.search() snapshot could). Only FOOD_DB/custom-food
    // matches, not scan-history - a manual expense entry has no barcode/scan
    // concept to search against in the first place.
    private val _expenseNameQuery = MutableStateFlow("")
    val expenseNameQuery: StateFlow<String> = _expenseNameQuery.asStateFlow()

    val expenseNameSuggestions: StateFlow<List<FoodEntry>> =
        combine(_expenseNameQuery.debounce(200), activeProfileId.flatMapLatest { id -> customFoodRepo.observeAll(id) }) { q, customs -> q to customs }
            .map { (q, customs) -> if (q.isBlank()) emptyList() else searchFoodDB(q, limit = 6, customs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setExpenseNameQuery(q: String) { _expenseNameQuery.value = q }
    fun clearExpenseNameQuery() { _expenseNameQuery.value = "" }

    /** Best-effort category guess for a picked suggestion - a FoodEntry search
     *  result carries no ProductCategory of its own (see CustomFoodRepository.
     *  toProduct's identical inference), so this mirrors that same fallback
     *  rather than leaving the picked name's category stuck on OTHER. */
    fun inferExpenseCategory(name: String): ProductCategory = inferCategoryFromName(name)

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

    // User-requested: "empreinte financière annuelle" - project the current
    // month's real spend rate into a full year and compare it to the
    // declared monthly budget x12, instead of only ever showing week/month
    // totals in isolation with no longer-term framing.
    val annualProjection: StateFlow<fr.scanneat.domain.engine.expense.AnnualSpendProjection> =
        combine(monthTotal, budgetMonthlyEuros, today) { total, budget, date ->
            fr.scanneat.domain.engine.expense.annualSpendProjection(
                monthTotalEuros = total,
                dayOfMonth = date.dayOfMonth,
                daysInMonth = date.lengthOfMonth(),
                budgetMonthlyEuros = budget,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), fr.scanneat.domain.engine.expense.AnnualSpendProjection(0.0, null, false))

    fun setBudgetWeekly(v: Double?) {
        guardedLaunch { prefs.setBudgetWeeklyEuros(v) }
    }
    fun setBudgetPerMeal(v: Double?) {
        guardedLaunch { prefs.setBudgetPerMealEuros(v) }
    }
    fun setBudgetDaily(v: Double?) {
        guardedLaunch { prefs.setBudgetDailyEuros(v) }
    }
    fun setBudgetMonthly(v: Double?) {
        guardedLaunch { prefs.setBudgetMonthlyEuros(v) }
    }
    // Same undo-delete pattern as WeightViewModel/MedicationViewModel/DiaryViewModel -
    // snapshots the row right before deleting it so a snackbar "Undo" can restore it.
    // Previously delete was confirm-only with no way back afterward, unlike Weight/
    // Medication's confirm-then-undo pair.
    private var lastDeleted: PriceEntry? = null

    fun deleteEntry(id: String) {
        val entry = entries.value.firstOrNull { it.id == id }
        guardedLaunch {
            priceRepo.delete(id)
            lastDeleted = entry
        }
    }

    /** Re-logs a deleted purchase (used by the "Undo" snackbar action) with its original date/name/price/weight. */
    fun undoDeleteEntry() {
        val entry = lastDeleted ?: return
        lastDeleted = null
        guardedLaunch { priceRepo.log(entry.date, entry.productName, barcode = entry.barcode, category = entry.category, priceEuros = entry.priceEuros, weightG = entry.weightG, profileId = activeProfileId.value) }
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
        guardedLaunch { priceRepo.log(date, productName.trim(), barcode = null, category = category, priceEuros = priceEuros, weightG = weightG, profileId = activeProfileId.value) }
    }

    /** Corrects an already-logged entry - see PriceRepository.update's own doc
     *  comment on why this preserves the row's id/loggedAt/barcode instead of a
     *  delete-then-re-add round trip. */
    fun editEntry(id: String, date: LocalDate, productName: String, category: ProductCategory, priceEuros: Double, weightG: Double?) {
        if (productName.isBlank()) return
        guardedLaunch { priceRepo.update(id, date, productName.trim(), category, priceEuros, weightG) }
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
        guardedLaunch { _csvExportReady.value = csvExportRepository.exportPricesCsv() }
    }
    fun clearCsvExport() { _csvExportReady.value = null }
    /** The SAF "save file" picker succeeded but the write itself failed (disk full,
     *  provider error) - same shape as SettingsViewModel.reportBackupIoFailed(). */
    fun reportCsvExportIoFailed() { _csvExportReady.value = null; flagActionFailed() }
}
