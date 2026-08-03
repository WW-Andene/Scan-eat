package fr.scanneat.presentation.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val weekTotal = viewModel.weekTotal.collectAsStateWithLifecycle()
    val budgetWeekly = viewModel.budgetWeeklyEuros.collectAsStateWithLifecycle()
    val budgetPerMeal = viewModel.budgetPerMealEuros.collectAsStateWithLifecycle()
    val avgPerEntry = viewModel.avgPerEntryThisWeek.collectAsStateWithLifecycle()
    val spendByCategory = viewModel.spendByCategory.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showBudgetEdit by remember { mutableStateOf(false) }
    var showAddEntry by remember { mutableStateOf(false) }
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
            contentPadding = PaddingValues(top = embeddedTopPadding, bottom = embeddedBottomPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            item {
                ExpensesWeekCard(
                    weekTotal = weekTotal.value,
                    budgetWeekly = budgetWeekly.value,
                    avgPerEntry = avgPerEntry.value,
                    budgetPerMeal = budgetPerMeal.value,
                    spendByCategory = spendByCategory.value,
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
                    // Previously the ONLY way to log a price_log row at all was
                    // ResultViewModel's barcode-scan flow - a cash purchase or
                    // anything without a barcode could never be tracked. See
                    // ExpensesViewModel.addEntry's own doc comment.
                    TextButton(onClick = { showAddEntry = true }) {
                        Text(stringResource(R.string.expenses_add_entry), color = AccentCoral, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (entries.value.isEmpty()) {
                item { EmptyListState(Icons.Rounded.Receipt, stringResource(R.string.expenses_empty_body)) }
            } else {
                items(entries.value, key = { it.id }) { entry ->
                    ExpenseEntryRow(entry = entry, dateFmt = dateFmt, onDelete = { deleteTarget = entry.id })
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
            weeklyInitial = budgetWeekly.value,
            perMealInitial = budgetPerMeal.value,
            onConfirm = { weekly, perMeal ->
                viewModel.setBudgetWeekly(weekly)
                viewModel.setBudgetPerMeal(perMeal)
                showBudgetEdit = false
            },
            onDismiss = { showBudgetEdit = false },
        )
    }

    if (showAddEntry) {
        AddExpenseDialog(
            onConfirm = { name, price, weight ->
                viewModel.addEntry(LocalDate.now(), name, price, weight)
                showAddEntry = false
            },
            onDismiss = { showAddEntry = false },
        )
    }
}

/** Rounds a euro amount to whole cents for an exact over/under-budget comparison -
 *  see ExpensesWeekCard's own comment on why a raw Double `>` isn't safe here. */
private fun centsOf(euros: Double): Long = Math.round(euros * 100)

@Composable
private fun ExpensesWeekCard(
    weekTotal: Double,
    budgetWeekly: Double?,
    avgPerEntry: Double?,
    budgetPerMeal: Double?,
    spendByCategory: List<Pair<ProductCategory, Double>>,
    onEditBudget: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
                    Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.expenses_cd_calendar), tint = OnSurface.copy(0.5f))
                }
                Spacer(Modifier.width(Spacing.XS))
                Text(stringResource(R.string.expenses_week_title), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f))
            }
            TextButton(onClick = onEditBudget) {
                Text(stringResource(R.string.expenses_edit_budget), color = AccentCoral, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            "${weekTotal.formatDecimal(2)} €",
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        if (budgetWeekly != null && budgetWeekly > 0) {
            val pct = (weekTotal / budgetWeekly).toFloat().coerceIn(0f, 1.5f)
            // weekTotal is a Double sum of per-entry prices, so a spend that's
            // mathematically exactly at budget can drift a fraction of a cent
            // either way from float summation - comparing to the nearest cent
            // (not a raw > 1f/Double >) avoids a budget met to the cent
            // flip-flopping between "over" and "under" depending on entry order.
            val isOverWeekly = centsOf(weekTotal) > centsOf(budgetWeekly)
            LinearProgressIndicator(
                progress = { pct.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (isOverWeekly) AccentCoral else semanticGreen(),
                trackColor = OnSurface.copy(0.1f),
            )
            Text(
                stringResource(R.string.expenses_of_budget, budgetWeekly),
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverWeekly) AccentCoral else OnSurface.copy(0.5f),
            )
        } else {
            Text(stringResource(R.string.expenses_no_budget_hint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        }
        if (avgPerEntry != null && budgetPerMeal != null && budgetPerMeal > 0) {
            // Same cent-rounded comparison as isOverWeekly above - avgPerEntry is
            // also a Double average of summed prices, subject to the same drift.
            val over = centsOf(avgPerEntry) > centsOf(budgetPerMeal)
            Text(
                stringResource(R.string.expenses_avg_per_meal, avgPerEntry, budgetPerMeal),
                style = MaterialTheme.typography.labelSmall,
                color = if (over) AccentCoral else OnSurface.copy(0.5f),
            )
        }
        // A single-category breakdown just repeats the total above with no new
        // information - only worth showing once spend actually spans categories.
        if (spendByCategory.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                spendByCategory.take(3).forEach { (category, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category.displayLabel(), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                        Text("${amount.formatDecimal(2)} €", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    }
                }
            }
        }
    }
}

/** No dedicated per-category string table exists for ProductCategory anywhere in
 *  the app (it's a scoring-engine classification, not a user-facing label
 *  elsewhere) - a readable fallback derived from the enum key rather than adding
 *  18 new translated strings just for this one breakdown row. */
private fun ProductCategory.displayLabel(): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun ExpenseEntryRow(entry: PriceEntry, dateFmt: DateTimeFormatter, onDelete: () -> Unit) {
    ScanEatCard(contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S)) {
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
                Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f))
            }
        }
    }
}

@Composable
private fun BudgetEditDialog(
    weeklyInitial: Double?,
    perMealInitial: Double?,
    onConfirm: (Double?, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    var weeklyText by remember { mutableStateOf(weeklyInitial?.formatDecimal(0) ?: "") }
    var perMealText by remember { mutableStateOf(perMealInitial?.formatDecimal(0) ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expenses_edit_budget), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(
                    value = weeklyText, onValueChange = { weeklyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_weekly_label)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = perMealText, onValueChange = { perMealText = it },
                    label = { Text(stringResource(R.string.expenses_budget_per_meal_label)) },
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
                // displayed percentage from a mistyped value.
                onConfirm(
                    weeklyText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 1.0..10000.0 },
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
private fun AddExpenseDialog(onConfirm: (name: String, price: Double, weight: Double?) -> Unit, onDismiss: () -> Unit) {
    var nameText by remember { mutableStateOf("") }
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
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.result_price_field_euros)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = weightText, onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.result_price_field_weight)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Text(stringResource(R.string.result_price_field_weight_hint), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { price?.let { onConfirm(nameText.trim(), it, weight) } },
                enabled = nameText.isNotBlank() && price != null,
            ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
