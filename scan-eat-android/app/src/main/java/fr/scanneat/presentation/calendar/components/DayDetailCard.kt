package fr.scanneat.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.activity.typeLabels
import fr.scanneat.presentation.calendar.CalendarDayDetail
import fr.scanneat.presentation.calendar.CalendarSource
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.dispWeight
import fr.scanneat.util.formatDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun DayDetailCard(detail: CalendarDayDetail, locale: Locale, useImperial: Boolean = false, onOpenDate: (LocalDate) -> Unit = {}) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                detail.date.format(dateFmt).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
            )
            // The month grid + this panel previously only ever displayed what
            // happened on a day - spotting a gap here still meant manually
            // navigating to Diary and re-selecting the same date by hand.
            TextButton(onClick = { onOpenDate(detail.date) }) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.calendar_open_in_diary), color = AccentCoral, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (detail.isEmpty) {
            Text(stringResource(R.string.calendar_day_empty), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.5f))
        } else {
            if (detail.mealCount > 0) {
                DetailRow(colorFor(CalendarSource.MEALS), stringResource(R.string.calendar_day_meals, detail.mealCount, detail.kcal.roundToInt()))
            }
            detail.weightKg?.let {
                DetailRow(colorFor(CalendarSource.WEIGHT), stringResource(R.string.calendar_day_weight, dispWeight(it, useImperial)))
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
            // Expenses was the one tracker never surfaced in this unified day panel -
            // see CalendarDayDetail.expensesTotal's own doc comment.
            if (detail.expensesTotal > 0.0) {
                DetailRow(colorFor(CalendarSource.EXPENSES), stringResource(R.string.calendar_day_expenses, detail.expensesTotal.formatDecimal(2)))
            }
        }
    }
}
