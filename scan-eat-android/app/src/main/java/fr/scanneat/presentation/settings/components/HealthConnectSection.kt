package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.HealthConnectAvailability
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HealthConnectSection(availability: HealthConnectAvailability, connected: Boolean, onConnect: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_health_connect)) {
        Text(stringResource(R.string.settings_healthconnect_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        when (availability) {
            HealthConnectAvailability.AVAILABLE -> {
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_healthconnect_connected), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
                    }
                } else {
                    ScanEatOutlinedButton(onClick = onConnect) {
                        Icon(Icons.Default.MonitorHeart, null, tint = OnBackground, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_healthconnect_connect_button), color = OnBackground)
                    }
                }
            }
            HealthConnectAvailability.NOT_INSTALLED -> Text(stringResource(R.string.settings_healthconnect_not_installed), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            HealthConnectAvailability.UNSUPPORTED   -> Text(stringResource(R.string.settings_healthconnect_unsupported), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        }
    }
}
