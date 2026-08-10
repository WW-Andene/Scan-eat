package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Violet

/** BiolismProfileScreen's female-only cycle-day section. Extracted (§T1 composition-root split). */
@Composable
internal fun BiolismCycleSection(cycleDay: String, onCycleDayChange: (String) -> Unit) {
    ProfileSection(stringResource(R.string.bioprofile_section_cycle)) {
        Text(stringResource(R.string.bioprofile_cycle_hint),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        BioInput(stringResource(R.string.bioprofile_field_cycle_day), cycleDay, KeyboardType.Number) { v -> if (v.toIntOrNull()?.let { it in 1..28 } != false) onCycleDayChange(v) }
        Slider(value = (cycleDay.toIntOrNull() ?: 14).toFloat(), onValueChange = { onCycleDayChange(it.toInt().toString()) },
            valueRange = 1f..28f, steps = 26, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
        val cd = cycleDay.toIntOrNull() ?: 14
        val phaseLabel = when {
            cd <= 5  -> stringResource(R.string.bioprofile_phase_menstrual)
            cd <= 13 -> stringResource(R.string.bioprofile_phase_follicular)
            cd == 14 -> stringResource(R.string.bioprofile_phase_ovulation)
            cd <= 21 -> stringResource(R.string.bioprofile_phase_luteal_early)
            else     -> stringResource(R.string.bioprofile_phase_luteal_late)
        }
        Text(stringResource(R.string.bioprofile_cycle_day_summary, cd, phaseLabel), style = MaterialTheme.typography.bodySmall, color = Violet)
    }
}
