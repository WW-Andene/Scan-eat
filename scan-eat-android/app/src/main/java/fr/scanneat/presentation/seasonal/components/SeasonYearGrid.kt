package fr.scanneat.presentation.seasonal.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticGreen
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * 4x3 real month grid (Jan-Dec) replacing the previous chevron one-month
 * nav - each cell is itself the month selector (tap to browse that month),
 * shaded by how many products are in season that month (season density),
 * and overridden with a distinct highlight color for [highlightMonths] when
 * a specific product is selected. Extracted from SeasonalProduceScreen (§T1
 * composition-root split) into components/, matching every sibling screen's
 * convention (recipes/, settings/, activity/, dashboard/cards).
 */
@Composable
internal fun SeasonYearGrid(
    selectedMonth: Int,
    currentMonth: Int,
    highlightMonths: Set<Int>?,
    countByMonth: Map<Int, Int>,
    locale: Locale,
    onMonthClick: (Int) -> Unit,
) {
    val maxCount = (countByMonth.values.maxOrNull() ?: 1).coerceAtLeast(1)
    ScanEatCard(contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                (1..3).forEach { col ->
                    val month = row * 3 + col
                    SeasonMonthCell(
                        month = month,
                        isSelected = month == selectedMonth,
                        isCurrent = month == currentMonth,
                        isHighlighted = highlightMonths?.contains(month) == true,
                        density = (countByMonth[month] ?: 0).toFloat() / maxCount,
                        locale = locale,
                        modifier = Modifier.weight(1f),
                        onClick = { onMonthClick(month) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonMonthCell(
    month: Int,
    isSelected: Boolean,
    isCurrent: Boolean,
    isHighlighted: Boolean,
    density: Float,
    locale: Locale,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Season-density fill (how many products are in season this month) stays
    // visible underneath the selection/highlight ring rather than being
    // replaced by it, so the indicator this whole grid exists for is never
    // hidden by browsing/selecting.
    val densityFill = AccentCoral.copy(alpha = 0.06f + density * 0.22f)
    val fill = if (isHighlighted) semanticGreen().copy(alpha = 0.28f) else densityFill
    val border = when {
        isHighlighted -> BorderStroke(1.5.dp, semanticGreen())
        isSelected -> BorderStroke(1.5.dp, AccentCoral)
        else -> null
    }
    Box(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(CardRadius.CONTROL))
            .background(fill)
            .let { if (border != null) it.border(border, RoundedCornerShape(CardRadius.CONTROL)) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
            Text(
                Month.of(month).getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) AccentCoral else OnBackground,
                fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal,
            )
            if (isCurrent) {
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Gold))
            }
        }
    }
}
