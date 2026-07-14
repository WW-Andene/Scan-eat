package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.RollupResult
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 30-day trailing view, same bars-vs-target visual language as WeeklyBarsCard
 * but zoomed out - monthlyRollup() was fully implemented in
 * DashboardAggregator (same shape as weeklyRollup, which already has a card)
 * but had zero callers anywhere, so there was no way to see anything past a
 * single week on the Dashboard.
 */
@Composable
internal fun MonthlyTrendCard(rollup: RollupResult, targets: DailyTargets?, language: String) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dashboard_month_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    if (targets != null) "${rollup.avg.kcal.roundToInt()}/${targets.kcal.roundToInt()} kcal"
                    else stringResource(R.string.dashboard_week_avg_kcal, rollup.avg.kcal.roundToInt()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
                )
            }
            val peak = (listOf(targets?.kcal ?: 0.0) + rollup.days.map { it.kcal }).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(
                modifier              = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment     = Alignment.Bottom,
            ) {
                rollup.days.forEach { day ->
                    val frac = (day.kcal / peak).toFloat().coerceIn(0f, 1f)
                    val isToday = day.date == java.time.LocalDate.now()
                    val isOver  = targets != null && day.kcal > targets.kcal
                    val color   = when {
                        day.count == 0 -> OnSurface.copy(0.1f)
                        isOver         -> semanticRed().copy(0.7f)
                        else           -> AccentCoral.copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(if (day.count == 0) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                            .background(color),
                    )
                }
            }
            // 30 individual date labels would never fit - only the window's
            // start/end are printed, same information WeeklyBarsCard conveys
            // per-bar but compressed to fit a month at a glance.
            // Locale(language) - same as WeeklyBarsCard/DiaryScreen/WeightScreen - so month
            // abbreviations follow the in-app language setting, not the device default.
            val fmt = remember(language) { DateTimeFormatter.ofPattern("d MMM", Locale(language)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rollup.days.first().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f), fontSize = 9.sp)
                Text(
                    stringResource(R.string.dashboard_month_days_logged, rollup.daysLogged, rollup.days.size),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f), fontSize = 9.sp, textAlign = TextAlign.Center,
                )
                Text(rollup.days.last().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f), fontSize = 9.sp)
            }
        }
    }
  }
}
