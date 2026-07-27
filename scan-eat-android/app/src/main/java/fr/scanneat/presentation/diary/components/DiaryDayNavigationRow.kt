package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevDay) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.diary_cd_prev_day), tint = OnBackground) }
            Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
            IconButton(onClick = onNextDay, enabled = !isToday) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.diary_cd_next_day), tint = if (!isToday) OnBackground else OnBackground.copy(0.3f))
            }
            IconButton(onClick = onToggleCalendar) {
                Icon(Icons.Default.CalendarMonth, stringResource(R.string.diary_cd_calendar), tint = if (showCalendar) AccentCoral else OnBackground.copy(0.5f))
            }
            IconButton(onClick = onCopyPreviousDay) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.diary_cd_copy_previous_day), tint = OnBackground.copy(0.5f))
            }
        }
        if (!isToday) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.diary_today_button), color = AccentCoral, style = MaterialTheme.typography.labelMedium) }
        }
    }
}
