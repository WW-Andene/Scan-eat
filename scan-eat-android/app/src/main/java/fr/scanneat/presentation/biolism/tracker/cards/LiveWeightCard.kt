package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.KG_TO_LB
import java.util.Locale

/** 1 ounce (avoirdupois) in grams - used only to give this card's gram-scale deltas
 *  (fat/glycogen lost, live delta) an imperial-equivalent small unit, matching kg's
 *  relationship to grams the way dispWeight() matches kg to lb for the main number. */
private const val G_TO_OZ = 1.0 / 28.3495

@Composable
internal fun LiveWeightCard(liveWeight: Double, baseWeight: Double, fatLostKg: Double, glycoLostKg: Double, ketosisOn: Boolean, useImperial: Boolean = false) {
    val color = if (ketosisOn) Teal else Gold
    // Every sibling weight display in this app (WeightScreen, BiolismProfileScreen,
    // BodyCompositionCard) routes through the useImperial preference - this card was
    // the one place still hardcoded to kg/g regardless of the user's setting.
    val mainUnit = if (useImperial) "lb" else "kg"
    val smallUnit = if (useImperial) "oz" else "g"
    fun mainValue(kg: Double) = if (useImperial) kg * KG_TO_LB else kg
    fun smallValue(g: Double) = if (useImperial) g * G_TO_OZ else g
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = color.copy(0.04f),
        border = BorderStroke(1.dp, color.copy(0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.biolism_liveweight_title), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(if (ketosisOn) stringResource(R.string.biolism_liveweight_method_ketosis) else stringResource(R.string.biolism_liveweight_method_normal),
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(String.format(Locale.US, "%.4f", mainValue(liveWeight)), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.W500), color = color)
                Text(mainUnit, style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
                val deltaG = (liveWeight - baseWeight) * 1000.0
                Text(stringResource(R.string.biolism_liveweight_delta, String.format(Locale.US, "%.4f", smallValue(deltaG)), smallUnit), style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.biolism_liveweight_base, String.format(Locale.US, "%.3f", mainValue(baseWeight)), mainUnit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                Text("−", color = OnBackground.copy(0.3f))
                Text(stringResource(R.string.biolism_liveweight_fat_lost, String.format(Locale.US, "%.4f", smallValue(fatLostKg * 1000)), smallUnit), style = MaterialTheme.typography.labelSmall, color = color.copy(0.8f))
                if (ketosisOn && glycoLostKg > 0) {
                    Text("−", color = OnBackground.copy(0.3f))
                    Text(stringResource(R.string.biolism_liveweight_glyco_lost, String.format(Locale.US, "%.2f", smallValue(glycoLostKg * 1000)), smallUnit), style = MaterialTheme.typography.labelSmall, color = Gold.copy(0.8f))
                }
            }
        }
    }
}
