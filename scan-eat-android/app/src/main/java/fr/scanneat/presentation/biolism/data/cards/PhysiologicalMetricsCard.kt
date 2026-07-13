package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs
import java.util.Locale

@Composable
fun PhysiologicalMetricsCard(
    met: MetabolicResult,
    profile: BiolismProfile,
    s: TimerState,
    cum: SessionCumulative?,
    manualHR: Int?,
    onSaveManualHR: (Int) -> Unit,
) {
    BioCard(stringResource(R.string.biolism_physio_title), defaultOpen = false, badge = { VioletBadge(stringResource(R.string.biolism_physio_badge)) }) {
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_physio_ve), "%.2f L/min".format(Locale.US, met.vePerMin), "VO₂ / (FiO₂ − FeO₂)"),
            Triple(stringResource(R.string.biolism_physio_hr_estimated), "%.1f bpm".format(Locale.US, met.hrEstimated), "Fick · VS 70mL"),
            Triple(stringResource(R.string.biolism_physio_atp_production), "%.2f mmol/min".format(Locale.US, met.atpMmolPerMin), "Berg 2015"),
            Triple(stringResource(R.string.biolism_physio_metwater), "%.3f g/min".format(Locale.US, met.metWaterPerMin), "Hill 2004"),
        ))
        InfoRow(stringResource(R.string.biolism_physio_n2_excretion), stringResource(R.string.biolism_physio_n2_value,
            met.nExcrGPerDay / 1440.0 * 1000, met.nExcrGPerDay), "", Violet)
        val estGluc = BiolismEngine.computeBloodGlucoseMmol(
            weightKg = profile.weightKg, kcalSec = met.kcalSec, carbFrac = met.sub.carbFrac,
            ketoHours = s.ketoHours, fastingHours = s.fastingHours, ketosis = s.ketosisOn,
            elapsedSec = s.elapsedMs / 1000.0,
        )
        InfoRow(stringResource(R.string.biolism_physio_glucose), "%.2f mmol/L".format(Locale.US, estGluc), stringResource(R.string.biolism_physio_glucose_sub),
            if (estGluc < 3.0) semanticRed() else if (estGluc < 3.9) semanticAmber() else semanticGreen())
        cum?.let { c ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(Violet) {
                Label(stringResource(R.string.biolism_physio_session_cum), Violet)
                InfoRow(stringResource(R.string.biolism_physio_metwater_total), "%.3f g".format(Locale.US, c.metWaterTotalG), "", Teal)
                InfoRow(stringResource(R.string.biolism_physio_atp_total), if (c.atpTotalMmol >= 1000) "%.2f mol".format(Locale.US, c.atpTotalMmol / 1000) else "%.2f mmol".format(Locale.US, c.atpTotalMmol), "", Gold)
                InfoRow(stringResource(R.string.biolism_physio_n2_total), if (c.n2ExcretedMg >= 1000) "%.4f g".format(Locale.US, c.n2ExcretedMg / 1000) else "%.3f mg".format(Locale.US, c.n2ExcretedMg), "", Violet)
            }
        }

        // Manual HR cross-check
        Spacer(Modifier.height(8.dp))
        var hrText by remember { mutableStateOf(manualHR?.toString() ?: "") }
        TintedPanel(Violet) {
            Label(stringResource(R.string.biolism_physio_hr_check_title), Violet)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hrText, onValueChange = { hrText = it; it.toIntOrNull()?.let { bpm -> onSaveManualHR(bpm) } },
                    label = { Text(stringResource(R.string.biolism_physio_hr_input_label)) }, singleLine = true,
                    modifier = Modifier.width(140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )
                manualHR?.let { mhr ->
                    val diff = mhr - met.hrEstimated
                    Column {
                        Text(stringResource(R.string.biolism_physio_hr_diff_value, diff, abs(diff) / met.hrEstimated * 100),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                            color = if (abs(diff) <= 10) semanticGreen() else if (abs(diff) <= 20) semanticAmber() else semanticRed())
                        Text(stringResource(R.string.biolism_physio_sv_implied, met.vo2PerMin * 1000 / (mhr * 0.05)),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                }
            }
        }
    }
}
