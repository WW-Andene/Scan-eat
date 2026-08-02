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
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** [embedded] mirrors WeightScreen/ActivityScreen/etc. - see their own doc comments. */
@Composable
fun ExpensesScreen(
    viewModel: ExpensesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    embeddedBottomPadding: Dp = 0.dp,
    embeddedTopPadding: Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries = viewModel.entries.collectAsStateWithLifecycle()
    val weekTotal = viewModel.weekTotal.collectAsStateWithLifecycle()
    val budgetWeekly = viewModel.budgetWeeklyEuros.collectAsStateWithLifecycle()
    val budgetPerMeal = viewModel.budgetPerMealEuros.collectAsStateWithLifecycle()
    val avgPerEntry = viewModel.avgPerEntryThisWeek.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var showBudgetEdit by remember { mutableStateOf(false) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }

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
                onEditBudget = { showBudgetEdit = true },
                onOpenCalendar = onOpenCalendar,
            )
        }

        item {
            Text(
                stringResource(R.string.expenses_history_title),
                style = MaterialTheme.typography.titleSmall,
                color = OnBackground,
                fontWeight = FontWeight.SemiBold,
            )
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
}

@Composable
private fun ExpensesWeekCard(
    weekTotal: Double,
    budgetWeekly: Double?,
    avgPerEntry: Double?,
    budgetPerMeal: Double?,
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
            String.format(Locale.getDefault(), "%.2f €", weekTotal),
            style = MaterialTheme.typography.headlineMedium,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        if (budgetWeekly != null && budgetWeekly > 0) {
            val pct = (weekTotal / budgetWeekly).toFloat().coerceIn(0f, 1.5f)
            LinearProgressIndicator(
                progress = { pct.coerceAtMost(1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (pct > 1f) AccentCoral else semanticGreen(),
                trackColor = OnSurface.copy(0.1f),
            )
            Text(
                stringResource(R.string.expenses_of_budget, budgetWeekly),
                style = MaterialTheme.typography.labelSmall,
                color = if (pct > 1f) AccentCoral else OnSurface.copy(0.5f),
            )
        } else {
            Text(stringResource(R.string.expenses_no_budget_hint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        }
        if (avgPerEntry != null && budgetPerMeal != null && budgetPerMeal > 0) {
            val over = avgPerEntry > budgetPerMeal
            Text(
                stringResource(R.string.expenses_avg_per_meal, avgPerEntry, budgetPerMeal),
                style = MaterialTheme.typography.labelSmall,
                color = if (over) AccentCoral else OnSurface.copy(0.5f),
            )
        }
    }
}

@Composable
private fun ExpenseEntryRow(entry: PriceEntry, dateFmt: DateTimeFormatter, onDelete: () -> Unit) {
    ScanEatCard(contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnBackground, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    Text(String.format(Locale.getDefault(), "%.2f €", entry.priceEuros), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    entry.pricePerKg?.let {
                        Text(String.format(Locale.getDefault(), "%.2f €/kg", it), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
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
    var weeklyText by remember { mutableStateOf(weeklyInitial?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var perMealText by remember { mutableStateOf(perMealInitial?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
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
                onConfirm(
                    weeklyText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
                    perMealText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 },
                )
            }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
