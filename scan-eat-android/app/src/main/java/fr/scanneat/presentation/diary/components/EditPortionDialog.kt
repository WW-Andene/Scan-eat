package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun EditPortionDialog(entry: DiaryEntry, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by remember(entry.id) { mutableStateOf(entry.portionG.toInt().toString()) }
    val portion = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.productName, color = OnBackground) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(R.string.diary_edit_portion_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { portion?.let { if (it > 0) onConfirm(it) } }, enabled = portion != null && portion > 0) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
    )
}
