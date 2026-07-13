package fr.scanneat.presentation.reminders.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.reminders.CustomReminder
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground

@Composable
internal fun ReminderRow(
    defaultLabel: String,
    customLabel: String = "",
    on: Boolean, time: String,
    onToggle: (Boolean) -> Unit, onTimeChange: (String) -> Unit,
    onLabelChange: ((String) -> Unit)? = null,
    onTest: () -> Unit,
) {
    var timeText  by remember(time) { mutableStateOf(time) }
    var labelText by remember(customLabel) { mutableStateOf(customLabel) }
    val isValid = remember(timeText) { runCatching { java.time.LocalTime.parse(timeText) }.isSuccess }
    val displayLabel = labelText.ifBlank { defaultLabel }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onLabelChange != null) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it; onLabelChange(it) },
                    placeholder = { Text(defaultLabel, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f)) },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )
            } else {
                Text(displayLabel, style = MaterialTheme.typography.bodyMedium, color = OnBackground, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it; if (runCatching { java.time.LocalTime.parse(it) }.isSuccess) onTimeChange(it) },
                modifier = Modifier.width(90.dp),
                singleLine = true,
                isError = !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
            )
            IconButton(onClick = onTest) {
                Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, displayLabel), tint = Gold)
            }
            Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                modifier = Modifier.semantics { contentDescription = displayLabel })
        }
    }
}

@Composable
internal fun CustomReminderRow(
    reminder: CustomReminder,
    onUpdate: (CustomReminder) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    var labelText by remember(reminder.label) { mutableStateOf(reminder.label) }
    var timeText  by remember(reminder.time)  { mutableStateOf(reminder.time) }
    val isValid = remember(timeText) { runCatching { java.time.LocalTime.parse(timeText) }.isSuccess }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it; onUpdate(reminder.copy(label = it)) },
            modifier = Modifier.weight(1f).padding(end = 6.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it; if (runCatching { java.time.LocalTime.parse(it) }.isSuccess) onUpdate(reminder.copy(time = it)) },
            modifier = Modifier.width(90.dp),
            singleLine = true,
            isError = !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
        )
        IconButton(onClick = onTest) { Icon(Icons.Default.Notifications, null, tint = AccentCoral) }
        Switch(checked = reminder.on, onCheckedChange = { onUpdate(reminder.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            modifier = Modifier.semantics { contentDescription = labelText })
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(16.dp)) }
    }
}
