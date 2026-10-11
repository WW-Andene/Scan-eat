package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun RemindersSection(onOpenReminders: () -> Unit) {
    SettingsSection(stringResource(R.string.reminders_title), icon = Icons.Default.Notifications) {
        Text(stringResource(R.string.settings_reminders_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(
            onClick = onOpenReminders,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Notifications, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_reminders_button), color = OnBackground)
        }
    }
}
