package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun OrganHeatCard(met: MetabolicResult, s: TimerState) {
    BioCard(stringResource(R.string.biolism_organheat_title), badge = { VioletBadge("ELIA 1992") }) {
        val maxPct = met.organs.maxOfOrNull { it.pct } ?: 1.0
        val eliaBase = mapOf("Liver" to 26.0, "Skeletal Muscle" to 22.0, "Brain" to 18.0, "Residual" to 16.0, "Kidneys" to 9.0, "Heart" to 9.0)
        val kcalPerDayFmt = stringResource(R.string.biolism_organheat_kcal_per_day)
        met.organs.forEach { organ ->
            val kcalDay = met.bmrDay * met.ketoSupprFactor * organ.pct / 100.0
            val delta   = organ.pct - (eliaBase[organ.name] ?: organ.pct)
            val barColor = colorFromToken(organ.colorToken)
            Column(Modifier.padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(organ.name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("%.1f%%".format(organ.pct), style = MaterialTheme.typography.labelSmall, color = barColor, fontWeight = FontWeight.Bold)
                        if (s.ketosisOn && delta != 0.0) Text("%+.1f%%".format(delta), style = MaterialTheme.typography.labelSmall, color = if (delta > 0) Teal else Violet)
                        Text(kcalPerDayFmt.format(kcalDay), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                }
                Spacer(Modifier.height(3.dp))
                LinearProgressIndicator(
                    progress = { (organ.pct / maxPct).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = barColor,
                    trackColor = OnBackground.copy(0.05f),
                )
            }
        }
    }
}
