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
    // User-requested: brightness/contrast (this section) and color accent
    // (ColorSection below) are two different things - the four color themes
    // used to live in this same row, which both mixed the two concepts
    // together in the UI and (see Theme.kt's ColorAccent doc comment) forced
    // OLED's true-black background and a color accent to be mutually
    // exclusive under the hood.
    SettingsSection(stringResource(R.string.settings_section_theme), icon = Icons.Default.Palette) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "system" to stringResource(R.string.settings_theme_system),
                "oled" to stringResource(R.string.settings_theme_oled),
                "dark" to stringResource(R.string.settings_theme_dark),
                "light" to stringResource(R.string.settings_theme_light),
                "high_contrast" to stringResource(R.string.settings_theme_high_contrast),
                "low_contrast" to stringResource(R.string.settings_theme_low_contrast),
                "notebook" to stringResource(R.string.settings_theme_notebook),
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

/**
 * Notebook theme's own display-font choice - only rendered by the caller
 * when `theme == "notebook"` (this section is meaningless for every other
 * theme, which doesn't use a decorative display font at all). Kept to fonts
 * with a confirmed, unambiguous commercial license (Caveat: Google Fonts/
 * SIL OFL; Mayonice and Foxlite Script: Khurasan, "free for personal &
 * commercial use" confirmed in writing) - several other candidate
 * handwriting fonts the user supplied were excluded pending a license
 * decision (see Theme.kt's own history/commit notes), so this list is
 * deliberately short rather than including every font on hand.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotebookFontSection(notebookFont: String, onNotebookFontChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_notebook_font), icon = Icons.Default.Palette) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "caveat" to stringResource(R.string.settings_notebook_font_caveat),
                "mayonice" to stringResource(R.string.settings_notebook_font_mayonice),
                "foxlite" to stringResource(R.string.settings_notebook_font_foxlite),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = notebookFont == key,
                    onClick  = { onNotebookFontChange(key) },
                    label    = { Text(label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}

/**
 * Color accent — independent from [ThemeSection]'s brightness/contrast choice
 * above (see Theme.kt's ColorAccent doc comment for why). Any accent can be
 * combined with any theme, including OLED's true-black background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorSection(colorAccent: String, onColorAccentChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_color), icon = Icons.Default.Palette) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "none" to stringResource(R.string.settings_color_none),
                "matcha" to stringResource(R.string.settings_theme_matcha),
                "lavande" to stringResource(R.string.settings_theme_lavande),
                "sunflower" to stringResource(R.string.settings_theme_sunflower),
                "lazulite" to stringResource(R.string.settings_theme_lazulite),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = colorAccent == key,
                    onClick  = { onColorAccentChange(key) },
                    label    = { Text(label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}
