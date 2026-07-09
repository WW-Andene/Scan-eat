package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.presentation.biolism.tracker.formatElapsed
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HeroCard(
    kcalTotal: Double, precision: Boolean, running: Boolean, ketosisOn: Boolean,
    elapsedSec: Double, fatPct: Int, carbPct: Int, protPct: Int,
    fatFrac: Double, carbFrac: Double, protFrac: Double, npRq: Double,
    onPrecision: () -> Unit,
) {
    val heroColor = if (ketosisOn) Teal else Gold
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ketosisOn) Box(Modifier.size(6.dp).clip(CircleShape).background(Teal))
                if (running)   Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                Surface(shape = RoundedCornerShape(4.dp), color = if (running) GoldHaze else VioletHaze,
                    border = BorderStroke(1.dp, if (running) GoldGlow else VioletGlow)) {
                    Text(if (running) "EN COURS" else "PAUSE", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = if (running) Gold else Violet, fontWeight = FontWeight.Bold)
                }
            }

            Text(if (ketosisOn) "Calories brûlées (cétose)" else "Calories brûlées",
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
                letterSpacing = 1.sp, fontWeight = FontWeight.Bold)

            Text(
                if (precision) String.format("%.4f", kcalTotal) else String.format("%.1f", kcalTotal),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W500, fontSize = 42.sp),
                color = heroColor,
            )
            Text("kcal", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            if (ketosisOn) {
                Text("oxydation : ${fatPct}% graisses · ${carbPct}% glucides · ${protPct}% protéines · npRQ ${String.format("%.3f", npRq)}",
                    style = MaterialTheme.typography.labelSmall, color = Teal.copy(0.8f))
            }

            TextButton(onClick = onPrecision) {
                Text(if (precision) "← 1 chiffre" else "4 chiffres →",
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            }

            // Elapsed
            Text(formatElapsed(elapsedSec), style = MaterialTheme.typography.labelMedium,
                color = OnBackground.copy(0.4f), fontWeight = FontWeight.Medium)

            // Substrate bar
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                    Box(Modifier.weight(fatFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(if (ketosisOn) Teal else Warm))
                    Box(Modifier.weight(carbFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(Gold.copy(0.6f)))
                    Box(Modifier.weight(protFrac.coerceAtLeast(0.01).toFloat()).fillMaxHeight().background(Violet.copy(0.7f)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubstrateLegendItem(if (ketosisOn) Teal else Warm, "Graisses $fatPct%")
                    SubstrateLegendItem(Gold.copy(0.9f), "Glucides $carbPct%")
                    SubstrateLegendItem(Violet, "Protéines $protPct%")
                }
            }
        }
    }
}

@Composable
private fun SubstrateLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
    }
}
