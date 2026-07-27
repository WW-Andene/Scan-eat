package fr.scanneat.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.calendar.WeekSummary
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.semanticGreen
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun MultiMarkerMonthGrid(
    month: java.time.YearMonth,
    selected: java.time.LocalDate,
    markers: Map<java.time.LocalDate, Set<fr.scanneat.presentation.calendar.CalendarSource>>,
    locale: Locale,
    weekSummaries: Map<java.time.LocalDate, WeekSummary> = emptyMap(),
    onMonthChange: (java.time.YearMonth) -> Unit,
    onDayClick: (java.time.LocalDate) -> Unit,
    onWeekClick: (WeekSummary) -> Unit = {},
) {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val fmt = DateTimeFormatter.ofPattern("EEEEE", locale)
    val monday = java.time.LocalDate.of(2024, 1, 1)
    val weekdayLabels = (0..6).map { monday.plusDays(it.toLong()).format(fmt).uppercase() }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // Both chevrons previously had a null contentDescription - a TalkBack
            // user heard only "button" for month navigation.
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.calendar_cd_prev_month), tint = OnBackground) }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.calendar_cd_next_month), tint = OnBackground) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Matches the week-number column's 48dp width below so these labels
            // land over the day columns they actually label, not shifted left of them.
            Spacer(Modifier.size(48.dp))
            weekdayLabels.forEach { label ->
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), textAlign = TextAlign.Center)
            }
        }
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            // Week row — tappable area on the far left shows week number and triggers summary popup
            val firstDayInRow = month.atDay((row * 7 - leadingBlanks + 1).coerceIn(1, daysInMonth))
            val weekStart = firstDayInRow.minusDays(firstDayInRow.dayOfWeek.value.toLong() - 1)
            val ws = weekSummaries[weekStart]
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Week-number column — tappable when a summary is available.
                // Outer box holds Android's 48dp minimum touch target; the visible dot
                // stays a small 20dp circle centered inside it via contentAlignment.
                // Growing the visible dot itself (rather than just the tap target) would
                // eat into the 7 weighted day-of-month columns to its right, since this
                // is a fixed-width sibling in the same Row.
                val weekNumber = firstDayInRow.get(java.time.temporal.WeekFields.ISO.weekOfYear())
                val weekSummaryCd = stringResource(R.string.calendar_cd_week_summary, weekNumber)
                Box(
                    Modifier.size(48.dp)
                        .then(
                            if (ws != null) {
                                Modifier.clickable { onWeekClick(ws) }.semantics { contentDescription = weekSummaryCd }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape)
                            .background(if (ws != null && ws.totalKcal > 0) AccentCoral.copy(0.1f) else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$weekNumber",
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f),
                        )
                    }
                }
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leadingBlanks + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val isSelected = date == selected
                            val isToday = date == java.time.LocalDate.now()
                            val sources = markers[date].orEmpty()
                            // Adherence color-coding: shade day by how many sources logged data.
                            val adherenceAlpha = when (sources.size) { 0 -> 0f; 1, 2 -> 0.07f; 3, 4 -> 0.13f; else -> 0.22f }
                            val adherenceColor = semanticGreen().copy(adherenceAlpha)
                            Box(
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape)
                                    .background(if (isSelected) AccentCoral.copy(0.2f) else adherenceColor)
                                    .clickable { onDayClick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum", style = MaterialTheme.typography.bodySmall,
                                        color = when { isSelected -> AccentCoral; isToday -> OnBackground; else -> OnBackground.copy(0.7f) },
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (sources.isNotEmpty()) {
                                        // Previously .take(4) - by enum declaration order (MEALS,
                                        // WEIGHT, ACTIVITY, HYDRATION, FASTING, MEDICATION, NOTE) any
                                        // day with 4+ trackers logged deterministically dropped
                                        // FASTING/MEDICATION/NOTE, arguably the ones most worth
                                        // noticing (medication adherence, personal notes) - and the
                                        // more actively a user logs, the worse this got. All 7
                                        // possible sources are only 3dp dots with 1dp spacing (≤27dp
                                        // total), comfortably fitting a day cell without a cap.
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            sources.sortedBy { it.ordinal }.forEach { s ->
                                                Box(Modifier.size(3.dp).clip(CircleShape).background(colorFor(s)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
