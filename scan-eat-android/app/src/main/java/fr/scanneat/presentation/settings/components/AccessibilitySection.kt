package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccessibilitySection(
    dyslexicFont: Boolean, onDyslexicFontChange: (Boolean) -> Unit,
    colorblindMode: String, onColorblindModeChange: (String) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_section_accessibility), icon = Icons.Default.Accessibility) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_dyslexic_font), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(R.string.settings_dyslexic_font_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            Switch(
                checked = dyslexicFont,
                onCheckedChange = onDyslexicFontChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            )
        }
        Spacer(Modifier.height(Spacing.XS))
        Text(stringResource(R.string.settings_colorblind_mode), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf(
                "none" to stringResource(R.string.settings_colorblind_none),
                "deuteranopia" to stringResource(R.string.settings_colorblind_deuteranopia),
                "protanopia" to stringResource(R.string.settings_colorblind_protanopia),
                "tritanopia" to stringResource(R.string.settings_colorblind_tritanopia),
            ).forEach { (key, label) ->
                FilterChip(
                    selected = colorblindMode == key,
                    onClick  = { onColorblindModeChange(key) },
                    label    = { Text(label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Live preview so the effect of the chosen mode is visible right here,
        // not just later on a scan result.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Grade.entries.forEach { grade ->
                val c = gradeColor(grade)
                Surface(shape = RoundedCornerShape(6.dp), color = c.copy(alpha = 0.2f), shadowElevation = 0.dp, modifier = Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(6.dp), ambientColor = ShadowTint, spotColor = ShadowTint).clip(RoundedCornerShape(6.dp))) {
                    Text(
                        grade.label,
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        style = MaterialTheme.typography.labelSmall, color = c, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
