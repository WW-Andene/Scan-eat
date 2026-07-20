package fr.scanneat.presentation.biolism.evolution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── Shared chart primitives for the Evolution tab ──────────────────────────
// Generalizes the Canvas polyline WeightScreen.kt already draws for weight
// history (fill + line + dots, optional dashed target line) into a reusable
// composable over any Double-valued series, plus a compact bar-sparkline for
// rows where a full-size chart would be too heavy (matches the Row/Box bar
// pattern already used by GlobalSummaryCard/SessionAnalyticsCard in the Data
// tab, rather than introducing a third visual language).

/**
 * Shown wherever a card's underlying series has fewer than 2 points — new
 * users, or a metric this app hasn't started tracking long enough yet.
 * Never fabricates a trend from insufficient data.
 */
@Composable
internal fun NotEnoughDataNote() {
    Text(
        stringResource(R.string.biolism_evo_not_enough_data),
        style = MaterialTheme.typography.labelSmall,
        color = OnBackground.copy(0.4f),
    )
}

@Composable
internal fun LineTrendChart(
    points: List<Pair<LocalDate, Double>>,
    color: Color,
    dateFmt: DateTimeFormatter,
    valueLabel: @Composable (Double) -> String,
    targetValue: Double? = null,
    targetColor: Color = color,
    height: Dp = 64.dp,
) {
    if (points.size < 2) {
        NotEnoughDataNote()
        return
    }
    val sorted = points.sortedBy { it.first }
    val allValues = sorted.map { it.second } + listOfNotNull(targetValue)
    val minV = allValues.min()
    val maxV = allValues.max().coerceAtLeast(minV + 0.0001)
    val trendDescription = stringResource(
        R.string.biolism_evo_chart_cd,
        valueLabel(sorted.first().second),
        valueLabel(sorted.last().second),
        sorted.size,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = trendDescription },
    ) {
        val w = size.width
        val h = size.height
        val n = sorted.size
        val xStep = w / (n - 1).toFloat()
        fun xAt(i: Int) = i * xStep
        fun yAt(v: Double) = h * (1f - ((v - minV) / (maxV - minV)).toFloat()).coerceIn(0f, 1f)

        val fillPath = Path().apply {
            moveTo(xAt(0), h)
            sorted.forEachIndexed { i, e -> lineTo(xAt(i), yAt(e.second)) }
            lineTo(xAt(n - 1), h)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.12f))

        val linePath = Path().apply {
            sorted.forEachIndexed { i, e ->
                val x = xAt(i); val y = yAt(e.second)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(linePath, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        sorted.forEachIndexed { i, e ->
            val isLast = i == n - 1
            drawCircle(
                color = if (isLast) color else color.copy(0.4f),
                radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(xAt(i), yAt(e.second)),
            )
        }

        targetValue?.let { tv ->
            val ty = yAt(tv)
            drawLine(
                color = targetColor.copy(0.7f),
                start = Offset(0f, ty),
                end   = Offset(w, ty),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(sorted.first().first.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        Text(sorted.last().first.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
    }
}

/**
 * Compact horizontal bar row — same visual language as GlobalSummaryCard's
 * 7-session spark row, generalized to any point count. Used for the macro/
 * ketosis rows where 5-6 full LineTrendCharts stacked in one card would be
 * too heavy.
 */
@Composable
internal fun BarSparkline(values: List<Double>, color: Color, barHeight: Dp = 32.dp) {
    if (values.isEmpty()) {
        NotEnoughDataNote()
        return
    }
    val maxV = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
    Row(Modifier.fillMaxWidth().height(barHeight), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            val isLast = i == values.size - 1
            val frac = (v / maxV).coerceIn(0.0, 1.0)
            val alpha = 0.35f + 0.65f * (i / (values.size - 1).coerceAtLeast(1).toFloat())
            val barDp = if (frac > 0.02) (barHeight.value * frac).dp else 2.dp
            Box(
                Modifier
                    .weight(1f)
                    .height(barDp)
                    .background(if (isLast) color else color.copy(alpha = alpha), RoundedCornerShape(2.dp)),
            )
        }
    }
}
