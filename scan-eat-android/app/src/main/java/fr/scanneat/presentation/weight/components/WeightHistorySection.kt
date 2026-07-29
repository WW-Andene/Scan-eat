package fr.scanneat.presentation.weight.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.WeightEntry
import fr.scanneat.data.repository.health.WeightSummary
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.ui.theme.dispWeight as sharedDispWeight
import fr.scanneat.util.formatDecimal
import java.time.format.DateTimeFormatter

@Composable
internal fun WeightUnitToggleRow(useImperial: Boolean, onUnitChange: (Boolean) -> Unit, onOpenCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Rounded.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(false to "kg", true to "lb").forEach { (imperial, label) ->
                FilterChip(
                    selected = useImperial == imperial,
                    onClick = { onUnitChange(imperial) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                )
            }
        }
    }
}

@Composable
internal fun WeightSummaryCard(
    summary: WeightSummary,
    forecast: WeightForecast,
    goalWeightKg: Double?,
    heightCm: Double?,
    loggingStreakDays: Int,
    dispWeight: (Double) -> String,
) {
    val s = summary
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(dispWeight(s.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                val sign = if (s.deltaKg >= 0) "+" else ""
                // Previously hardcoded "down = green, up = red" regardless of the
                // user's actual goal — a user with a gain goal (goalWeightKg above
                // current weight, e.g. a bulk/recovery program) saw progress toward
                // their own goal colored red.
                val wantsGain = goalWeightKg?.let { it > s.latestKg } ?: false
                val dColor = if (wantsGain) {
                    if (s.deltaKg >= 0) semanticGreen() else semanticRed()
                } else {
                    if (s.deltaKg <= 0) semanticGreen() else semanticRed()
                }
                Text(stringResource(R.string.weight_delta_kg, "$sign${dispWeight(kotlin.math.abs(s.deltaKg))}"), style = MaterialTheme.typography.labelSmall, color = dColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                val tSign = if (s.trendKgPerWeek >= 0) "+" else ""
                Text(stringResource(R.string.weight_trend_kg_week, "$tSign${dispWeight(kotlin.math.abs(s.trendKgPerWeek))}"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                if (forecast is WeightForecast.Ok) {
                    Text(stringResource(R.string.weight_goal_forecast, forecast.days), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                }
            }
        }
        // BMI row — only shown when profile height is set
        heightCm?.let { hcm ->
            val hm = hcm / 100.0
            val bmi = s.latestKg / (hm * hm)
            val (bmiLabel, bmiColor) = when {
                bmi < 18.5 -> stringResource(R.string.weight_bmi_underweight) to semanticBlue()
                bmi < 25.0 -> stringResource(R.string.weight_bmi_normal) to semanticGreen()
                bmi < 30.0 -> stringResource(R.string.weight_bmi_overweight) to semanticAmber()
                else       -> stringResource(R.string.weight_bmi_obese) to semanticRed()
            }
            HorizontalDivider(color = OnSurface.copy(0.08f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weight_bmi_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(bmi.formatDecimal(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = bmiColor)
                    Surface(shape = RoundedCornerShape(4.dp), color = bmiColor.copy(0.15f)) {
                        Text(bmiLabel, style = MaterialTheme.typography.labelSmall, color = bmiColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        goalWeightKg?.let { goal ->
            HorizontalDivider(color = OnSurface.copy(0.08f))
            val toGoal = s.latestKg - goal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.weight_goal_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                Text(
                    stringResource(R.string.weight_goal_delta, "${if (toGoal > 0) "−" else "+"}${dispWeight(kotlin.math.abs(toGoal))}", dispWeight(goal)),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = if (kotlin.math.abs(toGoal) < 0.5) semanticGreen() else AccentCoral,
                )
            }
        }
        if (loggingStreakDays > 0) {
            HorizontalDivider(color = OnSurface.copy(0.08f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.weight_logging_streak_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                Surface(shape = RoundedCornerShape(50), color = AccentCoral.copy(0.15f)) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Icon, not the 🔥 emoji baked into the string before -
                        // same LocalFireDepartment streak-badge convention already
                        // used by Activity/Medication/Fasting/Hydration's own streaks.
                        // design-aesthetic-audit §DC4: was tinted Gold - the
                        // Biolism-domain accent - despite every other tracker's own
                        // streak badge using its own established feature accent
                        // (Activity=Warm, Medication=Teal, Hydration=blue) and this
                        // same card already using AccentCoral for its own goal-delta
                        // indicator above. AccentCoral matches this screen's own
                        // established accent instead of an arbitrary borrowed one.
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                        Text(
                            stringResource(R.string.weight_logging_streak_value, loggingStreakDays),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeightTrendChart(chartEntries: List<WeightEntry>, goalKg: Double?, fmt: DateTimeFormatter, dispWeight: (Double) -> String) {
    // Improvement: include goal weight in range so the dashed goal line is always visible
    val allWeights = chartEntries.map { it.weightKg } + listOfNotNull(goalKg)
    val minW = allWeights.min()
    val maxW = allWeights.max().coerceAtLeast(minW + 0.5)
    val lineColor = AccentCoral
    val dotColor  = AccentCoral
    val goalLineColor = semanticGreen()
    val trendDescription = stringResource(
        R.string.weight_trend_cd,
        dispWeight(chartEntries.first().weightKg),
        dispWeight(chartEntries.last().weightKg),
        chartEntries.size,
    )
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.weight_trend_caption, chartEntries.size), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            Text(dispWeight(chartEntries.last().weightKg), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = AccentCoral)
        }
        Spacer(Modifier.height(Spacing.S))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clearAndSetSemantics { contentDescription = trendDescription },
        ) {
            val w = size.width
            val h = size.height
            val n = chartEntries.size
            if (n < 2) return@Canvas
            val xStep = w / (n - 1).toFloat()
            fun xAt(i: Int) = i * xStep
            fun yAt(kg: Double) = h * (1f - ((kg - minW) / (maxW - minW)).toFloat()).coerceIn(0f, 1f)

            // Fill area under the line
            val fillPath = Path().apply {
                moveTo(xAt(0), h)
                chartEntries.forEachIndexed { i, e -> lineTo(xAt(i), yAt(e.weightKg)) }
                lineTo(xAt(n - 1), h)
                close()
            }
            drawPath(fillPath, color = lineColor.copy(alpha = 0.12f))

            // Line
            val linePath = Path().apply {
                chartEntries.forEachIndexed { i, e ->
                    val x = xAt(i); val y = yAt(e.weightKg)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // Dots
            chartEntries.forEachIndexed { i, e ->
                val isLast = i == n - 1
                drawCircle(
                    color = if (isLast) dotColor else dotColor.copy(0.4f),
                    radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                    center = Offset(xAt(i), yAt(e.weightKg)),
                )
            }

            // Goal line — dashed horizontal at the target weight
            goalKg?.let { gk ->
                val gy = yAt(gk)
                drawLine(
                    color = goalLineColor.copy(0.7f),
                    start = Offset(0f, gy),
                    end   = Offset(w, gy),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                )
            }
        }
        Spacer(Modifier.height(Spacing.XS))
        // Bumped from 0.35f - a UI/UX audit flagged these axis dates
        // as real chart data rendered too faint against the dark surface.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(chartEntries.first().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            Text(chartEntries.last().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        }
    }
}

@Composable
internal fun WeeklyAverageCard(thisWeek: Double, lastWeek: Double, useImperial: Boolean) {
    val delta = thisWeek - lastWeek
    val dColor = if (delta < -0.1) semanticGreen() else if (delta > 0.1) semanticRed() else OnSurface.copy(0.6f)
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(Spacing.M), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.weight_weekly_avg_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Text(sharedDispWeight(thisWeek, useImperial), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.weight_vs_last_week, sharedDispWeight(lastWeek, useImperial)), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                }
            }
            Surface(shape = RoundedCornerShape(50), color = dColor.copy(0.15f)) {
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "$sign${(if (useImperial) delta * KG_TO_LB else delta).formatDecimal()} ${if (useImperial) "lb" else "kg"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                )
            }
        }
    }
}

@Composable
internal fun WeightEntryRow(entry: WeightEntry, delta: Double?, useImperial: Boolean, fmt: DateTimeFormatter, dispWeight: (Double) -> String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val e = entry
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M), onClick = onEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.date.format(fmt), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                if (e.notes.isNotBlank()) {
                    Text(e.notes, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                }
            }
            if (delta != null) {
                val dColor = if (delta < -0.05) semanticGreen() else if (delta > 0.05) semanticRed() else OnSurface.copy(0.4f)
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "$sign${(if (useImperial) delta * KG_TO_LB else delta).formatDecimal()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = Spacing.XS),
                )
            }
            Text(dispWeight(e.weightKg), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
