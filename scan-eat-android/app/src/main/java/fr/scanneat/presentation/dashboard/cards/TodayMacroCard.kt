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
                    kcalPct > 1.1f   -> semanticRed()
                    kcalPct > 0.9f   -> semanticGreen()
                    else             -> AccentCoral
                }
                val protPct = targets?.proteinGTarget?.takeIf { it > 0 }?.let { (totals.proteinG / it).toFloat() }
                // A hard cap (e.g. keto's 30g) still turns the ring red once exceeded;
                // every other diet gets the general AMDR-derived target instead of a
                // bare unfilled ring, same treatment as calories/protein/fat.
                val carbsTarget = targets?.carbsGTarget
                val carbsPct = carbsTarget?.takeIf { it > 0 }?.let { (totals.carbsG / it).toFloat() }
                val carbsIsHardCap = targets?.carbsGDailyMax != null
                val carbsColor = if (carbsIsHardCap && carbsPct != null && carbsPct > 1f) semanticRed() else AccentCoral
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroRing(stringResource(R.string.diary_macro_calories), totals.energyKcal.roundToInt(), "kcal", kcalPct, kcalColor, targets?.kcal?.roundToInt())
                    MacroRing(stringResource(R.string.diary_macro_protein), totals.proteinG.roundToInt(), "g", protPct, AccentCoral, targets?.proteinGTarget?.takeIf { it > 0 }?.roundToInt())
                    MacroRing(stringResource(R.string.diary_macro_carbs), totals.carbsG.roundToInt(), "g", carbsPct, carbsColor, carbsTarget?.takeIf { it > 0 }?.roundToInt())
                    val fatPct = targets?.fatGTarget?.takeIf { it > 0 }?.let { (totals.fatG / it).toFloat() }
                    val fatColor = if (fatPct != null && fatPct > 1.1f) semanticRed() else AccentCoral
                    MacroRing(stringResource(R.string.diary_macro_fat), totals.fatG.roundToInt(), "g", fatPct, fatColor, targets?.fatGTarget?.roundToInt())
                }
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Int, unit: String, pct: Float?, color: Color, target: Int?) {
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
        // Previously only the current total was shown ("120g") with no indication of
        // the profile-derived target it's being measured against, even though the
        // ring's own fill % was silently computed from that same target — the number
        // that would explain *why* the ring looks the way it does was never printed.
        Text(
            if (target != null) "$unit · $value/$target" else unit,
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}
