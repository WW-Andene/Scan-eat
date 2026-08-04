package fr.scanneat.presentation.hydration.components

import compose.icons.TablerIcons
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
    if (history.isEmpty()) return
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
            date = date, dateFmt = dateFmt, initialMl = ml,
            onConfirm = { newMl -> onEdit(date, newMl); showEdit = false },
            onDismiss = { showEdit = false },
        )
    }
}

@Composable
private fun HydrationHistoryEditDialog(date: LocalDate, dateFmt: DateTimeFormatter, initialMl: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var mlText by remember { mutableStateOf(initialMl.toString()) }
    val ml = mlText.toIntOrNull()?.takeIf { it in 0..20000 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hydration_history_edit_title, date.format(dateFmt)), color = OnBackground) },
        text = {
            OutlinedTextField(
                value = mlText, onValueChange = { mlText = it },
                label = { Text(stringResource(R.string.hydration_history_edit_label)) },
                singleLine = true,
                isError = mlText.isNotBlank() && ml == null,
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
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
