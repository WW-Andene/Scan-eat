package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * R&D §X.0: Biolism's Data tab has 14 cards, several research-grade (substrate
 * flux/RQ, Fanger thermoregulation, raw ventilation physiology, hormone
 * estimates, formula sheets). This toggle lets a user collapse to the
 * essentials (BMR, body composition, energy, macros, sessions) instead of the
 * full scientific view — defaults to on (advanced/full), so existing users see
 * no change unless they opt out.
 */
@Composable
internal fun BiolismDisplaySection(advancedView: Boolean, onChange: (Boolean) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_biolism_display), icon = Icons.Default.MonitorHeart) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_biolism_advanced), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(R.string.settings_biolism_advanced_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            Switch(
                checked = advancedView,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            )
        }
    }
}
