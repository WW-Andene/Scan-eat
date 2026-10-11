package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ChevronRight
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Copy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground

@Composable
internal fun DiaryDayNavigationRow(
    dateLabel: String,
    isToday: Boolean,
    showCalendar: Boolean,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToggleCalendar: () -> Unit,
    onCopyPreviousDay: () -> Unit,
    onToday: () -> Unit,
) {
    // User-reported: SpaceBetween left the nav+date+icon group pinned to the
    // left edge with a large dead void on the right whenever isToday was true
    // (the only case with nothing to space against - the "Aujourd'hui" button
    // only renders for a past/future day). Centered instead in that case, so
    // the row reads as a deliberately-centered date module rather than
    // left-stuck content with unused space beside it.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (isToday) Arrangement.Center else Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevDay) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.diary_cd_prev_day), tint = OnBackground) }
            Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
            IconButton(onClick = onNextDay, enabled = !isToday) {
                Icon(TablerIcons.ChevronRight, stringResource(R.string.diary_cd_next_day), tint = if (!isToday) OnBackground else OnBackground.copy(0.3f))
            }
            IconButton(onClick = onToggleCalendar) {
                Icon(TablerIcons.Calendar, stringResource(R.string.diary_cd_calendar), tint = if (showCalendar) AccentCoral else OnBackground.copy(0.5f))
            }
            IconButton(onClick = onCopyPreviousDay) {
                Icon(TablerIcons.Copy, stringResource(R.string.diary_cd_copy_previous_day), tint = OnBackground.copy(0.5f))
            }
        }
        if (!isToday) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.diary_today_button), color = AccentCoral, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
