package fr.scanneat.presentation.calendar.components

import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Activity
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Droplet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.calendar.MonthSummary
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.ShadowTint
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
            Surface(
                modifier = Modifier.weight(1f)
                    .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                    .clip(RoundedCornerShape(CardRadius.CONTROL)),
                shape = RoundedCornerShape(CardRadius.CONTROL),
                color = color.copy(0.08f),
                // art-direction-engine §CARDS: matching the small-stat-tile
                // elevation tier already applied to FastingHistorySection/
                // HistoryTopScannedRow - this was a plain flat Surface with
                // no depth cue at all.
                shadowElevation = 0.dp,
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
