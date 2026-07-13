package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun TodayMacroCard(totals: ConsumedNutrition, targets: DailyTargets?) {
    Box(modifier = Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(18.dp))) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.dashboard_today_label), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                val kcalPct = targets?.let { (totals.energyKcal / it.kcal).toFloat() }
                val kcalColor = when {
                    kcalPct == null  -> AccentCoral
                    kcalPct > 1.1f   -> FlagRed
                    kcalPct > 0.9f   -> FlagGreen
                    else             -> AccentCoral
                }
                val protPct = targets?.proteinGTarget?.takeIf { it > 0 }?.let { (totals.proteinG / it).toFloat() }
                // Only a diet with an actual daily carb budget (e.g. keto) gives this
                // ring a target - most diets are ingredient-exclusion only, not a
                // macro budget, so it stays an unfilled ring for them same as before.
                val carbsMax = targets?.carbsGDailyMax
                val carbsPct = carbsMax?.takeIf { it > 0 }?.let { (totals.carbsG / it).toFloat() }
                val carbsColor = if (carbsPct != null && carbsPct > 1f) FlagRed else AccentCoral
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroRing(stringResource(R.string.diary_macro_calories), totals.energyKcal.roundToInt(), "kcal", kcalPct, kcalColor)
                    MacroRing(stringResource(R.string.diary_macro_protein), totals.proteinG.roundToInt(), "g", protPct, AccentCoral)
                    MacroRing(stringResource(R.string.diary_macro_carbs), totals.carbsG.roundToInt(), "g", carbsPct, carbsColor)
                    MacroRing(stringResource(R.string.diary_macro_fat), totals.fatG.roundToInt(), "g", null, AccentCoral)
                }
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Int, unit: String, pct: Float?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress   = { pct?.coerceIn(0f, 1f) ?: 1f },
                modifier   = Modifier.fillMaxSize(),
                color      = if (pct != null) color else OnSurface.copy(alpha = 0.12f),
                strokeWidth = 4.dp,
                trackColor = OnSurface.copy(alpha = 0.12f),
            )
            Text(value.toString(), style = MaterialTheme.typography.labelMedium, color = OnBackground, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}
