package fr.scanneat.presentation.calendar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.calendar.components.DayDetailCard
import fr.scanneat.presentation.calendar.components.DetailRow
import fr.scanneat.presentation.calendar.components.LegendDot
import fr.scanneat.presentation.calendar.components.MonthSummaryBar
import fr.scanneat.presentation.calendar.components.MultiMarkerMonthGrid
import fr.scanneat.presentation.calendar.components.colorFor
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.util.Locale

/**
 * Single consolidated calendar - previously Diary/Weight/Activity/Hydration
 * each embedded their own siloed single-domain mini-calendar (DiaryScreen's
 * own comment: "Journal tabs ... manage their own date context internally"),
 * so seeing everything logged on one day meant opening four screens and
 * flipping each to the same date by hand. This shows a multi-source dot per
 * day plus one combined detail panel for whichever date is selected.
 */
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel(), onBack: () -> Unit, onOpenDate: (LocalDate) -> Unit = {}) {
    val month = viewModel.month.collectAsStateWithLifecycle()
    val selected = viewModel.selectedDate.collectAsStateWithLifecycle()
    val markers = viewModel.markers.collectAsStateWithLifecycle()
    val detail = viewModel.dayDetail.collectAsStateWithLifecycle()
    val weekSummaries = viewModel.weekSummaries.collectAsStateWithLifecycle()
    val monthSummary = viewModel.monthSummary.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val useImperial = viewModel.useImperial.collectAsStateWithLifecycle()
    val locale = Locale(language.value)
    var weekPopup by remember { mutableStateOf<WeekSummary?>(null) }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.calendar_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        // A single LazyColumn item wrapping this screen's whole (non-lazy) Column
        // body — not worth decomposing into per-section items, but still gets the
        // header true-scroll-under every other screen has via contentPadding
        // (which content can visually pass under) instead of Modifier.padding
        // (a hard inset content can never cross).
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
        ) {
            item {
            // verticalArrangement lives here now (on this inner Column), not on the
            // outer LazyColumn above — that only spaces between top-level items, and
            // this whole body is deliberately kept as a single one.
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Spacer(Modifier.height(Spacing.XS))
            ScanEatCard(contentPadding = PaddingValues(Spacing.M)) {
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
            DayDetailCard(detail.value, locale, useImperial = useImperial.value, onOpenDate = onOpenDate)
            Spacer(Modifier.height(Spacing.XXL))
            }
            }
        }
    }

    weekPopup?.let { ws ->
        AlertDialog(
            onDismissRequest = { weekPopup = null },
            containerColor = SurfaceVariant,
            shape = RoundedCornerShape(CardRadius.PROMINENT),
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
