package fr.scanneat.presentation.calendar.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.calendar.MonthSummary
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Warm

@Composable
internal fun MonthSummaryBar(ms: MonthSummary) {
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
