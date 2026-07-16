package fr.scanneat.presentation.diary.components

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
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.label
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun EditPortionDialog(entry: DiaryEntry, onConfirm: (Double, MealSlot) -> Unit, onDismiss: () -> Unit) {
    var text by remember(entry.id) { mutableStateOf(entry.portionG.toInt().toString()) }
    // DiaryViewModel.updateEntry()/ConsumptionRepository.update() already accept a
    // full DiaryEntry, so meal slot could always be corrected in one write - this
    // dialog only ever exposed the portion field, so a common correction ("I
    // logged this as lunch but it was actually a snack") required delete +
    // re-add instead of a one-tap fix.
    var mealSlot by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    val portion = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.productName, color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text(stringResource(R.string.diary_edit_portion_label)) },
                    singleLine = true,
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
            }
        },
        confirmButton = {
            TextButton(onClick = { portion?.let { if (it > 0) onConfirm(it, mealSlot) } }, enabled = portion != null && portion > 0) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
    )
}
