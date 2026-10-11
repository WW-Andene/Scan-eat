package fr.scanneat.presentation.calendar.components

import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Activity
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Droplet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import fr.scanneat.R
import fr.scanneat.presentation.calendar.MonthSummary
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Warm
import fr.scanneat.presentation.ui.theme.IconSize

@Composable
internal fun MonthSummaryBar(ms: MonthSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        // app-audit note: Warm/Teal here deliberately match CalendarSourceColor's
        // Activity/Hydration entries (same screen's month grid legend, week popup,
        // and day detail all key off that same divergent-but-internally-consistent
        // palette) - verified this is NOT a mismatch bug before considering a change.
        listOf(
            Triple(AccentCoral, TablerIcons.ClipboardList, stringResource(R.string.calendar_month_kcal, ms.totalKcal)),
            Triple(Warm,        TablerIcons.Activity,  stringResource(R.string.calendar_month_minutes, ms.activeMinutes)),
            Triple(Teal,        TablerIcons.Droplet,       stringResource(R.string.calendar_month_hydration, ms.hydrationMl)),
            Triple(Gold,        TablerIcons.Calendar,   stringResource(R.string.calendar_month_days, ms.activeDays)),
        ).forEach { (color, icon, label) ->
            // User-instructed: these tiles must use the app's standard card
            // style (ScanEatCard) - was a raw Surface with a manual shadow
            // and CardRadius.CONTROL (wrong token role) instead of the
            // standard CardRadius.CARD + hairline border + no-shadow chrome
            // every other card in the app uses. The per-stat accent tint
            // (color.copy(0.08f)) is kept - ScanEatCard already supports a
            // custom color, this isn't style drift.
            ScanEatCard(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(CardRadius.CARD),
                color = color.copy(0.08f),
                contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = Spacing.S),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.T2),
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(IconSize.Tiny))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center)
            }
        }
    }
}
