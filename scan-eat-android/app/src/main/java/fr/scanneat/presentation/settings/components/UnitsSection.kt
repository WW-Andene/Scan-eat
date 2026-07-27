package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun UnitsSection(useImperialWeight: Boolean, onChange: (Boolean) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_units)) {
        Text(stringResource(R.string.settings_units_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(false to stringResource(R.string.bioprofile_unit_metric), true to stringResource(R.string.bioprofile_unit_imperial)).forEach { (imperial, label) ->
                FilterChip(
                    selected = useImperialWeight == imperial,
                    onClick  = { onChange(imperial) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}
