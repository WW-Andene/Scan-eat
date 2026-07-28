package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSection(theme: String, onThemeChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_theme), icon = Icons.Default.Palette) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "oled" to stringResource(R.string.settings_theme_oled),
                "dark" to stringResource(R.string.settings_theme_dark),
                "light" to stringResource(R.string.settings_theme_light),
                "high_contrast" to stringResource(R.string.settings_theme_high_contrast),
                "low_contrast" to stringResource(R.string.settings_theme_low_contrast),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = theme == key,
                    onClick  = { onThemeChange(key) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}
