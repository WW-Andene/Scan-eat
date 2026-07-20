package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import java.util.Locale

@Composable
internal fun MacroSummaryCard(totals: ConsumedNutrition, targets: DailyTargets?, goalTargets: DailyTargets? = null, goalWeightKg: Double? = null) {
    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Text(stringResource(R.string.diary_totals_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        MacroRow(totals, targets, AccentCoral)
        // Second row: what the same day's totals look like against the
        // targets for the user's stated goal weight instead of their
        // current one - previously the only way to see this was to edit
        // Profile's weight field to the goal value, check, then edit it
        // back, since goalWeightKg was collected but never used here.
        if (goalTargets != null && goalWeightKg != null) {
            Text(
                stringResource(R.string.diary_goal_targets_title, formatWeight(goalWeightKg)),
                style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.SemiBold,
            )
            MacroRow(totals, goalTargets, Gold)
        }
    }
}

private fun formatWeight(kg: Double): String =
    if (kg == kg.toInt().toDouble()) kg.toInt().toString() else String.format(Locale.getDefault(), "%.1f", kg)

@Composable
private fun MacroRow(totals: ConsumedNutrition, targets: DailyTargets?, accent: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        MacroItem(stringResource(R.string.diary_macro_calories), "${totals.energyKcal.toInt()}", "kcal", targets?.kcal?.toInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_protein), "${totals.proteinG.toInt()}", "g", targets?.proteinGTarget?.takeIf { it > 0 }?.toInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_carbs), "${totals.carbsG.toInt()}", "g", targets?.carbsGTarget?.takeIf { it > 0 }?.toInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_fat), "${totals.fatG.toInt()}", "g", targets?.fatGTarget?.takeIf { it > 0 }?.toInt(), accent)
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String, target: Int?, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Previously only the raw total was shown, with no indication of the
        // profile-derived daily target it should be measured against.
        Text(if (target != null) "$value/$target" else value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
    }
}
