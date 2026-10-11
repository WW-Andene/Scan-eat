package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/** [goalMinutes] defaults to the WHO 150min/week figure but is user-
 *  overridable (see ActivityGoalEditorDialog/ActivityViewModel.
 *  customWeeklyGoalMinutes) - previously a flat, non-editable constant. */
@Composable
internal fun ActivityWeeklyMinutesCard(weeklyMinutes: Int, weekTrendPct: Int?, goalMinutes: Int = 150, hasCustomGoal: Boolean = false, onSetGoal: ((Int?) -> Unit)? = null) {
    var showGoalEditor by remember { mutableStateOf(false) }
    val pct = (weeklyMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.L),
        onClick = onSetGoal?.let { { showGoalEditor = true } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_weekly_minutes_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    weekTrendPct?.let { trend ->
                        val (trendColor, trendIcon) = when {
                            trend > 0  -> semanticGreen() to "↑"
                            trend < 0  -> semanticRed()   to "↓"
                            else       -> OnSurface.copy(0.5f) to "→"
                        }
                        Text("$trendIcon${kotlin.math.abs(trend)}%", style = MaterialTheme.typography.labelSmall, color = trendColor)
                    }
                    Text("$weeklyMinutes/$goalMinutes min", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = if (pct >= 1f) semanticGreen() else AccentCoral, fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(
                progress    = { pct },
                modifier    = Modifier.fillMaxWidth(),
                color       = if (pct >= 1f) semanticGreen() else AccentCoral,
                trackColor  = SurfaceVariant,
            )
            if (pct >= 1f) Text(stringResource(R.string.activity_who_goal_reached), style = MaterialTheme.typography.labelSmall, color = semanticGreen())
            else Text(stringResource(R.string.activity_who_goal_hint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
        }
    }
    if (showGoalEditor && onSetGoal != null) {
        ActivityGoalEditorDialog(
            initialGoalText = goalMinutes.toString(),
            hasCustomGoal = hasCustomGoal,
            onDismiss = { showGoalEditor = false },
            onReset = { onSetGoal(null); showGoalEditor = false },
            onConfirm = { text ->
                text.toIntOrNull()?.takeIf { it > 0 }?.coerceIn(1, 2000)?.let { onSetGoal(it) }
                showGoalEditor = false
            },
        )
    }
}
