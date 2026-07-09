package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs

@Composable
fun PhysiologicalMetricsCard(
    met: MetabolicResult,
    profile: BiolismProfile,
    s: TimerState,
    cum: SessionCumulative?,
    manualHR: Int?,
    onSaveManualHR: (Int) -> Unit,
) {
    BioCard("Métriques physiologiques", defaultOpen = false, badge = { VioletBadge("LIVE") }) {
        MetCellGrid(listOf(
            Triple("V̇E (ventilation)", "%.2f L/min".format(met.vePerMin), "VO₂ / (FiO₂ − FeO₂)"),
            Triple("FC estimée (repos)", "%.1f bpm".format(met.hrEstimated), "Fick · VS 70mL"),
            Triple("Production ATP", "%.2f mmol/min".format(met.atpMmolPerMin), "Berg 2015"),
            Triple("Eau métabolique", "%.3f g/min".format(met.metWaterPerMin), "Hill 2004"),
        ))
        InfoRow("Excrétion azote (N₂)", "%.4f mg/min · %.2f g/j".format(
            met.nExcrGPerDay / 1440.0 * 1000, met.nExcrGPerDay), "", Violet)
        val estGluc = BiolismEngine.computeBloodGlucoseMmol(
            weightKg = profile.weightKg, kcalSec = met.kcalSec, carbFrac = met.sub.carbFrac,
            ketoHours = s.ketoHours, fastingHours = s.fastingHours, ketosis = s.ketosisOn,
            elapsedSec = s.elapsedMs / 1000.0,
        )
        InfoRow("Glycémie estimée", "%.2f mmol/L".format(estGluc), "modèle cinétique simplifié · Guyton & Hall 2016",
            if (estGluc < 3.0) Danger else if (estGluc < 3.9) Warm else Teal)
        cum?.let { c ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(Violet) {
                Label("Cumul de session", Violet)
                InfoRow("Eau métabolique totale", "%.3f g".format(c.metWaterTotalG), "", Teal)
                InfoRow("ATP total", if (c.atpTotalMmol >= 1000) "%.2f mol".format(c.atpTotalMmol / 1000) else "%.2f mmol".format(c.atpTotalMmol), "", Gold)
                InfoRow("N₂ excrété total", if (c.n2ExcretedMg >= 1000) "%.4f g".format(c.n2ExcretedMg / 1000) else "%.3f mg".format(c.n2ExcretedMg), "", Violet)
            }
        }

        // Manual HR cross-check
        Spacer(Modifier.height(8.dp))
        var hrText by remember { mutableStateOf(manualHR?.toString() ?: "") }
        TintedPanel(Violet) {
            Label("Vérification FC (Fick)", Violet)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hrText, onValueChange = { hrText = it; it.toIntOrNull()?.let { bpm -> onSaveManualHR(bpm) } },
                    label = { Text("FC repos (bpm)") }, singleLine = true,
                    modifier = Modifier.width(140.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )
                manualHR?.let { mhr ->
                    val diff = mhr - met.hrEstimated
                    Column {
                        Text("%+.1f bpm (%.0f%%)".format(diff, abs(diff) / met.hrEstimated * 100),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                            color = if (abs(diff) <= 10) Teal else if (abs(diff) <= 20) Gold else Danger)
                        Text("VS impliqué : %.1f mL".format(met.vo2PerMin * 1000 / (mhr * 0.05)),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                }
            }
        }
    }
}
