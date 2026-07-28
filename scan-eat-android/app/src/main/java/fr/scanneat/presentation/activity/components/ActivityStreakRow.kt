package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ActivityStreakRow(streakDays: Int, onOpenCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Consecutive-days activity streak badge. Uses Warm (this screen's own
        // accent, see ActivityScreen's ambientGloom) - semanticRed() was wrong
        // here: it's the app-wide "danger" color, and a streak is a positive
        // metric, not a warning.
        if (streakDays > 0) {
            StreakBadge(streakDays, Warm)
        } else {
            Spacer(Modifier.width(1.dp))
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
        }
    }
}
