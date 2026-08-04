package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Profile
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * BMI / TDEE preview card shown at the top of ProfileScreen — extracted from
 * ProfileScreen's inline LazyColumn item so the main screen file stays a thin
 * layout scaffold. Pure structural move, no behavior change.
 */
@Composable
internal fun ProfileMetricsPreviewCard(
    currentProfile: Profile,
    bmi: Double?,
    bmiCat: fr.scanneat.domain.engine.scoring.BmiCategory?,
    tdee: Double?,
    tdeeGoal: Double?,
    useImperial: Boolean,
) {
    // Macro ratio: recommended P/G/L split by diet, expressed as display percentages.
    val (pPct, cPct, fPct) = when (currentProfile.diet) {
        DietKey.KETO       -> Triple(25f, 5f, 70f)
        DietKey.CARNIVORE  -> Triple(40f, 0f, 60f)
        DietKey.PALEO      -> Triple(30f, 25f, 45f)
        DietKey.MEDITERRANEAN -> Triple(20f, 50f, 30f)
        else -> when (currentProfile.goal) {
            Goal.LOSE  -> Triple(35f, 35f, 30f)
            Goal.GAIN  -> Triple(35f, 45f, 20f)
            else       -> Triple(25f, 45f, 30f)
        }
    }
    // Goal ETA: weeks to reach goal weight at a realistic 0.5 kg/week deficit/surplus.
    val etaWeeks: Int? = run {
        val cw = currentProfile.weightKg ?: return@run null
        val gw = currentProfile.goalWeightKg ?: return@run null
        val diff = abs(gw - cw)
        if (diff < 0.5) null else (diff / 0.5).roundToInt()
    }

    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            bmi?.let { bmiVal ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
                    MetricChip(stringResource(R.string.profile_bmi_label), "$bmiVal")
                    bmiCat?.let { cat ->
                        val (catLabel, catColor) = when (cat) {
                            fr.scanneat.domain.engine.scoring.BmiCategory.UNDERWEIGHT -> stringResource(R.string.profile_bmi_cat_underweight) to semanticBlue()
                            fr.scanneat.domain.engine.scoring.BmiCategory.NORMAL      -> stringResource(R.string.profile_bmi_cat_normal)      to semanticGreen()
                            fr.scanneat.domain.engine.scoring.BmiCategory.OVERWEIGHT  -> stringResource(R.string.profile_bmi_cat_overweight)  to semanticAmber()
                            // OBESE_2 and OBESE_3 previously both rendered in the exact same
                            // full-opacity semanticRed(), giving no visual distinction between
                            // two categories of meaningfully different severity, while OBESE_1
                            // was (correctly) lighter via alpha. Alpha already maxes out at
                            // OBESE_2, so OBESE_3 darkens the same hue instead of picking an
                            // independent color - this keeps it correct under every colorblind
                            // mode, since it's a scale of whatever semanticRed() already
                            // returned for the active mode, not a second hardcoded color.
                            fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_1     -> stringResource(R.string.profile_bmi_cat_obese1)      to semanticRed().copy(0.8f)
                            fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_2     -> stringResource(R.string.profile_bmi_cat_obese2)      to semanticRed()
                            fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_3     -> stringResource(R.string.profile_bmi_cat_obese3)      to
                                semanticRed().let { it.copy(red = it.red * 0.7f, green = it.green * 0.7f, blue = it.blue * 0.7f) }
                        }
                        Surface(shape = RoundedCornerShape(50), color = catColor.copy(alpha = 0.15f)) {
                            Text(catLabel, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2), style = MaterialTheme.typography.labelSmall, color = catColor)
                        }
                    }
                }
            }
            tdee?.let { MetricChip("TDEE", stringResource(R.string.profile_tdee_kcal, it.roundToInt())) }
        }
        tdeeGoal?.let {
            HorizontalDivider(color = OnSurface.copy(0.08f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                MetricChip(stringResource(R.string.profile_tdee_goal_label), stringResource(R.string.profile_tdee_kcal, it.roundToInt()))
            }
        }
        // Improvement: macro ratio bar showing recommended P/G/L distribution.
        HorizontalDivider(color = OnSurface.copy(0.08f))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.profile_macro_ratio_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            Row(
                modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
            ) {
                Box(Modifier.weight(pPct).fillMaxHeight().background(semanticGreen()))
                Box(Modifier.weight(cPct).fillMaxHeight().background(AccentCoral))
                Box(Modifier.weight(fPct).fillMaxHeight().background(Gold))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                val proteinAbbr = stringResource(R.string.macro_protein_abbr)
                val carbsAbbr   = stringResource(R.string.macro_carbs_abbr)
                val fatAbbr     = stringResource(R.string.macro_fat_abbr)
                listOf("$proteinAbbr ${pPct.toInt()}%" to semanticGreen(), "$carbsAbbr ${cPct.toInt()}%" to AccentCoral, "$fatAbbr ${fPct.toInt()}%" to Gold).forEach { (label, color) ->
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color)
                }
            }
        }
        // New: goal weight progress + weeks-to-goal ETA.
        val cw = currentProfile.weightKg
        val gw = currentProfile.goalWeightKg
        if (cw != null && gw != null && abs(gw - cw) >= 0.1) {
            HorizontalDivider(color = OnSurface.copy(0.08f))
            val startWeight = if (gw < cw) maxOf(cw, gw + 30.0) else minOf(cw, gw - 30.0)
            val totalRange = abs(gw - startWeight).coerceAtLeast(0.1)
            val progress = ((cw - startWeight) / (gw - startWeight)).toFloat().coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.profile_goal_progress_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    etaWeeks?.let { Text(stringResource(R.string.profile_goal_eta_weeks, it), style = MaterialTheme.typography.labelSmall, color = AccentCoral) }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentCoral,
                    trackColor = OnSurface.copy(0.1f),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // dispWeight(), not a hardcoded "kg" suffix - this row previously
                    // ignored the metric/imperial toggle two sections below on this
                    // same screen, always showing kg even in imperial mode.
                    Text(dispWeight(cw, useImperial), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    Text("→ ${dispWeight(gw, useImperial)}", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                }
            }
        }
    }
}
