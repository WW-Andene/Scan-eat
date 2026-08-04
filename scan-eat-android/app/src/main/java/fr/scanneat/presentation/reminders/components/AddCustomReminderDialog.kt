package fr.scanneat.presentation.reminders.components

import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.TextMuted
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticRed

@Composable
internal fun AddCustomReminderDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var time  by remember { mutableStateOf("09:00") }
    val timeValid = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = 0.94f),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.reminders_add_custom), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                // Was no example anywhere for what to type here (unlike the time field's
                // sensible pre-filled "09:00" default) - a first-time user had no cue what
                // kind of thing a "custom reminder" is meant to hold.
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text(stringResource(R.string.reminders_custom_label_hint)) },
                    placeholder = { Text(stringResource(R.string.reminders_custom_label_example), color = TextMuted) },
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, singleLine = true,
                    isError = !timeValid,
                    label = { Text(stringResource(R.string.reminders_custom_time_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = scanEatTextFieldColors(),
                )
                // Turning the field red on invalid input with no explanation of why -
                // same gap StartFastForm's custom time fields had, already fixed there
                // with this identical inline-hint pattern.
                if (!timeValid) {
                    Text(stringResource(R.string.fasting_custom_time_error), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                }
            }
        },
        confirmButton = {
            // Was saving the raw untrimmed label - a " Meds " reminder displayed with
            // stray whitespace in every row, the "next reminder" chip, and the fired
            // notification's own title. Same trim fix as RenameDialog/AddTemplateDialog.
            val labelValid = label.trim().isNotBlank()
            TextButton(onClick = { onConfirm(label.trim(), time) }, enabled = labelValid && timeValid) {
                Text(stringResource(R.string.common_add), color = if (labelValid && timeValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
