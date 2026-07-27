package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ActivityWeeklyMinutesCard(weeklyMinutes: Int, weekTrendPct: Int?) {
    val whoGoal = 150
    val pct = (weeklyMinutes.toFloat() / whoGoal).coerceIn(0f, 1f)
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
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
                    Text("$weeklyMinutes/$whoGoal min", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = if (pct >= 1f) semanticGreen() else AccentCoral, fontWeight = FontWeight.Bold)
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
}
