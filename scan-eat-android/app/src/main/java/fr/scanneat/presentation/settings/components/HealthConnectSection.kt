package fr.scanneat.presentation.settings.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.HealthConnectAvailability
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HealthConnectSection(availability: HealthConnectAvailability, connected: Boolean, onConnect: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_health_connect), icon = Icons.Default.HealthAndSafety) {
        Text(stringResource(R.string.settings_healthconnect_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        when (availability) {
            HealthConnectAvailability.AVAILABLE -> {
                if (connected) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Icon(Icons.Default.Check, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                        Text(stringResource(R.string.settings_healthconnect_connected), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
                    }
                } else {
                    ScanEatOutlinedButton(onClick = onConnect) {
                        Icon(Icons.Default.MonitorHeart, null, tint = OnBackground, modifier = Modifier.size(IconSize.Compact))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_healthconnect_connect_button), color = OnBackground)
                    }
                }
            }
            // Was a passive sentence with no path forward - every other "blocked" state
            // elsewhere in the app (denied camera permission, no camera hardware) gives
            // a concrete next step; this left a motivated user to independently
            // discover and search the Play Store for Health Connect themselves.
            HealthConnectAvailability.NOT_INSTALLED -> {
                val context = LocalContext.current
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(stringResource(R.string.settings_healthconnect_not_installed), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    ScanEatOutlinedButton(onClick = {
                        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                        }
                    }) {
                        Text(stringResource(R.string.settings_healthconnect_install_button), color = OnBackground)
                    }
                }
            }
            HealthConnectAvailability.UNSUPPORTED   -> Text(stringResource(R.string.settings_healthconnect_unsupported), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        }
    }
}
