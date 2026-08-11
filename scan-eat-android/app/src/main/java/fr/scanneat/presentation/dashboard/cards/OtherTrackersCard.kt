package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Droplet
import compose.icons.tablericons.Pill
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.OtherTrackersSnapshot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticBlue
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import kotlin.math.roundToInt

/**
 * Compact glance row for the three trackers Dashboard otherwise never shows
 * (Water/Fasting/Treatment - see DashboardViewModel.otherTrackers).
 *
 * Restructuration audit (§XI): each stat is now tappable, deep-linking
 * straight to its Diary sub-tab - same "diary_selected_tab" SavedStateHandle
 * mechanism ExpensesRecapCard already uses (see AppNavGraph.kt's
 * onOpenDiaryTab). Previously "am I on track today?" was answerable without
 * leaving Dashboard but doing anything about it required opening Diary and
 * manually switching to the right sub-tab yourself - the one glance row on
 * Dashboard with no tap-through, unlike every other card here.
 */
@Composable
internal fun OtherTrackersCard(
    snapshot: OtherTrackersSnapshot,
    onOpenHydration: () -> Unit = {},
    onOpenFasting: () -> Unit = {},
    onOpenMedication: () -> Unit = {},
) {
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
            // User-requested: does the Dashboard also flag a real water surplus
            // (e.g. 10L/day), not just Hydration's own tab? Same >=2x-goal
            // relative threshold checkOverhydration() uses, mirrored here rather
            // than threaded as a new OtherTrackersSnapshot field, since this glance
            // tile only needs the tint, not the full warning banner/message
            // (shown on the Hydration tab this tile links to).
            val isOverhydrated = snapshot.hydrationGoalMl > 0 && snapshot.hydrationMl >= snapshot.hydrationGoalMl * 2
            TrackerStat(
                icon = TablerIcons.Droplet,
                tint = if (isOverhydrated) semanticRed() else semanticBlue(),
                value = stringResource(R.string.dashboard_other_trackers_hydration_value, hydrationPct.coerceAtMost(999)),
                label = stringResource(R.string.dashboard_other_trackers_hydration_label),
                modifier = Modifier.weight(1f).clickable(onClick = onOpenHydration),
            )
            val fasting = snapshot.fastingActive
            TrackerStat(
                icon = TablerIcons.Clock,
                // app-audit §E3: Hydration's icon above correctly uses semanticBlue()
                // (its real identity color everywhere else) and Medication's is a real
                // status indicator (green/amber by compliance) - this was a static
                // semanticAmber() with no status meaning, mismatched against Fasting's
                // actual identity color (AccentCoral, used throughout ActiveFastCard/
                // StartFastForm).
                tint = AccentCoral,
                value = if (fasting != null) stringResource(R.string.dashboard_other_trackers_fasting_active_value, fasting.elapsedHours.roundToInt())
                        else stringResource(R.string.dashboard_other_trackers_fasting_idle_value),
                label = stringResource(R.string.dashboard_other_trackers_fasting_label),
                modifier = Modifier.weight(1f).clickable(onClick = onOpenFasting),
            )
            TrackerStat(
                icon = TablerIcons.Pill,
                tint = if (snapshot.medsActiveCount == 0 || snapshot.medsTakenCount == snapshot.medsActiveCount) semanticGreen() else semanticAmber(),
                value = stringResource(R.string.dashboard_other_trackers_meds_value, snapshot.medsTakenCount, snapshot.medsActiveCount),
                label = stringResource(R.string.dashboard_other_trackers_meds_label),
                modifier = Modifier.weight(1f).clickable(onClick = onOpenMedication),
            )
        }
        // Activity has never had its own streak surfaced anywhere in the app,
        // unlike diary-logging (CalorieBalanceCard) - same logStreakDays engine,
        // just fed workout dates. A separate line rather than a 4th column so
        // the existing 3-column row's equal-weight alignment isn't disturbed.
        if (snapshot.workoutStreak > 0) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.SM))
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = OnSurface.copy(0.07f))
            androidx.compose.foundation.layout.Spacer(Modifier.size(Spacing.SM))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Tiny))
                Text(
                    pluralStringResource(R.plurals.dashboard_workout_streak, snapshot.workoutStreak, snapshot.workoutStreak),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(0.6f),
                )
            }
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
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(IconSize.Compact))
        Text(value, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}
