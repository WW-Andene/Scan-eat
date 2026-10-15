package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.CaffeineBudget
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardEmphasis
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import kotlin.math.roundToInt

/** Today's caffeine against the EFSA limit for this profile (see CaffeineBudget). */
@Composable
internal fun CaffeineBudgetCard(c: CaffeineBudget) {
    ScanEatCard(
        emphasis = CardEmphasis.SECONDARY,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(stringResource(R.string.diary_caffeine_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(stringResource(R.string.diary_caffeine_total, c.totalMg.roundToInt(), c.limitMg.roundToInt()),
            style = MaterialTheme.typography.bodyMedium, color = if (c.over) AccentCoral else OnSurface)
        LinearProgressIndicator(progress = { if (c.limitMg <= 0) (if (c.totalMg > 0) 1f else 0f) else (c.totalMg / c.limitMg).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
            color = if (c.over) AccentCoral else MaterialTheme.colorScheme.primary)
        if (c.highSingleDoses.isNotEmpty()) Text(stringResource(R.string.diary_caffeine_single_dose, c.highSingleDoses.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        if (c.fromReductionPlan) Text(stringResource(R.string.diary_caffeine_plan_cap),
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
        if (c.estimatedMg > 0) Text(stringResource(R.string.diary_caffeine_estimated, c.estimatedMg.roundToInt()),
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
        Text(stringResource(R.string.diary_caffeine_source), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}
