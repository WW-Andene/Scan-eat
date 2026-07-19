package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.RollupResult
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.presentation.ui.theme.*
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun WeeklyBarsCard(rollup: RollupResult, targets: DailyTargets?, language: String) {
    val locale = remember(language) { Locale(language) }
    val peak = (listOf(targets?.kcal ?: 0.0) + rollup.days.map { it.kcal }).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val targetFrac = targets?.let { (it.kcal / peak).toFloat().coerceIn(0f, 1f) }

  ScanEatCard(
    color = SurfaceVariant,
    contentPadding = PaddingValues(Spacing.L),
    verticalArrangement = Arrangement.spacedBy(Spacing.S),
  ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.dashboard_week_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Text(
                if (targets != null) "${rollup.avg.kcal.roundToInt()}/${targets.kcal.roundToInt()} kcal"
                else stringResource(R.string.dashboard_week_avg_kcal, rollup.avg.kcal.roundToInt()),
                style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
            )
        }
        // Bars overlaid with a dashed target line via Canvas
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                verticalAlignment = Alignment.Bottom,
            ) {
                rollup.days.forEach { day ->
                    val frac   = (day.kcal / peak).toFloat().coerceIn(0f, 1f)
                    val isToday = day.date == java.time.LocalDate.now()
                    val isOver  = targets != null && day.kcal > targets.kcal * 1.1
                    val isOnTarget = targets != null && day.kcal >= targets.kcal * 0.9 && !isOver
                    val color   = when {
                        day.count == 0 -> OnSurface.copy(0.1f)
                        isOver         -> semanticRed().copy(0.7f)
                        isOnTarget     -> semanticGreen().copy(if (isToday) 1f else 0.7f)
                        else           -> AccentCoral.copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(if (day.count == 0) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            // Dashed target line — only shown when targets are set
            if (targetFrac != null) {
                val dashColor = OnSurface.copy(0.35f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val y = size.height * (1f - targetFrac)
                    drawLine(
                        color = dashColor,
                        start = Offset(0f, y),
                        end   = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                    )
                }
            }
        }
        // Day labels
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            rollup.days.forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                        .replaceFirstChar { it.uppercaseChar() },
                    modifier  = Modifier.weight(1f),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = if (day.date == java.time.LocalDate.now()) AccentCoral else OnSurface.copy(0.4f),
                    textAlign = TextAlign.Center,
                    fontSize  = 9.sp,
                )
            }
        }
  }
}

@Composable
internal fun BestWorstDayCard(rollup: RollupResult, targets: DailyTargets?, language: String) {
    val locale = remember(language) { Locale(language) }
    val loggedDays = rollup.days.filter { it.count > 0 }
    if (loggedDays.size < 2) return

    val best  = loggedDays.minByOrNull { kotlin.math.abs(it.kcal - (targets?.kcal ?: it.kcal)) } ?: return
    val worst = loggedDays.maxByOrNull { kotlin.math.abs(it.kcal - (targets?.kcal ?: it.kcal)) } ?: return
    if (best.date == worst.date) return

    ScanEatCard(
        color = SurfaceVariant,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
            Text(stringResource(R.string.dashboard_best_worst_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                BestWorstItem(
                    modifier = Modifier.weight(1f),
                    label    = stringResource(R.string.dashboard_best_day),
                    day      = best,
                    color    = semanticGreen(),
                    locale   = locale,
                )
                BestWorstItem(
                    modifier = Modifier.weight(1f),
                    label    = stringResource(R.string.dashboard_worst_day),
                    day      = worst,
                    color    = semanticRed(),
                    locale   = locale,
                )
            }
    }
}

@Composable
private fun BestWorstItem(
    modifier: Modifier,
    label: String,
    day: fr.scanneat.domain.engine.dashboard.DayBucket,
    color: androidx.compose.ui.graphics.Color,
    locale: Locale,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(CardRadius.CONTROL), color = color.copy(0.1f)) {
        Column(Modifier.padding(Spacing.S), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnSurface,
            )
            Text("${day.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
        }
    }
}
