package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.OtherTrackersSnapshot
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticBlue
import fr.scanneat.presentation.ui.theme.semanticGreen
import kotlin.math.roundToInt

/**
 * Compact glance row for the three trackers Dashboard otherwise never shows
 * (Water/Fasting/Treatment - see DashboardViewModel.otherTrackers). Purely
 * informational, no tap targets: Diary's own tabs already own the full
 * interactive UI for each of these, this card only answers "am I on track
 * today?" without leaving Dashboard.
 */
@Composable
internal fun OtherTrackersCard(snapshot: OtherTrackersSnapshot) {
    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L),
    ) {
        // weight(1f) on each stat, not a bare SpaceBetween over 3 unweighted
        // columns - "Traitement" is a much wider label than "Eau"/"Jeûne", so
        // each column's own natural width (icon vs value vs label, whichever
        // is widest) differed a lot between the three, and SpaceBetween only
        // equalizes the *gaps* between columns, not the columns themselves -
        // the icon centers correctly *within its own column* (TrackerStat's
        // CenterHorizontally), but the columns' centers weren't evenly spaced
        // across the row, so the icons visibly weren't aligned with each other.
        // Equal-width slots make every icon sit at the same relative position.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val hydrationPct = if (snapshot.hydrationGoalMl > 0) (snapshot.hydrationMl * 100 / snapshot.hydrationGoalMl) else 0
            TrackerStat(
                icon = Icons.Default.Opacity,
                tint = semanticBlue(),
                value = stringResource(R.string.dashboard_other_trackers_hydration_value, hydrationPct.coerceAtMost(999)),
                label = stringResource(R.string.dashboard_other_trackers_hydration_label),
                modifier = Modifier.weight(1f),
            )
            val fasting = snapshot.fastingActive
            TrackerStat(
                icon = Icons.Default.Timer,
                tint = semanticAmber(),
                value = if (fasting != null) stringResource(R.string.dashboard_other_trackers_fasting_active_value, fasting.elapsedHours.roundToInt())
                        else stringResource(R.string.dashboard_other_trackers_fasting_idle_value),
                label = stringResource(R.string.dashboard_other_trackers_fasting_label),
                modifier = Modifier.weight(1f),
            )
            TrackerStat(
                icon = Icons.Default.Medication,
                tint = if (snapshot.medsActiveCount == 0 || snapshot.medsTakenCount == snapshot.medsActiveCount) semanticGreen() else semanticAmber(),
                value = stringResource(R.string.dashboard_other_trackers_meds_value, snapshot.medsTakenCount, snapshot.medsActiveCount),
                label = stringResource(R.string.dashboard_other_trackers_meds_label),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackerStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}
