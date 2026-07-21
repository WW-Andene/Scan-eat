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

/**
 * Daily "don't exceed" budgets (saturated fat / free sugars / salt) - DailyTargets
 * already computes all three (WHO-anchored, condition-adjusted for diabetes/
 * hypertension in PersonalScoreEngine.kt), and the raw daily totals were already
 * tracked in ConsumedNutrition, but neither Dashboard nor Diary ever showed a
 * "how much of today's budget is used" readout the way TodayMacroCard does for
 * protein/carbs/fat. Unlike MicronutrientCard's bars (more = better), these bars
 * flag *approaching or exceeding* the cap, not falling short of it.
 */
@Composable
internal fun NutrientBudgetCard(totals: ConsumedNutrition, targets: DailyTargets) {
    if (totals.energyKcal < 1.0) return

    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(
            stringResource(R.string.dashboard_budget_title),
            style = MaterialTheme.typography.titleSmall,
            color = OnSurface,
            fontWeight = FontWeight.SemiBold,
        )

        BudgetRow(stringResource(R.string.dashboard_budget_satfat), totals.saturatedFatG, targets.satFatGMax, "g", Gold)
        // Total sugars, not strictly "free sugars" - same approximation
        // PersonalScoreEngine.kt's own scan-result flag copy already makes
        // when comparing product.nutrition.sugarsG against freeSugarsGMax.
        // freeSugarsGIdeal (WHO's stricter "further reduction" 5%-of-kcal figure,
        // vs. the 10% freeSugarsGMax cap this row's bar already tracks) was
        // computed by PersonalScoreEngine.kt with nowhere to show it - only the
        // max ever reached the UI. Shown here as a caption, not a second bar,
        // since it's WHO's own aspirational figure, not a hard budget.
        BudgetRow(
            stringResource(R.string.dashboard_budget_sugars), totals.sugarsG, targets.freeSugarsGMax, "g", AccentCoral,
            idealCaption = stringResource(R.string.dashboard_budget_ideal, targets.freeSugarsGIdeal.roundToInt()),
        )
        BudgetRow(stringResource(R.string.dashboard_budget_salt), totals.saltG, targets.saltGMax, "g", Violet)
    }
}

@Composable
private fun BudgetRow(label: String, value: Double, max: Double, unit: String, color: Color, idealCaption: String? = null) {
    val pct = (value / max.coerceAtLeast(0.1)).toFloat()
    val isOver = pct > 1f
    Column(modifier = Modifier.fillMaxWidth()) {
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
                progress   = { pct.coerceIn(0f, 1f) },
                modifier   = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color      = if (isOver) semanticRed() else color,
                trackColor = OnSurface.copy(0.08f),
            )
            Text(
                "${value.roundToInt()}/${max.roundToInt()}$unit",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOver) semanticRed() else OnSurface.copy(0.5f),
                fontWeight = if (isOver) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.width(64.dp),
            )
        }
        idealCaption?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface.copy(0.4f),
                modifier = Modifier.padding(start = 72.dp + Spacing.S),
            )
        }
    }
}
