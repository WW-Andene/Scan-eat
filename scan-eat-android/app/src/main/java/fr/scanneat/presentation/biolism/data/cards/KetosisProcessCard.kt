package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun KetosisProcessCard(s: TimerState, met: MetabolicResult, lang: String = "fr") {
    val phase = BiolismEngine.ketoPhaseInfo(s.ketoHours, s.ketoAdapted, lang)
    val phaseColor = colorFromToken(phase.colorToken)
    BioCard(stringResource(R.string.biolism_ketoproc_title), badge = { Badge(phase.label.uppercase(), phaseColor) }) {
        Text(formatDuration(s.ketoElapsedMs), style = HeroNumberStyle.copy(fontSize = 24.sp), color = phaseColor)
        Text(stringResource(R.string.biolism_ketoproc_elapsed_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        Spacer(Modifier.height(6.dp))
        Text(phase.description, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        Spacer(Modifier.height(Spacing.S))
        LinearProgressIndicator(
            progress = { (phase.progressPct / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = phaseColor, trackColor = OnBackground.copy(0.06f),
        )
        Spacer(Modifier.height(10.dp))
        MetCellGrid(
            listOf(
                Triple(stringResource(R.string.biolism_ketoproc_time_label), formatDuration(s.ketoElapsedMs), ""),
                if (s.fastingHours > 0) Triple(stringResource(R.string.biolism_ketoproc_fasting), "%.1f h".format(Locale.US, s.fastingHours), "")
                else Triple(stringResource(R.string.biolism_ketoproc_gng_rate), "%.2f g/h".format(Locale.US, met.gngGPerHr), ""),
                Triple(stringResource(R.string.biolism_ketoproc_ketones_est), phase.estimatedKetoneMmol, ""),
                Triple(stringResource(R.string.biolism_ketoproc_live_rq), "%.3f".format(Locale.US, met.sub.rq), ""),
            ),
            accents = listOf(phaseColor, Gold, TextSecondary, phaseColor),
        )
        if (s.ketoHours >= 1440) {
            Spacer(Modifier.height(Spacing.S))
            Text(stringResource(R.string.biolism_ketoproc_warning),
                style = MaterialTheme.typography.labelSmall, color = semanticRed(), fontWeight = FontWeight.SemiBold)
        }
    }
}
