package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun BodyCompositionCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard(stringResource(R.string.biolism_body_title), defaultOpen = true, badge = { BmiChip(met) }) {
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_body_bmi_label), "%.1f".format(met.bmi), stringResource(R.string.biolism_body_bmi_sub)),
            Triple(stringResource(R.string.biolism_body_fat_label), "%.1f%%".format(met.bfPct), stringResource(R.string.biolism_body_fat_sub)),
            Triple(stringResource(R.string.biolism_body_lean_label), "%.1f kg".format(met.ffm), stringResource(R.string.biolism_body_lean_sub)),
            Triple(stringResource(R.string.biolism_body_fatmass_label), "%.1f kg".format(met.fm), stringResource(R.string.biolism_body_fatmass_sub)),
        ))
        // Navy tape (when available)
        met.navyBfPct?.let { navy ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(Teal) {
                Label(stringResource(R.string.biolism_body_navy_method), Teal)
                MetCellGrid(listOf(
                    Triple(stringResource(R.string.biolism_body_navy_bf_label), "%.1f%%".format(navy), stringResource(R.string.biolism_body_navy_bf_sub)),
                    Triple(stringResource(R.string.biolism_body_navy_lean_label), "%.1f kg".format(met.navyFfm ?: 0.0), stringResource(R.string.biolism_body_navy_lean_sub)),
                    Triple(stringResource(R.string.biolism_body_navy_fat_label), "%.1f kg".format(met.navyFm ?: 0.0), stringResource(R.string.biolism_body_navy_fat_sub)),
                    Triple(stringResource(R.string.biolism_body_navy_delta_label), "%+.1f%%".format(navy - met.bfPct), ""),
                ))
            }
        }
        // IBW
        Spacer(Modifier.height(8.dp))
        Label(stringResource(R.string.biolism_body_ibw_title), OnBackground.copy(0.4f))
        val ibwDelta = profile.weightKg - met.ibwMean
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_body_ibw_devine), "%.1f kg".format(met.ibwDevine), stringResource(R.string.biolism_body_ibw_devine_sub)),
            Triple(stringResource(R.string.biolism_body_ibw_robinson), "%.1f kg".format(met.ibwRobinson), stringResource(R.string.biolism_body_ibw_robinson_sub)),
            Triple(stringResource(R.string.biolism_body_ibw_miller), "%.1f kg".format(met.ibwMiller), stringResource(R.string.biolism_body_ibw_miller_sub)),
            Triple(stringResource(R.string.biolism_body_ibw_mean), "%.1f kg".format(met.ibwMean),
                stringResource(R.string.biolism_body_ibw_delta_sub, if (ibwDelta > 0) "+" else "", ibwDelta)),
        ))
        // Visceral
        Spacer(Modifier.height(8.dp))
        TintedPanel(Gold) {
            Label(stringResource(R.string.biolism_body_visceral_title), Gold)
            val riskThin = stringResource(R.string.biolism_body_risk_thin)
            val riskHealthy = stringResource(R.string.biolism_body_risk_healthy)
            val riskCentral = stringResource(R.string.biolism_body_risk_central)
            val riskHigh = stringResource(R.string.biolism_body_risk_high)
            val riskLow = stringResource(R.string.biolism_body_risk_low)
            met.whtr?.let { v ->
                InfoRow(stringResource(R.string.biolism_body_whtr_label), "%.3f".format(v),
                    if (v < 0.40) riskThin else if (v < 0.50) riskHealthy else if (v < 0.60) riskCentral else riskHigh,
                    if (v < 0.50) Teal else if (v < 0.60) Gold else Danger)
            } ?: Text(stringResource(R.string.biolism_body_whtr_prompt),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            met.whr?.let { v ->
                val thresh = if (profile.sex == BiolismSex.MALE) 0.90 else 0.85
                InfoRow(stringResource(R.string.biolism_body_whr_label), "%.3f".format(v),
                    if (v < thresh) riskLow else riskHigh,
                    if (v < thresh) Teal else Danger)
            }
            met.bai?.let { v ->
                InfoRow(stringResource(R.string.biolism_body_bai_label), "%.1f%%".format(v), stringResource(R.string.biolism_body_bai_sub), Gold)
            }
        }
    }
}

@Composable
private fun BmiChip(m: MetabolicResult) {
    val color = when (m.bmiClass) {
        BiolismBmiCategory.NORMAL      -> Teal
        BiolismBmiCategory.UNDERWEIGHT -> Violet
        BiolismBmiCategory.OVERWEIGHT  -> Gold
        BiolismBmiCategory.OBESE       -> Danger
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.15f), border = BorderStroke(1.dp, color.copy(0.3f))) {
        Text(m.bmiClass.name, modifier = Modifier.padding(horizontal = Spacing.S, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
