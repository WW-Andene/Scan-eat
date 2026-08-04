package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeSection(
    theme: String, onThemeChange: (String) -> Unit,
    animatedBackground: Boolean, onAnimatedBackgroundChange: (Boolean) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_theme), icon = Icons.Default.Palette) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "system" to stringResource(R.string.settings_theme_system),
                "oled" to stringResource(R.string.settings_theme_oled),
                "dark" to stringResource(R.string.settings_theme_dark),
                "light" to stringResource(R.string.settings_theme_light),
                "high_contrast" to stringResource(R.string.settings_theme_high_contrast),
                "low_contrast" to stringResource(R.string.settings_theme_low_contrast),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = theme == key,
                    onClick  = { onThemeChange(key) },
                    // User-reported: uneven gap between chips here vs. the
                    // colorblind-mode row below (AccessibilitySection.kt), which
                    // already forces maxLines = 1 - this row's longer labels
                    // ("Contraste élevé"/"faible") could wrap to a second line,
                    // making that chip taller than its neighbors and the
                    // identical Spacing.S gap read uneven. Matched.
                    label    = { Text(label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        Spacer(Modifier.height(Spacing.S))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_animated_background), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(R.string.settings_animated_background_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            Switch(
                checked = animatedBackground,
                onCheckedChange = onAnimatedBackgroundChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            )
        }
    }
}
