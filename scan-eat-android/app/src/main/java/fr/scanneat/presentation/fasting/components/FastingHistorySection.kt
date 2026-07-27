package fr.scanneat.presentation.fasting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
internal fun Fasting7DayChart(history: List<FastCompletion>) {
    val today = LocalDate.now()
    // Map date-string → FastCompletion for quick lookup (one entry per day)
    val byDate = history.associateBy { it.date }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.fasting_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
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
                        date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()).replaceFirstChar { it.uppercaseChar() },
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
internal fun FastingHistoryStatsCard(history: List<FastCompletion>) {
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
            stringResource(R.string.fasting_stat_avg)     to "${String.format(Locale.US, "%.1f", avgHours)}h",
            stringResource(R.string.fasting_stat_record)  to "${longestH}h",
        ).forEach { (label, value) ->
            Surface(
                modifier = Modifier.weight(1f).glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                color = SurfaceVariant.copy(alpha = 0.42f),
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
    Fasting7DayChart(completed)
}

@Composable
internal fun FastingHistoryRow(completion: FastCompletion, onDelete: () -> Unit) {
    val c = completion
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(c.date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        Text(stringResource(R.string.fasting_history_entry, c.achievedHours, c.targetHours), style = MaterialTheme.typography.bodySmall, color = if (c.reached) semanticGreen() else semanticAmber())
        Icon(if (c.reached) Icons.Default.CheckCircle else Icons.Default.Close, null, tint = if (c.reached) semanticGreen() else OnSurface.copy(0.3f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.XS))
        // Previously the only way to fix a mis-tapped Finish (wrong hours logged)
        // was clearHistory(), which wipes all 90 entries and zeroes the streak -
        // matching the per-entry delete Weight/Activity/Medication already have.
        // Distinct icon/tint from the status glyph above (also a small X when not
        // reached) so the two aren't visually confusable as the same control.
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, stringResource(R.string.common_delete), tint = semanticRed().copy(0.5f), modifier = Modifier.size(16.dp))
        }
    }
}
