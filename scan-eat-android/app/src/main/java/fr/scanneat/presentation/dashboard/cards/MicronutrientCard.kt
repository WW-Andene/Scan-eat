package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

// EU generic Nutrient Reference Values (NRV, Regulation 1169/2011) - fallback
// only, used when no profile-derived DailyTargets exist yet (see targets param
// below).
private object NRV {
    const val FIBER_G    = 25.0
    const val IRON_MG    = 14.0
    const val CALCIUM_MG = 800.0
    const val VIT_D_UG   = 5.0
    const val B12_UG     = 2.5
    const val MAGNESIUM_MG = 375.0
    const val POTASSIUM_MG = 2000.0
    const val ZINC_MG      = 10.0
    const val VIT_C_MG     = 80.0
}

@Composable
internal fun MicronutrientCard(totals: ConsumedNutrition, targets: DailyTargets? = null) {
    // Only show when there's at least some logged food
    if (totals.energyKcal < 1.0) return

    // Previously always compared against the flat NRV constants above regardless
    // of the personalized DailyTargets already computed for this same dashboard
    // (GapCloserCard/ChronicGapCard use them) - on the same day, with the same
    // logged food, this card could show e.g. a vitD bar as "complete" against the
    // generic 5µg reference while GapCloserCard simultaneously flagged a deficit
    // against the real 15-20µg target, and ignored the app's own sex/age
    // personalization (an elderly user's real vitD need is 4x understated by the
    // flat NRV value). Falls back to NRV only when no profile exists yet.
    val fiberTarget   = targets?.fiberGTarget   ?: NRV.FIBER_G
    val ironTarget    = targets?.ironMgTarget   ?: NRV.IRON_MG
    val calciumTarget = targets?.calciumMgTarget ?: NRV.CALCIUM_MG
    val vitDTarget    = targets?.vitDUgTarget   ?: NRV.VIT_D_UG
    val b12Target     = targets?.b12UgTarget    ?: NRV.B12_UG
    // DailyTargets already computed these four (PersonalScoreEngine.kt) but
    // ConsumedNutrition had nowhere to accumulate the consumed side until now
    // (see DiaryEntry.kt) - a personalized target with nothing to compare it
    // against never reached this card.
    val magnesiumTarget = targets?.magnesiumMgTarget ?: NRV.MAGNESIUM_MG
    val potassiumTarget = targets?.potassiumMgTarget ?: NRV.POTASSIUM_MG
    val zincTarget       = targets?.zincMgTarget      ?: NRV.ZINC_MG
    val vitCTarget       = targets?.vitCMgTarget      ?: NRV.VIT_C_MG

    ScanEatCard(
        color = SurfaceVariant,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(
            stringResource(R.string.dashboard_micronutrients_title),
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
        )

        MicroRow(stringResource(R.string.dashboard_micro_fiber),   totals.fiberG,    fiberTarget,   "g",  Teal)
        MicroRow(stringResource(R.string.dashboard_micro_iron),    totals.ironMg,    ironTarget,    "mg", Gold)
        MicroRow(stringResource(R.string.dashboard_micro_calcium), totals.calciumMg, calciumTarget, "mg", AccentCoral)
        MicroRow(stringResource(R.string.dashboard_micro_vitd),    totals.vitDUg,    vitDTarget,    "µg", Violet)
        MicroRow(stringResource(R.string.dashboard_micro_b12),     totals.b12Ug,     b12Target,     "µg", Warm)
        MicroRow(stringResource(R.string.dashboard_micro_magnesium), totals.magnesiumMg, magnesiumTarget, "mg", MetaGreen)
        MicroRow(stringResource(R.string.dashboard_micro_potassium), totals.potassiumMg, potassiumTarget, "mg", HydrationBlue)
        MicroRow(stringResource(R.string.dashboard_micro_zinc),     totals.zincMg,      zincTarget,      "mg", CalorieOrange)
        MicroRow(stringResource(R.string.dashboard_micro_vitc),     totals.vitCMg,       vitCTarget,      "mg", Teal)
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
