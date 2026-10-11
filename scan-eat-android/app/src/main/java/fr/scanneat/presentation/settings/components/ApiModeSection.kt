package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ApiModeSection(mode: ApiMode, onModeChange: (ApiMode) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_api_mode), icon = Icons.Default.SettingsEthernet) {
        Text(
            stringResource(R.string.settings_api_mode_hint),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ApiMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick  = { onModeChange(m) },
                    label    = { Text(if (m == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct) else stringResource(R.string.settings_mode_server)) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        Text(
            if (mode == ApiMode.DIRECT) stringResource(R.string.settings_mode_direct_desc)
            else stringResource(R.string.settings_mode_server_desc),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
    }
}
