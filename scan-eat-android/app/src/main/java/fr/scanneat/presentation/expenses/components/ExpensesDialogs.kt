package fr.scanneat.presentation.expenses.components

import compose.icons.tablericons.Check
import compose.icons.TablerIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

@Composable
internal fun BudgetEditDialog(
    dailyInitial: Double?,
    weeklyInitial: Double?,
    monthlyInitial: Double?,
    perMealInitial: Double?,
    currencySymbol: String,
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
                    label = { Text(stringResource(R.string.expenses_budget_daily_label, currencySymbol)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_daily_hint, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = weeklyText, onValueChange = { weeklyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_weekly_label, currencySymbol)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_weekly_hint, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = monthlyText, onValueChange = { monthlyText = it },
                    label = { Text(stringResource(R.string.expenses_budget_monthly_label, currencySymbol)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_monthly_hint, currencySymbol)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = perMealText, onValueChange = { perMealText = it },
                    label = { Text(stringResource(R.string.expenses_budget_per_meal_label, currencySymbol)) },
                    supportingText = { Text(stringResource(R.string.expenses_budget_per_meal_hint, currencySymbol)) },
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
internal fun AddExpenseDialog(onConfirm: (name: String, category: ProductCategory, price: Double, weight: Double?) -> Unit, onDismiss: () -> Unit) {
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
internal fun EditExpenseDialog(
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
internal fun ExpenseCategoryPicker(category: ProductCategory, onCategoryChange: (ProductCategory) -> Unit) {
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
