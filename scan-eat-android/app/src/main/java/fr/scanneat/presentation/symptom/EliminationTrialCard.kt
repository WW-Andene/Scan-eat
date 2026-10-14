package fr.scanneat.presentation.symptom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.symptom.EliminationTrial
import fr.scanneat.domain.engine.symptom.TrialPhase
import fr.scanneat.domain.engine.symptom.TrialReport
import fr.scanneat.domain.engine.symptom.WindowRate
import fr.scanneat.presentation.ui.theme.*

/** Roadmap D4: start / follow an avoidance trial. Observation, never a diagnosis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EliminationTrialCard(
    trial: EliminationTrial?,
    report: TrialReport?,
    onStart: (target: String, avoidDays: Int, reintroDays: Int) -> Unit,
    onStop: () -> Unit,
) {
    var target by remember { mutableStateOf("lactose") }
    var avoid by remember { mutableIntStateOf(14) }
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(stringResource(R.string.trial_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground)
        Text(stringResource(R.string.trial_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
        if (trial == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf("lactose" to R.string.allergen_lactose, "gluten" to R.string.allergen_gluten, fr.scanneat.domain.engine.symptom.POLYOLS_TARGET to R.string.trial_target_polyols).forEach { (k, res) ->
                    FilterChip(selected = target == k, onClick = { target = k }, label = { Text(stringResource(res)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(14, 21, 28).forEach { d -> FilterChip(selected = avoid == d, onClick = { avoid = d }, label = { Text(stringResource(R.string.trial_days, d)) }) }
            }
            Button(onClick = { onStart(target, avoid, 7) }) { Text(stringResource(R.string.trial_start)) }
        } else {
            val targetLabel = stringResource(when (trial.target) { "gluten" -> R.string.allergen_gluten; fr.scanneat.domain.engine.symptom.POLYOLS_TARGET -> R.string.trial_target_polyols; else -> R.string.allergen_lactose })
            report?.let { r ->
                Text(when (r.phase) {
                    TrialPhase.AVOIDANCE -> stringResource(R.string.trial_phase_avoid, targetLabel, r.daysLeftInPhase)
                    TrialPhase.REINTRODUCTION -> stringResource(R.string.trial_phase_reintro, targetLabel, r.daysLeftInPhase)
                    TrialPhase.FINISHED -> stringResource(R.string.trial_phase_done)
                }, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                @Composable fun line(label: Int, w: WindowRate) = Text(
                    stringResource(label) + " : " + (w.percent?.let { stringResource(R.string.trial_rate, it, w.symptomDays, w.days) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground)
                line(R.string.trial_baseline, r.baseline)
                line(R.string.trial_avoidance, r.avoidance)
                line(R.string.trial_reintroduction, r.reintroduction)
                if (r.slips.isNotEmpty()) Text(stringResource(R.string.trial_slips, r.slips.size) + "\n" + r.slips.take(5).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall, color = semanticAmber())
            }
            TextButton(onClick = onStop) { Text(stringResource(R.string.trial_stop)) }
        }
    }
}
