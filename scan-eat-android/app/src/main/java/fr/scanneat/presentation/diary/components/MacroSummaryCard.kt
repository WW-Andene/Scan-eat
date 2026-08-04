package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.domain.model.ConsumedNutrition
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.GlassAlertDialog
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.dispWeight
import kotlin.math.roundToInt

@Composable
internal fun MacroSummaryCard(totals: ConsumedNutrition, targets: DailyTargets?, goalTargets: DailyTargets? = null, goalWeightKg: Double? = null, useImperial: Boolean = false) {
    // The keto/low-carb clinical-range explanation used to always render as a full
    // paragraph at the bottom of this card - moved behind a small info icon (top-right,
    // tap to read) instead, since a user checking their daily totals doesn't need that
    // paragraph re-explained on every visit; it's still one tap away, not removed.
    var showLowCarbInfo by remember { mutableStateOf(false) }
    val isLowCarbDiet = (targets ?: goalTargets)?.carbsGDailyMax != null
    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.diary_totals_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            if (isLowCarbDiet) {
                IconButton(onClick = { showLowCarbInfo = true }) {
                    Icon(Icons.Rounded.Info, stringResource(R.string.diary_low_carb_hint_cd), tint = OnSurface.copy(0.5f), modifier = Modifier.size(18.dp))
                }
            }
        }
        MacroRow(totals, targets, AccentCoral)
        // Second row: what the same day's totals look like against the
        // targets for the user's stated goal weight instead of their
        // current one - previously the only way to see this was to edit
        // Profile's weight field to the goal value, check, then edit it
        // back, since goalWeightKg was collected but never used here.
        if (goalTargets != null && goalWeightKg != null) {
            Text(
                // dispWeight(), not a private formatWeight() that hardcoded kg and
                // used Locale.getDefault() (comma decimal separator on FR devices) -
                // same bug class already fixed for every other weight display in
                // the app (see UnitConversion.kt's own doc comment).
                stringResource(R.string.diary_goal_targets_title, dispWeight(goalWeightKg, useImperial)),
                style = MaterialTheme.typography.titleSmall, color = Gold, fontWeight = FontWeight.SemiBold,
            )
            MacroRow(totals, goalTargets, Gold)
        }
    }
    // carbsGDailyMax is only non-null for a macro-budget diet (keto's 20-50g
    // clinical ketosis ceiling, see DailyTargets.kt) - a user seeing e.g. "0/30g"
    // right next to protein/fat targets several times higher had no indication
    // that number was an intentional diet-driven cap rather than a miscalculated
    // target, so a scan-through of Profile's own diet choice would read as the
    // app being wrong instead of the diet being strict. Now behind the info icon
    // above instead of always-visible body text.
    if (showLowCarbInfo) {
        GlassAlertDialog(
            onDismissRequest = { showLowCarbInfo = false },
            title = { Text(stringResource(R.string.diary_totals_title), color = OnBackground) },
            text = { Text(stringResource(R.string.diary_low_carb_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f)) },
            confirmButton = { TextButton(onClick = { showLowCarbInfo = false }) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
        )
    }
}

@Composable
private fun MacroRow(totals: ConsumedNutrition, targets: DailyTargets?, accent: androidx.compose.ui.graphics.Color) {
    // .roundToInt(), not .toInt() - .toInt() always truncates toward zero, biasing
    // every figure on the app's primary "how am I doing" display down. The
    // codebase already has an explicit rule against this exact anti-pattern
    // (MealPlanViewModel.dayCalories's own comment), just never applied here.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        MacroItem(stringResource(R.string.diary_macro_calories), "${totals.energyKcal.roundToInt()}", "kcal", targets?.kcal?.roundToInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_protein), "${totals.proteinG.roundToInt()}", "g", targets?.proteinGTarget?.takeIf { it > 0 }?.roundToInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_carbs), "${totals.carbsG.roundToInt()}", "g", targets?.carbsGTarget?.takeIf { it > 0 }?.roundToInt(), accent)
        MacroItem(stringResource(R.string.diary_macro_fat), "${totals.fatG.roundToInt()}", "g", targets?.fatGTarget?.takeIf { it > 0 }?.roundToInt(), accent)
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String, target: Int?, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        // Previously only the raw total was shown, with no indication of the
        // profile-derived daily target it should be measured against.
        Text(if (target != null) "$value/$target" else value, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
    }
}
