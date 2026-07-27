package fr.scanneat.presentation.hydration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun HydrationWeeklyChart(weeklyIntake: List<Pair<LocalDate, Int>>, goalMl: Int, weeklyGoalMetDays: Int) {
    val goalMlCoerced = goalMl.coerceAtLeast(1)
    val peak = weeklyIntake.maxOfOrNull { it.second }?.coerceAtLeast(goalMlCoerced) ?: goalMlCoerced
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hydration_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                if (weeklyGoalMetDays > 0) {
                    Text(
                        stringResource(R.string.hydration_weekly_goal_met, weeklyGoalMetDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = semanticGreen(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
                weeklyIntake.forEach { (date, ml) ->
                    val frac = (ml.toFloat() / peak).coerceIn(0f, 1f)
                    val isToday = date == java.time.LocalDate.now()
                    val color = when {
                        ml == 0    -> OnBackground.copy(0.08f)
                        ml >= goalMlCoerced -> semanticGreen().copy(if (isToday) 1f else 0.7f)
                        else       -> semanticBlue().copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(if (frac == 0f) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                weeklyIntake.forEach { (date, _) ->
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == java.time.LocalDate.now()) semanticBlue() else OnBackground.copy(0.35f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                }
            }
        }
    }
}
