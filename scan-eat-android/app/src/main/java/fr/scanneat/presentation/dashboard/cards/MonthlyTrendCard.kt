package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import fr.scanneat.domain.engine.dashboard.WeekOverWeekDelta
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
internal fun MonthlyTrendCard(rollup: RollupResult, targets: DailyTargets?, language: String, delta: WeekOverWeekDelta? = null) {
  ScanEatCard(
    color = SurfaceVariant,
    contentPadding = PaddingValues(Spacing.L),
    verticalArrangement = Arrangement.spacedBy(Spacing.S),
  ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dashboard_month_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text(
                if (targets != null) "${rollup.avg.kcal.roundToInt()}/${targets.kcal.roundToInt()} kcal"
                else stringResource(R.string.dashboard_week_avg_kcal, rollup.avg.kcal.roundToInt()),
                style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
            )
        }
        // WeekDeltaCard already compares this week to last week - the 30-day view
        // right below it had no equivalent, no way to tell "is this month trending
        // up or down from the prior 30 days" without doing the arithmetic by hand.
        delta?.let { d ->
            val sign = if (d.kcal >= 0) "+" else ""
            val color = if (d.kcal <= 0) semanticGreen() else semanticAmber()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (d.kcal >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    null, tint = color, modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(R.string.dashboard_month_delta, "$sign${d.kcal.roundToInt()}"),
                    style = MaterialTheme.typography.labelSmall, color = color,
                )
            }
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
