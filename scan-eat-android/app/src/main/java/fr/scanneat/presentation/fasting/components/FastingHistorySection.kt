package fr.scanneat.presentation.fasting.components

import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
internal fun Fasting7DayChart(history: List<FastCompletion>, language: String) {
    val today = LocalDate.now()
    // Map date-string → FastCompletion for quick lookup (one entry per day)
    val byDate = history.associateBy { it.date }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
        // app-audit §E5: this standalone chart card had no shadowElevation at all,
        // unlike its sibling stat tiles (FastingHistoryStatsCard, below) in this
        // same file which already carry 3dp.
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.fasting_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            // design-aesthetic-audit §DDV: same class of gap just fixed in Medication's
            // adherence chart - bars here encode reached/partial/missed purely through
            // color, with no textual fallback (WCAG 1.4.1). Reusing the same LegendDot
            // component Calendar/Dashboard/Medication already share for this.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                fr.scanneat.presentation.calendar.components.LegendDot(semanticGreen().copy(0.8f), stringResource(R.string.fasting_legend_reached))
                fr.scanneat.presentation.calendar.components.LegendDot(semanticAmber().copy(0.7f), stringResource(R.string.fasting_legend_partial))
                fr.scanneat.presentation.calendar.components.LegendDot(semanticRed().copy(0.5f), stringResource(R.string.fasting_legend_missed))
            }
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
                (6 downTo 0).forEach { daysBack ->
                    val date = today.minusDays(daysBack.toLong())
                    val dateKey = date.toString()
                    val entry = byDate[dateKey]
                    val frac = if (entry != null && entry.targetHours > 0)
                        (entry.achievedHours.toFloat() / entry.targetHours).coerceIn(0f, 1f)
                    else 0f
                    val color = when {
                        entry == null -> OnSurface.copy(0.08f)
                        entry.reached -> semanticGreen().copy(0.8f)
                        frac > 0.5f   -> semanticAmber().copy(0.7f)
                        else          -> semanticRed().copy(0.5f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(if (frac == 0f) 0.06f else frac.coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                (6 downTo 0).forEach { daysBack ->
                    val date = today.minusDays(daysBack.toLong())
                    Text(
                        // In-app language, not Locale.getDefault() - see WeightScreen's own
                        // doc comment on the identical fix; this file's own header comment
                        // on ActivityWeeklyBurnChart claimed this exact chart was already the
                        // "good example" other charts should copy, but it was still
                        // device-locale-driven itself.
                        date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale(language)).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysBack == 0) AccentCoral else OnSurface.copy(0.35f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FastingHistoryStatsCard(history: List<FastCompletion>, language: String) {
    val completed = history
    val successCount = completed.count { it.reached }
    val avgHours = completed.map { it.achievedHours }.average()
    val longestH = completed.maxOf { it.achievedHours }
    Text(stringResource(R.string.fasting_history_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(Spacing.S))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        listOf(
            stringResource(R.string.fasting_stat_total)   to "${completed.size}",
            stringResource(R.string.fasting_stat_success) to "$successCount/${completed.size}",
            stringResource(R.string.fasting_stat_avg)     to "${avgHours.formatDecimal(1)}h",
            stringResource(R.string.fasting_stat_record)  to "${longestH}h",
        ).forEach { (label, value) ->
            Surface(
                modifier = Modifier.weight(1f).glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                color = SurfaceVariant.copy(alpha = 0.42f),
                shadowElevation = 3.dp,
            ) {
                Column(modifier = Modifier.padding(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleSmall, color = AccentCoral, fontWeight = FontWeight.Bold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.M))
    // 7-day consistency mini-chart: one bar per day, height = achieved/target
    Fasting7DayChart(completed, language)
}

@Composable
internal fun FastingHistoryRow(completion: FastCompletion, onDelete: () -> Unit) {
    val c = completion
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(c.date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        Text(stringResource(R.string.fasting_history_entry, c.achievedHours, c.targetHours), style = MaterialTheme.typography.bodySmall, color = if (c.reached) semanticGreen() else semanticAmber())
        Icon(if (c.reached) Icons.Rounded.CheckCircle else TablerIcons.X, null, tint = if (c.reached) semanticGreen() else OnSurface.copy(0.3f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.XS))
        // Previously the only way to fix a mis-tapped Finish (wrong hours logged)
        // was clearHistory(), which wipes all 90 entries and zeroes the streak -
        // matching the per-entry delete Weight/Activity/Medication already have.
        // Distinct icon/tint from the status glyph above (also a small X when not
        // reached) so the two aren't visually confusable as the same control.
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.common_delete), tint = semanticRed().copy(0.5f), modifier = Modifier.size(16.dp))
        }
    }
}
