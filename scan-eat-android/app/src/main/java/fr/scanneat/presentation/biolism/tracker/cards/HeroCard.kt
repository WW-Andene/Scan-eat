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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import fr.scanneat.R
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
    ScanEatCard(contentPadding = PaddingValues(16.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ketosisOn) Box(Modifier.size(6.dp).clip(CircleShape).background(Teal))
                if (running)   Box(Modifier.size(6.dp).clip(CircleShape).background(Gold))
                Surface(shape = RoundedCornerShape(4.dp), color = if (running) GoldHaze else VioletHaze,
                    border = BorderStroke(1.dp, if (running) GoldGlow else VioletGlow)) {
                    Text(if (running) stringResource(R.string.biolism_hero_running) else stringResource(R.string.biolism_hero_paused), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, color = if (running) Gold else Violet, fontWeight = FontWeight.Bold)
                }
            }

            Text(if (ketosisOn) stringResource(R.string.biolism_hero_kcal_ketosis) else stringResource(R.string.biolism_hero_kcal_label),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
                letterSpacing = 1.sp, fontWeight = FontWeight.Bold)

            Text(
                if (precision) String.format(Locale.US, "%.4f", kcalTotal) else String.format(Locale.US, "%.1f", kcalTotal),
                style = HeroNumberStyle.copy(fontSize = 42.sp),
                color = heroColor,
            )
            Text("kcal", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            if (ketosisOn) {
                Text(stringResource(R.string.biolism_hero_oxidation, fatPct, carbPct, protPct, npRq),
                    style = MaterialTheme.typography.labelSmall, color = Teal.copy(0.8f))
            }

            TextButton(onClick = onPrecision) {
                Text(if (precision) stringResource(R.string.biolism_hero_precision_low) else stringResource(R.string.biolism_hero_precision_high),
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
                    SubstrateLegendItem(if (ketosisOn) Teal else Warm, stringResource(R.string.biolism_hero_legend_fat, fatPct))
                    SubstrateLegendItem(Gold.copy(0.9f), stringResource(R.string.biolism_hero_legend_carbs, carbPct))
                    SubstrateLegendItem(Violet, stringResource(R.string.biolism_hero_legend_protein, protPct))
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
