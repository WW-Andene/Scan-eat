package fr.scanneat.presentation.diary.components

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.dialogContainerColor
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.label
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun EditPortionDialog(
    entry: DiaryEntry,
    onConfirm: (Double, MealSlot) -> Unit,
    onDismiss: () -> Unit,
    // User-reported: there was no way to add/correct this entry's price from
    // Diary at all, even from this exact "modifier le produit" dialog - only
    // reachable via Result screen's PriceEntryCard or Expenses. null when the
    // entry has no barcode (price is logged per-barcode, see
    // DiaryViewModel.savePrice's own doc comment) - the field simply doesn't
    // show for a manually-added, barcode-less entry.
    onSavePrice: ((priceEuros: Double, weightG: Double?) -> Unit)? = null,
) {
    var text by remember(entry.id) { mutableStateOf(entry.portionG.toInt().toString()) }
    // DiaryViewModel.updateEntry()/ConsumptionRepository.update() already accept a
    // full DiaryEntry, so meal slot could always be corrected in one write - this
    // dialog only ever exposed the portion field, so a common correction ("I
    // logged this as lunch but it was actually a snack") required delete +
    // re-add instead of a one-tap fix.
    var mealSlot by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    var priceText by remember(entry.id) { mutableStateOf("") }
    var weightText by remember(entry.id) { mutableStateOf("") }
    val portion = text.replace(',', '.').toDoubleOrNull()?.let { if (it in 1.0..2000.0) it else null }
    // Same bounds PriceEntryCard's own PriceInputDialog uses.
    val price = priceText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.01..9999.99 }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..50000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.productName, color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                // §A5-audit finding: gave no visual feedback for an out-of-range
                // value on any of its three fields - the Save button just silently
                // stayed disabled, the exact "typed something, but it's out of
                // bounds" gap already fixed elsewhere (AddExpenseDialog's own doc
                // comment on why it added isError, unlike AddWeightDialog/
                // MedicationReminderDialog which already had it).
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(stringResource(R.string.diary_edit_portion_label)) },
                    singleLine = true,
                    isError = text.isNotBlank() && portion == null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MealSlot.entries.forEach { slot ->
                        FilterChip(
                            selected  = mealSlot == slot,
                            onClick   = { mealSlot = slot },
                            label     = { Text(slot.label(), style = MaterialTheme.typography.labelSmall) },
                            colors    = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f),
                                selectedLabelColor     = AccentCoral,
                                labelColor             = OnBackground.copy(0.7f),
                            ),
                        )
                    }
                }
                if (onSavePrice != null) {
                    Text(stringResource(R.string.result_price_title), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    portion?.let { if (it > 0) onConfirm(it, mealSlot) }
                    if (onSavePrice != null) price?.let { onSavePrice(it, weight) }
                },
                enabled = portion != null && portion > 0,
            ) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
