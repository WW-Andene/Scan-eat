package fr.scanneat.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun colorFor(source: CalendarSource): Color = when (source) {
    CalendarSource.MEALS      -> AccentCoral
    CalendarSource.WEIGHT     -> Gold
    CalendarSource.ACTIVITY   -> Warm
    CalendarSource.HYDRATION  -> Teal
    CalendarSource.FASTING    -> Violet
    CalendarSource.MEDICATION -> semanticGreen()
}

/**
 * Single consolidated calendar - previously Diary/Weight/Activity/Hydration
 * each embedded their own siloed single-domain mini-calendar (DiaryScreen's
 * own comment: "Journal tabs ... manage their own date context internally"),
 * so seeing everything logged on one day meant opening four screens and
 * flipping each to the same date by hand. This shows a multi-source dot per
 * day plus one combined detail panel for whichever date is selected.
 */
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel(), onBack: () -> Unit) {
    val month = viewModel.month.collectAsStateWithLifecycle()
    val selected = viewModel.selectedDate.collectAsStateWithLifecycle()
    val markers = viewModel.markers.collectAsStateWithLifecycle()
    val detail = viewModel.dayDetail.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val locale = Locale(language.value)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CARD))) {
                Surface(shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(Spacing.M)) {
                        MultiMarkerMonthGrid(
                            month = month.value, selected = selected.value, markers = markers.value, locale = locale,
                            onMonthChange = viewModel::setMonth, onDayClick = viewModel::selectDate,
                        )
                        // Legend - which color means which tracker, since a bare dot alone
                        // (unlike the existing single-domain MonthCalendar) is now ambiguous.
                        Row(Modifier.fillMaxWidth().padding(top = Spacing.S), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendDot(colorFor(CalendarSource.MEALS), stringResource(R.string.calendar_legend_meals))
                            LegendDot(colorFor(CalendarSource.WEIGHT), stringResource(R.string.calendar_legend_weight))
                            LegendDot(colorFor(CalendarSource.ACTIVITY), stringResource(R.string.calendar_legend_activity))
                            LegendDot(colorFor(CalendarSource.HYDRATION), stringResource(R.string.calendar_legend_hydration))
                            LegendDot(colorFor(CalendarSource.FASTING), stringResource(R.string.calendar_legend_fasting))
                            LegendDot(colorFor(CalendarSource.MEDICATION), stringResource(R.string.calendar_legend_medication))
                        }
                    }
                }
            }

            DayDetailCard(detail.value, locale)
            Spacer(Modifier.height(Spacing.XXL))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
    }
}

@Composable
private fun MultiMarkerMonthGrid(
    month: java.time.YearMonth,
    selected: java.time.LocalDate,
    markers: Map<java.time.LocalDate, Set<CalendarSource>>,
    locale: Locale,
    onMonthChange: (java.time.YearMonth) -> Unit,
    onDayClick: (java.time.LocalDate) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val fmt = DateTimeFormatter.ofPattern("EEEEE", locale)
    val monday = java.time.LocalDate.of(2024, 1, 1)
    val weekdayLabels = (0..6).map { monday.plusDays(it.toLong()).format(fmt).uppercase() }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, null, tint = OnBackground) }
            Text(
                month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() } + " " + month.year,
                style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, null, tint = OnBackground) }
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
                            val isToday = date == java.time.LocalDate.now()
                            val sources = markers[date].orEmpty()
                            Box(
                                modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape)
                                    .background(if (isSelected) AccentCoral.copy(0.2f) else Color.Transparent)
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                            sources.sortedBy { it.ordinal }.take(4).forEach { s ->
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

@Composable
private fun DayDetailCard(detail: CalendarDayDetail, locale: Locale) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CARD))) {
        Surface(shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    detail.date.format(dateFmt).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
                )
                if (detail.isEmpty) {
                    Text(stringResource(R.string.calendar_day_empty), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.5f))
                } else {
                    if (detail.mealCount > 0) {
                        DetailRow(colorFor(CalendarSource.MEALS), stringResource(R.string.calendar_day_meals, detail.mealCount, detail.kcal.roundToInt()))
                    }
                    detail.weightKg?.let {
                        DetailRow(colorFor(CalendarSource.WEIGHT), stringResource(R.string.calendar_day_weight, it))
                    }
                    if (detail.activities.isNotEmpty()) {
                        val totalMin = detail.activities.sumOf { it.minutes }
                        val totalKcal = detail.activities.sumOf { it.kcalBurned }
                        DetailRow(colorFor(CalendarSource.ACTIVITY), stringResource(R.string.calendar_day_activity, detail.activities.size, totalMin, totalKcal))
                        detail.activities.forEach { a ->
                            Text("· ${a.type.labelFr} (${a.minutes} min)", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                        }
                    }
                    if (detail.hydrationMl > 0) {
                        DetailRow(colorFor(CalendarSource.HYDRATION), stringResource(R.string.calendar_day_hydration, detail.hydrationMl))
                    }
                    detail.fastCompletion?.let { f ->
                        DetailRow(colorFor(CalendarSource.FASTING), stringResource(R.string.calendar_day_fasting, f.achievedHours, f.targetHours))
                    }
                    if (detail.medicationsTaken.isNotEmpty()) {
                        DetailRow(
                            colorFor(CalendarSource.MEDICATION),
                            stringResource(R.string.calendar_day_medication, detail.medicationsTaken.joinToString(", ") { it.medicationName }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnSurface)
    }
}
