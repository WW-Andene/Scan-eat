package fr.scanneat.presentation.reminders.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

@Composable
internal fun AddCustomReminderDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var time  by remember { mutableStateOf("09:00") }
    val timeValid = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.reminders_add_custom), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text(stringResource(R.string.reminders_custom_label_hint)) },
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, singleLine = true,
                    isError = !timeValid,
                    label = { Text(stringResource(R.string.reminders_custom_time_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label, time) }, enabled = label.isNotBlank() && timeValid) {
                Text(stringResource(R.string.common_add), color = if (label.isNotBlank() && timeValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
