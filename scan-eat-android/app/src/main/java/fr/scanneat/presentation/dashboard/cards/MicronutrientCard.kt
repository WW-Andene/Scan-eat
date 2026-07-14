package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

// EU Nutrient Reference Values (NRV, Regulation 1169/2011)
private object NRV {
    const val FIBER_G    = 25.0
    const val IRON_MG    = 14.0
    const val CALCIUM_MG = 800.0
    const val VIT_D_UG   = 5.0
    const val B12_UG     = 2.5
}

@Composable
internal fun MicronutrientCard(totals: ConsumedNutrition) {
    // Only show when there's at least some logged food
    if (totals.energyKcal < 1.0) return

    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CARD))) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    stringResource(R.string.dashboard_micronutrients_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = OnSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                MicroRow(stringResource(R.string.dashboard_micro_fiber),   totals.fiberG,    NRV.FIBER_G,    "g",  Teal)
                MicroRow(stringResource(R.string.dashboard_micro_iron),    totals.ironMg,    NRV.IRON_MG,    "mg", Gold)
                MicroRow(stringResource(R.string.dashboard_micro_calcium), totals.calciumMg, NRV.CALCIUM_MG, "mg", AccentCoral)
                MicroRow(stringResource(R.string.dashboard_micro_vitd),    totals.vitDUg,    NRV.VIT_D_UG,   "µg", Violet)
                MicroRow(stringResource(R.string.dashboard_micro_b12),     totals.b12Ug,     NRV.B12_UG,     "µg", Warm)
            }
        }
    }
}

@Composable
private fun MicroRow(label: String, value: Double, nrv: Double, unit: String, color: Color) {
    val pct = (value / nrv).toFloat().coerceIn(0f, 1f)
    val isLow = pct < 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface.copy(0.7f),
            modifier = Modifier.width(72.dp),
        )
        LinearProgressIndicator(
            progress   = { pct },
            modifier   = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
            color      = if (isLow) semanticAmber() else color,
            trackColor = OnSurface.copy(0.08f),
        )
        Text(
            "${value.roundToInt()}/${ nrv.roundToInt()}$unit",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLow) semanticAmber() else OnSurface.copy(0.5f),
            fontWeight = if (isLow) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.width(64.dp),
        )
    }
}
