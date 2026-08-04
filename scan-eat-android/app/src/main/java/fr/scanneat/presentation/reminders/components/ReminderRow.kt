package fr.scanneat.presentation.reminders.components

import compose.icons.tablericons.Bell
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
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
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

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
    // The bell silently no-ops when POST_NOTIFICATIONS is denied (NotificationHelper.show()
    // is a no-op without it) - a tapped bell that "does nothing" with zero explanation
    // left the user unsure whether their reminder was even configured correctly.
    val (permGranted, _, _) = permissionState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onLabelChange != null) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it; onLabelChange(it) },
                    placeholder = { Text(defaultLabel, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f)) },
                    modifier = Modifier.weight(1f).padding(end = Spacing.S),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
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
                colors = scanEatTextFieldColors(),
            )
            IconButton(onClick = onTest, enabled = permGranted) {
                Icon(TablerIcons.Bell, stringResource(R.string.reminders_cd_test, displayLabel), tint = if (permGranted) AccentCoral else OnBackground.copy(0.3f))
            }
            Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
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
    val (permGranted, _, _) = permissionState()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it; onUpdate(reminder.copy(label = it)) },
            modifier = Modifier.weight(1f).padding(end = Spacing.S),
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
            colors = scanEatTextFieldColors(),
        )
        // Both icon-only buttons previously had a null contentDescription - a
        // TalkBack user got no signal at all for what "test" or "delete" would do.
        IconButton(onClick = onTest, enabled = permGranted) { Icon(TablerIcons.Bell, stringResource(R.string.reminders_cd_test, labelText.ifBlank { reminder.label }), tint = if (permGranted) AccentCoral else OnBackground.copy(0.3f)) }
        Switch(checked = reminder.on, onCheckedChange = { onUpdate(reminder.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            modifier = Modifier.semantics { contentDescription = labelText })
        IconButton(onClick = onDelete) { Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Small)) }
    }
}
