package fr.scanneat.presentation.diary.components

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
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.CardRadius

@Composable
internal fun MacroSummaryCard(totals: ConsumedNutrition, targets: DailyTargets?) {
    Box(Modifier.fillMaxWidth().glassSheen()) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.diary_totals_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroItem(stringResource(R.string.diary_macro_calories), "${totals.energyKcal.toInt()}", "kcal", targets?.kcal?.toInt())
                    MacroItem(stringResource(R.string.diary_macro_protein), "${totals.proteinG.toInt()}", "g", targets?.proteinGTarget?.takeIf { it > 0 }?.toInt())
                    MacroItem(stringResource(R.string.diary_macro_carbs), "${totals.carbsG.toInt()}", "g", targets?.carbsGTarget?.takeIf { it > 0 }?.toInt())
                    MacroItem(stringResource(R.string.diary_macro_fat), "${totals.fatG.toInt()}", "g", targets?.fatGTarget?.takeIf { it > 0 }?.toInt())
                }
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String, target: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Previously only the raw total was shown, with no indication of the
        // profile-derived daily target it should be measured against.
        Text(if (target != null) "$value/$target" else value, style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
    }
}
