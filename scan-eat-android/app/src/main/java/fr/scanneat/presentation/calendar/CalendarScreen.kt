package fr.scanneat.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import fr.scanneat.presentation.activity.typeLabels
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
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
    CalendarSource.NOTE       -> OnBackground.copy(0.5f)
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
    val weekSummaries = viewModel.weekSummaries.collectAsStateWithLifecycle()
    val monthSummary = viewModel.monthSummary.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val locale = Locale(language.value)
    var weekPopup by remember { mutableStateOf<WeekSummary?>(null) }

    Scaffold(
        topBar = {
            FloatingTopBar(
                title = { Text(stringResource(R.string.calendar_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Spacer(Modifier.height(Spacing.XS))
            ScanEatCard(color = SurfaceVariant, contentPadding = PaddingValues(Spacing.M)) {
                MultiMarkerMonthGrid(
                    month = month.value, selected = selected.value, markers = markers.value, locale = locale,
                    weekSummaries = weekSummaries.value,
                    onMonthChange = viewModel::setMonth, onDayClick = viewModel::selectDate,
                    onWeekClick = { weekPopup = it },
                )
                // Legend - which color means which tracker, since a bare dot alone
                // (unlike the existing single-domain MonthCalendar) is now ambiguous.
                // Horizontally scrollable rather than SpaceEvenly-only - a 7th entry
                // (NOTE) pushed this past what reliably fits on a narrow phone width
                // without wrapping or clipping.
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.S).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    LegendDot(colorFor(CalendarSource.MEALS), stringResource(R.string.calendar_legend_meals))
                    LegendDot(colorFor(CalendarSource.WEIGHT), stringResource(R.string.calendar_legend_weight))
                    LegendDot(colorFor(CalendarSource.ACTIVITY), stringResource(R.string.calendar_legend_activity))
                    LegendDot(colorFor(CalendarSource.HYDRATION), stringResource(R.string.calendar_legend_hydration))
                    LegendDot(colorFor(CalendarSource.FASTING), stringResource(R.string.calendar_legend_fasting))
                    LegendDot(colorFor(CalendarSource.MEDICATION), stringResource(R.string.calendar_legend_medication))
                    LegendDot(colorFor(CalendarSource.NOTE), stringResource(R.string.calendar_legend_note))
                }
            }

            monthSummary.value?.let { ms ->
                MonthSummaryBar(ms)
            }
            DayDetailCard(detail.value, locale)
            Spacer(Modifier.height(Spacing.XXL))
        }
    }

    weekPopup?.let { ws ->
        AlertDialog(
            onDismissRequest = { weekPopup = null },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.calendar_week_popup_title, ws.weekStart.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", locale))), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    DetailRow(colorFor(CalendarSource.MEALS), stringResource(R.string.calendar_week_kcal, ws.totalKcal))
                    DetailRow(colorFor(CalendarSource.ACTIVITY), stringResource(R.string.calendar_week_activity, ws.activeMinutes))
                    if (ws.hydrationMl > 0) DetailRow(colorFor(CalendarSource.HYDRATION), stringResource(R.string.calendar_week_hydration, ws.hydrationMl))
                    Text(stringResource(R.string.calendar_week_active_days, ws.activeDays), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { weekPopup = null }) { Text(stringResource(R.string.common_close), color = OnBackground.copy(0.6f)) } },
        )
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
                            .background(if (ws != null && ws.totalKcal > 0) AccentCoral.copy(0.1f) else Color.Transparent),
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

@Composable
private fun DayDetailCard(detail: CalendarDayDetail, locale: Locale) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    ScanEatCard(color = SurfaceVariant, contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
                // Was a.type.labelFr - always French regardless of the app's language
                // setting. ActivityScreen already has localized labels for these same
                // types, reused here instead.
                val activityTypeLabels = typeLabels()
                detail.activities.forEach { a ->
                    Text("· ${activityTypeLabels[a.type] ?: a.type.name} (${a.minutes} min)", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
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
            // The month grid's NOTE dot previously had nowhere to lead - tapping a
            // day with a note to actually read it found nothing here.
            if (detail.note.isNotBlank()) {
                DetailRow(colorFor(CalendarSource.NOTE), stringResource(R.string.calendar_day_note, detail.note))
            }
        }
    }
}

@Composable
private fun DetailRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnSurface)
    }
}

@Composable
private fun MonthSummaryBar(ms: MonthSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        listOf(
            Triple(AccentCoral, Icons.Default.RestaurantMenu, stringResource(R.string.calendar_month_kcal, ms.totalKcal)),
            Triple(Warm,        Icons.Default.DirectionsRun,  stringResource(R.string.calendar_month_minutes, ms.activeMinutes)),
            Triple(Teal,        Icons.Default.WaterDrop,       stringResource(R.string.calendar_month_hydration, ms.hydrationMl)),
            Triple(Gold,        Icons.Default.CalendarMonth,   stringResource(R.string.calendar_month_days, ms.activeDays)),
        ).forEach { (color, icon, label) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                color = color.copy(0.08f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.XS, vertical = Spacing.S),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
