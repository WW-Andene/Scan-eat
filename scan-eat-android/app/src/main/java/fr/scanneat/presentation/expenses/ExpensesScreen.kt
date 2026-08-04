package fr.scanneat.presentation.expenses

import compose.icons.tablericons.FileInvoice
import compose.icons.TablerIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.presentation.expenses.components.AddExpenseDialog
import fr.scanneat.presentation.expenses.components.BudgetEditDialog
import fr.scanneat.presentation.expenses.components.EditExpenseDialog
import fr.scanneat.presentation.expenses.components.ExpenseEntryRow
import fr.scanneat.presentation.expenses.components.ExpensesSummaryMode
import fr.scanneat.presentation.expenses.components.ExpensesWeekCard
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val currencySymbol = viewModel.currencySymbol.collectAsStateWithLifecycle()
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
                    currencySymbol = currencySymbol.value,
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
                    ExpenseEntryRow(entry = entry, dateFmt = dateFmt, currencySymbol = currencySymbol.value, onEdit = { editTarget = entry }, onDelete = { deleteTarget = entry.id })
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
            currencySymbol = currencySymbol.value,
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
