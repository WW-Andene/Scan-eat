package fr.scanneat.presentation.expenses

import compose.icons.tablericons.FileInvoice
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Check
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ExpensesSummaryMode { DAY, WEEK, MONTH }

/**
 * Unlike WeightScreen/ActivityScreen/etc., this screen only ever runs embedded
 * (its sole call site is DiaryScreen's Dépenses tab, always passing embedded
 * semantics) - no standalone Scaffold/TopAppBar path exists, so there's no
 * `embedded` toggle or `onBack` here to begin with.
 */
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel = hiltViewModel(),
    embeddedBottomPadding: Dp = 0.dp,
    embeddedTopPadding: Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries = viewModel.entries.collectAsStateWithLifecycle()
    val dayTotal = viewModel.dayTotal.collectAsStateWithLifecycle()
    val weekTotal = viewModel.weekTotal.collectAsStateWithLifecycle()
    val monthTotal = viewModel.monthTotal.collectAsStateWithLifecycle()
    val budgetDaily = viewModel.budgetDailyEuros.collectAsStateWithLifecycle()
    val budgetWeekly = viewModel.budgetWeeklyEuros.collectAsStateWithLifecycle()
    val budgetPerMeal = viewModel.budgetPerMealEuros.collectAsStateWithLifecycle()
    val budgetMonthly = viewModel.budgetMonthlyEuros.collectAsStateWithLifecycle()
    val avgPerEntry = viewModel.avgPerEntryThisWeek.collectAsStateWithLifecycle()
    val avgPerEntryMonth = viewModel.avgPerEntryThisMonth.collectAsStateWithLifecycle()
    val spendByCategoryDay = viewModel.spendByCategoryDay.collectAsStateWithLifecycle()
    val spendByCategory = viewModel.spendByCategory.collectAsStateWithLifecycle()
    val spendByCategoryMonth = viewModel.spendByCategoryMonth.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showBudgetEdit by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PriceEntry?>(null) }
    var summaryMode by remember { mutableStateOf(ExpensesSummaryMode.WEEK) }
    val language = viewModel.language.collectAsStateWithLifecycle()
    // In-app language, not Locale.getDefault() - see WeightScreen's own doc
    // comment on the identical fix; this was the one date-heavy screen still
    // defaulting to the device locale instead of the in-app one.
    val dateFmt = remember(language.value) { DateTimeFormatter.ofPattern("d MMM", Locale(language.value)) }

    // markTaken/save/delete-style writes previously called priceRepo/prefs
    // completely unguarded here - see ExpensesViewModel.actionFailed's own comment
    // (same runCatching+actionFailed pattern every sibling tracker screen uses).
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // Same CsvExportReady-then-SAF-picker split SettingsScreen's own CSV export
    // launchers use - the CSV text is built in the ViewModel (testable, no
    // Android dependency), this launches the system "save file" picker once
    // it's ready, since only an Activity context (not the ViewModel) can do that.
    val context = LocalContext.current
    val csvExportReady = viewModel.csvExportReady.collectAsStateWithLifecycle()
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = csvExportReady.value
        when {
            uri == null -> viewModel.clearCsvExport()
            csv != null -> {
                val stream = context.contentResolver.openOutputStream(uri)
                val wrote = stream != null && runCatching { stream.use { it.write(csv.toByteArray()) } }.isSuccess
                if (wrote) viewModel.clearCsvExport() else viewModel.reportCsvExportIoFailed()
            }
            else -> viewModel.reportCsvExportIoFailed()
        }
    }
    LaunchedEffect(csvExportReady.value) {
        if (csvExportReady.value != null) {
            csvExportLauncher.launch("scaneat-depenses-${LocalDate.now()}.csv")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
            contentPadding = PaddingValues(top = embeddedTopPadding, bottom = embeddedBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            item {
                ExpensesWeekCard(
                    mode = summaryMode,
                    onModeChange = { summaryMode = it },
                    dayTotal = dayTotal.value,
                    weekTotal = weekTotal.value,
                    monthTotal = monthTotal.value,
                    budgetDaily = budgetDaily.value,
                    budgetWeekly = budgetWeekly.value,
                    budgetMonthly = budgetMonthly.value,
                    avgPerEntry = avgPerEntry.value,
                    avgPerEntryMonth = avgPerEntryMonth.value,
                    budgetPerMeal = budgetPerMeal.value,
                    spendByCategoryDay = spendByCategoryDay.value,
                    spendByCategory = spendByCategory.value,
                    spendByCategoryMonth = spendByCategoryMonth.value,
                    onEditBudget = { showBudgetEdit = true },
                    onOpenCalendar = onOpenCalendar,
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.expenses_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reachable directly from this screen instead of only via
                        // Settings > Sauvegarde (which already has the same export,
                        // see SettingsViewModel.preparePricesCsvExport) - a user
                        // reviewing their spending here is the one most likely to
                        // want to export it on the spot.
                        IconButton(onClick = { viewModel.prepareCsvExport() }, enabled = entries.value.isNotEmpty()) {
                            Icon(Icons.Rounded.Download, stringResource(R.string.expenses_export_csv), tint = OnSurface.copy(0.5f))
                        }
                        // Previously the ONLY way to log a price_log row at all was
                        // ResultViewModel's barcode-scan flow - a cash purchase or
                        // anything without a barcode could never be tracked. See
                        // ExpensesViewModel.addEntry's own doc comment.
                        TextButton(onClick = { showAddEntry = true }) {
                            Text(stringResource(R.string.expenses_add_entry), color = AccentCoral, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (entries.value.isEmpty()) {
                // Was the only tracker's empty state with no CTA - every other tracker
                // (Weight/Activity/Medication/Grocery/Templates/etc.) routes straight
                // into its add flow from here.
                item {
                    EmptyListState(
                        TablerIcons.FileInvoice, stringResource(R.string.expenses_empty_body),
                        ctaLabel = stringResource(R.string.expenses_add_entry),
                        onCta = { showAddEntry = true },
                    )
                }
            } else {
                items(entries.value, key = { it.id }) { entry ->
                    ExpenseEntryRow(entry = entry, dateFmt = dateFmt, onEdit = { editTarget = entry }, onDelete = { deleteTarget = entry.id })
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }

        ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
    }

    deleteTarget?.let { id ->
        DeleteConfirmDialog(
            itemName = entries.value.firstOrNull { it.id == id }?.productName,
            onConfirm = { viewModel.deleteEntry(id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showBudgetEdit) {
        BudgetEditDialog(
            dailyInitial = budgetDaily.value,
            weeklyInitial = budgetWeekly.value,
            monthlyInitial = budgetMonthly.value,
            perMealInitial = budgetPerMeal.value,
            onConfirm = { daily, weekly, monthly, perMeal ->
                viewModel.setBudgetDaily(daily)
                viewModel.setBudgetWeekly(weekly)
                viewModel.setBudgetMonthly(monthly)
                viewModel.setBudgetPerMeal(perMeal)
                showBudgetEdit = false
            },
            onDismiss = { showBudgetEdit = false },
        )
    }

    if (showAddEntry) {
        AddExpenseDialog(
            onConfirm = { name, category, price, weight ->
                viewModel.addEntry(LocalDate.now(), name, category, price, weight)
                showAddEntry = false
            },
            onDismiss = { showAddEntry = false },
        )
    }

    editTarget?.let { entry ->
        EditExpenseDialog(
            entry = entry,
            onConfirm = { name, category, price, weight ->
                viewModel.editEntry(entry.id, entry.date, name, category, price, weight)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

/** Rounds a euro amount to whole cents for an exact over/under-budget comparison -
 *  see ExpensesWeekCard's own comment on why a raw Double `>` isn't safe here. */
private fun centsOf(euros: Double): Long = Math.round(euros * 100)

@Composable
private fun ExpensesWeekCard(
    mode: ExpensesSummaryMode,
    onModeChange: (ExpensesSummaryMode) -> Unit,
    dayTotal: Double,
    weekTotal: Double,
    monthTotal: Double,
    budgetDaily: Double?,
    budgetWeekly: Double?,
    budgetMonthly: Double?,
    avgPerEntry: Double?,
    avgPerEntryMonth: Double?,
    budgetPerMeal: Double?,
    spendByCategoryDay: List<Pair<ProductCategory, Double>>,
    spendByCategory: List<Pair<ProductCategory, Double>>,
    spendByCategoryMonth: List<Pair<ProductCategory, Double>>,
    onEditBudget: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    // Each of the three windows (Jour/Semaine/Mois) now has its own budget
    // target (budgetDaily/budgetWeekly/budgetMonthly, all set via
    // onEditBudget) - previously only the weekly window had one, so Month
    // (and now Day) showed the total with no progress bar at all.
    val total = when (mode) {
        ExpensesSummaryMode.DAY   -> dayTotal
        ExpensesSummaryMode.WEEK  -> weekTotal
        ExpensesSummaryMode.MONTH -> monthTotal
    }
    val budget = when (mode) {
        ExpensesSummaryMode.DAY   -> budgetDaily
        ExpensesSummaryMode.WEEK  -> budgetWeekly
        ExpensesSummaryMode.MONTH -> budgetMonthly
    }
    // Per-meal average is only meaningful over a multi-purchase window - Day
    // mode has no separate "average per purchase today" figure, so this stays
    // null there rather than reusing the weekly average out of context.
    val avg = when (mode) {
        ExpensesSummaryMode.DAY   -> null
        ExpensesSummaryMode.WEEK  -> avgPerEntry
        ExpensesSummaryMode.MONTH -> avgPerEntryMonth
    }
    val byCategory = when (mode) {
        ExpensesSummaryMode.DAY   -> spendByCategoryDay
        ExpensesSummaryMode.WEEK  -> spendByCategory
        ExpensesSummaryMode.MONTH -> spendByCategoryMonth
    }
    val ofBudgetTemplate = when (mode) {
        ExpensesSummaryMode.DAY   -> R.string.expenses_of_budget_day
        ExpensesSummaryMode.WEEK  -> R.string.expenses_of_budget
        ExpensesSummaryMode.MONTH -> R.string.expenses_of_budget_month
    }
    val noBudgetHint = when (mode) {
        ExpensesSummaryMode.DAY   -> R.string.expenses_no_budget_hint_day
        ExpensesSummaryMode.WEEK  -> R.string.expenses_no_budget_hint
        ExpensesSummaryMode.MONTH -> R.string.expenses_no_budget_hint_month
    }

    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Every other embedded Journal tab (Weight/Activity/Hydration/Fasting/
                // Traitement) exposes this same shortcut into the unified Calendar -
                // Dépenses previously accepted onOpenCalendar as a parameter but never
                // actually called it anywhere, the one embedded tab silently missing
                // this affordance.
                // No explicit size override - every sibling embedded tab's own
                // calendar IconButton (Weight/Medication/etc.) relies on the
                // default 48dp minimum touch target rather than shrinking it;
                // an earlier pass here set 32dp, undersizing this one tap target
                // below every other IconButton in the app.
                IconButton(onClick = onOpenCalendar) {
                    Icon(TablerIcons.Calendar, stringResource(R.string.expenses_cd_calendar), tint = OnSurface.copy(0.5f))
                }
                Spacer(Modifier.width(Spacing.XS))
                // Jour/Semaine/Mois toggle - was a static "Cette semaine" label with
                // no way to see a shorter (today-only) or longer (calendar month)
                // total than the trailing 7-day window.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    listOf(
                        ExpensesSummaryMode.DAY to stringResource(R.string.expenses_view_day),
                        ExpensesSummaryMode.WEEK to stringResource(R.string.expenses_view_week),
                        ExpensesSummaryMode.MONTH to stringResource(R.string.expenses_view_month),
                    ).forEach { (m, label) ->
                        val selected = m == mode
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AccentCoral else OnSurface.copy(0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(CardRadius.BADGE))
                                .clickable { onModeChange(m) }
                                .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                        )
                    }
                }
            }
            TextButton(onClick = onEditBudget) {
                Text(stringResource(R.string.expenses_edit_budget), color = AccentCoral, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            "${total.formatDecimal(2)} €",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        if (budget != null && budget > 0) {
            val pct = (total / budget).toFloat().coerceIn(0f, 1.5f)
            // total is a Double sum of per-entry prices, so a spend that's
            // mathematically exactly at budget can drift a fraction of a cent
            // either way from float summation - comparing to the nearest cent
            // (not a raw > 1f/Double >) avoids a budget met to the cent
            // flip-flopping between "over" and "under" depending on entry order.
            val isOver = centsOf(total) > centsOf(budget)
            LinearProgressIndicator(
                progress = { pct.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(CardRadius.BADGE)),
                color = if (isOver) AccentCoral else semanticGreen(),
                trackColor = OnSurface.copy(0.1f),
            )
            // Was color-only (a thin 8dp bar + small label hue switch) - easy to miss,
            // and not a real "helpful warning vs. scolding" signal since nothing here
            // said "over budget" in words, only in a subtle color shift.
            Text(
                stringResource(ofBudgetTemplate, budget) +
                    if (isOver) " · " + stringResource(R.string.expenses_over_budget_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOver) AccentCoral else OnSurface.copy(0.5f),
            )
        } else {
            Text(stringResource(noBudgetHint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        }
        if (avg != null && budgetPerMeal != null && budgetPerMeal > 0) {
            // Same cent-rounded comparison as isOver above - avg is
            // also a Double average of summed prices, subject to the same drift.
            val over = centsOf(avg) > centsOf(budgetPerMeal)
            Text(
                stringResource(R.string.expenses_avg_per_meal, avg, budgetPerMeal) +
                    if (over) " · " + stringResource(R.string.expenses_over_budget_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) AccentCoral else OnSurface.copy(0.5f),
            )
        }
        // A single-category breakdown just repeats the total above with no new
        // information - only worth showing once spend actually spans categories.
        if (byCategory.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                byCategory.take(3).forEach { (category, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category.displayLabel(), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                        Text("${amount.formatDecimal(2)} €", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    }
                }
            }
        }
    }
}

/** Was falling back to the raw enum key with underscores swapped for spaces
 *  (e.g. "Dairy products"-style English leaking into an otherwise fully French
 *  screen) since no per-category string table existed - this breakdown row is
 *  the only place ProductCategory needs a user-facing label at all. */
@Composable
private fun ProductCategory.displayLabel(): String = stringResource(
    when (this) {
        ProductCategory.SANDWICH          -> R.string.category_sandwich
        ProductCategory.READY_MEAL        -> R.string.category_ready_meal
        ProductCategory.SOUP              -> R.string.category_soup
        ProductCategory.BREAD             -> R.string.category_bread
        ProductCategory.BREAKFAST_CEREAL  -> R.string.category_breakfast_cereal
        ProductCategory.YOGURT            -> R.string.category_yogurt
        ProductCategory.CHEESE            -> R.string.category_cheese
        ProductCategory.PROCESSED_MEAT    -> R.string.category_processed_meat
        ProductCategory.FRESH_MEAT        -> R.string.category_fresh_meat
        ProductCategory.FISH              -> R.string.category_fish
        ProductCategory.SNACK_SWEET       -> R.string.category_snack_sweet
        ProductCategory.SNACK_SALTY       -> R.string.category_snack_salty
        ProductCategory.BEVERAGE_SOFT     -> R.string.category_beverage_soft
        ProductCategory.BEVERAGE_JUICE    -> R.string.category_beverage_juice
        ProductCategory.BEVERAGE_WATER    -> R.string.category_beverage_water
        ProductCategory.CONDIMENT         -> R.string.category_condiment
        ProductCategory.OIL_FAT           -> R.string.category_oil_fat
        ProductCategory.OTHER             -> R.string.category_other
    },
)

@Composable
private fun ExpenseEntryRow(entry: PriceEntry, dateFmt: DateTimeFormatter, onEdit: () -> Unit, onDelete: () -> Unit) {
    // Tapping the row now opens the edit dialog - previously the only action
    // available on a logged entry was delete, so a mistyped price or name
    // could only be fixed by deleting and re-adding it from scratch.
    ScanEatCard(contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), onClick = onEdit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnBackground, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    Text("${entry.priceEuros.formatDecimal(2)} €", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    entry.pricePerKg?.let {
                        Text("${it.formatDecimal(2)} €/kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                    }
                    entry.valueScore?.let { score ->
                        val (label, color) = when (score) {
                            ValueScore.GREAT   -> stringResource(R.string.result_price_value_great) to semanticGreen()
                            ValueScore.GOOD    -> stringResource(R.string.result_price_value_good) to semanticGreen()
                            ValueScore.AVERAGE -> stringResource(R.string.result_price_value_average) to semanticAmber()
                            ValueScore.POOR    -> stringResource(R.string.result_price_value_poor) to AccentCoral
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f))
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    dailyInitial: Double?,
    weeklyInitial: Double?,
    monthlyInitial: Double?,
    perMealInitial: Double?,
    onConfirm: (daily: Double?, weekly: Double?, monthly: Double?, perMeal: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var dailyText by remember { mutableStateOf(dailyInitial?.formatDecimal(0) ?: "") }
    var weeklyText by remember { mutableStateOf(weeklyInitial?.formatDecimal(0) ?: "") }
    var monthlyText by remember { mutableStateOf(monthlyInitial?.formatDecimal(0) ?: "") }
    var perMealText by remember { mutableStateOf(perMealInitial?.formatDecimal(0) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_edit_budget), color = OnBackground) },
        text = {
            // Scrollable - four fields (was two) previously risked clipping on a
            // small screen with no way to reach the Save button below the fold.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                // Neither field previously explained what leaving it blank does (both
                // are optional and silently skip that budget check entirely) or gave
                // an example value - a first-time user had to guess.
                OutlinedTextField(
                    value = dailyText, onValueChange = { dailyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_daily_label)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_daily_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = weeklyText, onValueChange = { weeklyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_weekly_label)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_weekly_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = monthlyText, onValueChange = { monthlyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_monthly_label)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_monthly_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = perMealText, onValueChange = { perMealText = it },
                    label = { Text(stringResource(R.string.expenses_budget_per_meal_label)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_per_meal_hint)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Bounded, not just "> 0" - an unbounded budget target previously fed
                // straight into the weekly-spend percentage/remaining-budget math shown
                // on this screen and the Dashboard recap card, risking an absurd
                // displayed percentage from a mistyped value. Daily/monthly bounds
                // scale from the same per-meal/weekly ranges (daily ≈ a few meals,
                // monthly ≈ 4x weekly's ceiling).
                onConfirm(
                    dailyText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.5..2000.0 },
                    weeklyText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..10000.0 },
                    monthlyText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..40000.0 },
                    perMealText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.5..2000.0 },
                )
            }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

/** Manual "no barcode behind this purchase" entry - see ExpensesViewModel.addEntry's
 *  own doc comment on the gap this closes. Same bounded-input discipline as
 *  PriceEntryCard's PriceInputDialog (result/cards/PriceEntryCard.kt), which this
 *  intentionally mirrors rather than diverging on validation rules. */
@Composable
private fun AddExpenseDialog(onConfirm: (name: String, category: ProductCategory, price: Double, weight: Double?) -> Unit, onDismiss: () -> Unit) {
    var nameText by remember { mutableStateOf("") }
    // Manual entries previously always landed in ProductCategory.OTHER with no
    // way to pick a real category - see ExpensesViewModel.addEntry's own doc
    // comment. That silently skewed the spend-by-category breakdown above
    // toward OTHER for every purchase not tied to a barcode scan.
    var category by remember { mutableStateOf(ProductCategory.OTHER) }
    var priceText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    val price = priceText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.01..9999.99 }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..50000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_add_entry), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(
                    value = nameText, onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.expenses_add_entry_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                ExpenseCategoryPicker(category = category, onCategoryChange = { category = it })
                // Was giving no visual feedback for an out-of-range value - the Save
                // button just silently stayed disabled, unlike AddWeightDialog/
                // MedicationReminderDialog's identical isError pattern for the same
                // "typed something, but it's out of bounds" case.
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.result_price_field_euros)) },
                    singleLine = true,
                    isError = priceText.isNotBlank() && price == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = weightText, onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.result_price_field_weight)) },
                    singleLine = true,
                    isError = weightText.isNotBlank() && weight == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Text(stringResource(R.string.result_price_field_weight_hint), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { price?.let { onConfirm(nameText.trim(), category, it, weight) } },
                enabled = nameText.isNotBlank() && price != null,
            ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

/** Pre-filled AddExpenseDialog twin for correcting an already-logged entry -
 *  see ExpensesViewModel.editEntry's own doc comment on why this is a separate
 *  update path rather than delete-then-re-add. Keeps the entry's original date;
 *  only name/category/price/weight are editable here. */
@Composable
private fun EditExpenseDialog(
    entry: PriceEntry,
    onConfirm: (name: String, category: ProductCategory, price: Double, weight: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameText by remember { mutableStateOf(entry.productName) }
    var category by remember { mutableStateOf(entry.category) }
    var priceText by remember { mutableStateOf(entry.priceEuros.formatDecimal(2)) }
    var weightText by remember { mutableStateOf(entry.weightG?.formatDecimal(0) ?: "") }
    val price = priceText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.01..9999.99 }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..50000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_edit_entry_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(
                    value = nameText, onValueChange = { nameText = it },
                    label = { Text(stringResource(R.string.expenses_add_entry_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                ExpenseCategoryPicker(category = category, onCategoryChange = { category = it })
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.result_price_field_euros)) },
                    singleLine = true,
                    isError = priceText.isNotBlank() && price == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = weightText, onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.result_price_field_weight)) },
                    singleLine = true,
                    isError = weightText.isNotBlank() && weight == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Text(stringResource(R.string.result_price_field_weight_hint), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { price?.let { onConfirm(nameText.trim(), category, it, weight) } },
                enabled = nameText.isNotBlank() && price != null,
            ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

/** Compact "Catégorie : <current>" trigger + popup menu - same shape as
 *  CollapsibleFilterBar (ui/theme/CollapsibleFilterBar.kt), reused here instead
 *  of a FlowRow of 18 chips, which would make this modal dialog unreasonably
 *  tall for a field that's secondary to name/price/weight. */
@Composable
private fun ExpenseCategoryPicker(category: ProductCategory, onCategoryChange: (ProductCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Text(stringResource(R.string.expenses_add_entry_category_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(CardRadius.CONTROL),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(category.displayLabel(), color = OnBackground)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(CardRadius.CONTROL),
                containerColor = SurfaceVariant.copy(alpha = 0.94f),
                shadowElevation = 0.dp,
                modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)),
                offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = Spacing.T2),
            ) {
                ProductCategory.entries.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.displayLabel()) },
                        trailingIcon = { if (c == category) Icon(TablerIcons.Check, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact)) },
                        onClick = { onCategoryChange(c); expanded = false },
                    )
                }
            }
        }
    }
}
