package fr.scanneat.presentation.hydration.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HydrationStreakRow(streakDays: Int, onOpenCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Improvement: 7-day adherence streak badge
        if (streakDays > 0) {
            Surface(shape = RoundedCornerShape(50), color = semanticBlue().copy(0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                ) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = semanticBlue(), modifier = Modifier.size(16.dp))
                    // stringResource, not a hardcoded "j" (French "jour") suffix - an
                    // English-language user saw this exact French fragment regardless
                    // of the app's own in-app language setting.
                    Text(
                        stringResource(R.string.common_streak_days_compact, streakDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = semanticBlue(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
        }
    }
}
