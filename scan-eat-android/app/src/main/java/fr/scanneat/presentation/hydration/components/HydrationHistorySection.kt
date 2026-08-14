package fr.scanneat.presentation.hydration.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Droplet
import compose.icons.tablericons.Edit
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Day-level history list - see HydrationViewModel.history's own doc comment on
 * why this is one row per DAY (a running total), not per individual glass.
 * Previously hydration had no history view at all: a mistaken tap could only
 * be corrected by decrementing *today's* count via removeGlass(), and any
 * past day's total was neither visible nor fixable.
 */
@Composable
internal fun HydrationHistorySection(
    history: List<Pair<LocalDate, Int>>,
    dateFmt: DateTimeFormatter,
    useImperial: Boolean,
    onEdit: (LocalDate, Int) -> Unit,
    onDelete: (LocalDate) -> Unit,
) {
    // Previously returned nothing at all when empty - a new user had no way to
    // discover this section (day-level edit/delete of past totals) exists,
    // unlike every sibling history list (Weight/Recipes/Templates/Grocery)
    // which shows an EmptyListState instead of vanishing.
    if (history.isEmpty()) {
        EmptyListState(TablerIcons.Droplet, stringResource(R.string.hydration_history_empty))
        return
    }
    // Title + CSV export shortcut are rendered by the caller (HydrationScreen),
    // matching ExpensesScreen's "Historique" header + export icon pattern -
    // this composable only owns the list itself.
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        history.forEach { (date, ml) ->
            HydrationHistoryRow(date = date, ml = ml, dateFmt = dateFmt, useImperial = useImperial, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun HydrationHistoryRow(date: LocalDate, ml: Int, dateFmt: DateTimeFormatter, useImperial: Boolean, onEdit: (LocalDate, Int) -> Unit, onDelete: (LocalDate) -> Unit) {
    var showEdit by remember { mutableStateOf(false) }
    // Whole-row tap opens the edit dialog, same pattern as WeightEntryRow/
    // ExpenseEntryRow - the delete icon stays a separate, smaller tap target
    // inside it so a stray tap doesn't silently wipe a day's log.
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), onClick = { showEdit = true }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(dateFmt), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dispVolume(ml, useImperial), style = MaterialTheme.typography.bodyMedium, color = OnSurface.copy(0.7f))
                IconButton(onClick = { showEdit = true }) {
                    Icon(TablerIcons.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Small))
                }
                IconButton(onClick = { onDelete(date) }) {
                    Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Small))
                }
            }
        }
    }
    if (showEdit) {
        HydrationHistoryEditDialog(
            date = date, dateFmt = dateFmt, initialMl = ml, useImperial = useImperial,
            onConfirm = { newMl -> onEdit(date, newMl); showEdit = false },
            onDismiss = { showEdit = false },
        )
    }
}

// §A5-audit finding: this dialog always edited/labeled the raw mL value
// ("Total (mL)") regardless of useImperial, even though the row it opens
// from already displays "24 fl oz" via dispVolume() when the setting is on
// - an imperial user editing a day's total saw a mL figure with no unit
// conversion or matching label, unlike WeightLogDialogs' AddWeightDialog
// which already converts correctly for kg/lb. Now edits in whichever unit
// the row itself displays, converting back to mL only for onConfirm/storage.
@Composable
private fun HydrationHistoryEditDialog(date: LocalDate, dateFmt: DateTimeFormatter, initialMl: Int, useImperial: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (useImperial) (initialMl * ML_TO_FLOZ).roundToInt().toString() else initialMl.toString()) }
    val maxNative = if (useImperial) (20000 * ML_TO_FLOZ).roundToInt() else 20000
    val typed = text.toIntOrNull()?.takeIf { it in 0..maxNative }
    val ml = typed?.let { if (useImperial) (it / ML_TO_FLOZ).roundToInt() else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hydration_history_edit_title, date.format(dateFmt)), color = OnBackground) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(if (useImperial) R.string.hydration_history_edit_label_imperial else R.string.hydration_history_edit_label)) },
                singleLine = true,
                isError = text.isNotBlank() && typed == null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { ml?.let(onConfirm) }, enabled = ml != null) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
