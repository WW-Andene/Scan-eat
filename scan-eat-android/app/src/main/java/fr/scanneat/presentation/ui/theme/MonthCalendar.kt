package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shared month-grid calendar. [markedDates] gets a small dot (e.g. days with a
 * logged weight/meal/activity entry); [selected] gets the accent ring. Purely
 * a date picker + at-a-glance marker - no event content is rendered inline,
 * callers show that separately for whichever day is selected.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate?,
    markedDates: Set<LocalDate> = emptySet(),
    accent: androidx.compose.ui.graphics.Color = AccentCoral,
    locale: Locale = Locale.getDefault(),
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    // ISO: Monday=1..Sunday=7 - leading blanks before the 1st to align the grid.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val weekdayLabels = weekdayLabels(locale)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // Both chevrons previously had a null contentDescription - a TalkBack
            // user heard only "button" for month navigation.
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Default.ChevronLeft, stringResource(R.string.calendar_cd_prev_month), tint = OnBackground)
            }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.calendar_cd_next_month), tint = OnBackground)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), textAlign = TextAlign.Center)
            }
        }
        val totalCells = leadingBlanks + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leadingBlanks + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val isSelected = date == selected
                            val isToday = date == LocalDate.now()
                            val isMarked = date in markedDates
                            Box(
                                modifier = Modifier
                                    .fillMaxSize().padding(2.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) accent.copy(0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onDayClick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNum", style = MaterialTheme.typography.bodySmall,
                                        color = when { isSelected -> accent; isToday -> OnBackground; else -> OnBackground.copy(0.7f) },
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (isMarked) {
                                        Box(Modifier.size(4.dp).clip(CircleShape).background(accent))
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

@Composable
private fun weekdayLabels(locale: Locale): List<String> {
    val fmt = DateTimeFormatter.ofPattern("EEEEE", locale)
    val monday = LocalDate.of(2024, 1, 1) // a known Monday
    return (0..6).map { monday.plusDays(it.toLong()).format(fmt).uppercase() }
}
