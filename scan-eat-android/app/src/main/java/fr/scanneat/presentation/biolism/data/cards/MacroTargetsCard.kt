package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs
import java.util.Locale

@Composable
fun MacroTargetsCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard(stringResource(R.string.biolism_macro_title), defaultOpen = false, badge = { GoldBadge(stringResource(R.string.biolism_macro_badge)) }) {
        InfoRow(stringResource(R.string.biolism_macro_tdee_label),
            stringResource(R.string.biolism_macro_tdee_value, met.tdeeDay, met.bmrDay, profile.activityMeta.mult, met.ffm), "", Gold)
        Spacer(Modifier.height(4.dp))
        MacroTargetRow(stringResource(R.string.biolism_macro_protein), met.macroProtMinG, "g",
            stringResource(R.string.biolism_macro_protein_sub, met.protGPerKgFfm, met.ffm), Violet)
        MacroTargetRow(stringResource(R.string.biolism_macro_carbs), met.macroCarbMinG, "g",
            if (met.macroCarbMinG < 50) stringResource(R.string.biolism_macro_carbs_sub_keto) else stringResource(R.string.biolism_macro_carbs_sub_brain), Teal)
        MacroTargetRow(stringResource(R.string.biolism_macro_fat), met.macroFatMinG, "g",
            stringResource(R.string.biolism_macro_fat_sub, met.essentialFatMinG), Warm)

        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = GoldHaze, border = BorderStroke(1.dp, GoldBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.biolism_macro_total_min), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.biolism_macro_total_min_sub), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.0f kcal".format(Locale.US, met.macroFloorKcal), style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
                    val delta = met.macroFloorKcal - met.tdeeDay
                    Text(stringResource(R.string.biolism_macro_tdee_delta, met.tdeeDay, delta), style = MaterialTheme.typography.labelSmall,
                        color = if (abs(delta) < 5) Teal else OnBackground.copy(0.5f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Label(stringResource(R.string.biolism_macro_extra_title), OnBackground.copy(0.4f))
        InfoRow(stringResource(R.string.biolism_macro_water), "≥ %.1f L".format(Locale.US, met.waterNeedL),
            if (profile.activityMeta.mult >= 1.55)
                stringResource(R.string.biolism_macro_water_sub_activity, if (profile.sex == BiolismSex.MALE) 2.5 else 2.0, 0.5)
            else
                stringResource(R.string.biolism_macro_water_sub, if (profile.sex == BiolismSex.MALE) 2.5 else 2.0),
            Teal)
        InfoRow(stringResource(R.string.biolism_macro_fiber), "≥ 25 g", stringResource(R.string.biolism_macro_fiber_sub), Gold)
        InfoRow(stringResource(R.string.biolism_macro_sodium), "1500–2300 mg", stringResource(R.string.biolism_macro_sodium_sub), Warm)
        InfoRow(stringResource(R.string.biolism_macro_potassium), stringResource(R.string.biolism_macro_potassium_value, if (profile.sex == BiolismSex.MALE) 3400 else 2600),
            stringResource(R.string.biolism_macro_potassium_sub), Violet)
    }
}
