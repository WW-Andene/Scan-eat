package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.health.OverhydrationSeverity
import fr.scanneat.domain.engine.health.OvertrainingSeverity
import fr.scanneat.presentation.activity.components.overtrainingMessage
import fr.scanneat.presentation.dashboard.DashboardSafetyWarning
import fr.scanneat.presentation.medication.components.interactionWarningLabel
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * User-requested "centre de vigilance": every safety caution built this
 * session (activity overtraining, hydration over-consumption, medication
 * drug-drug interactions, medication-food interactions) previously only ever
 * surfaced on its own tab or scan sheet - a user relying on Dashboard as
 * their single daily glance had no way to know any of them applied to today
 * without visiting each tracker individually. One consolidated card, top
 * priority placement (see DashboardScreen - inserted ahead of
 * CalorieBalanceCard), each line reusing the exact same message-building
 * logic its own tab already uses (see [buildMessage]'s own doc comment) so
 * nothing here can drift out of sync with what that tab shows.
 */
@Composable
internal fun SafetyCenterCard(warnings: List<DashboardSafetyWarning>) {
    if (warnings.isEmpty()) return
    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        Text(
            stringResource(R.string.dashboard_safety_center_title, warnings.size),
            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
        )
        warnings.forEach { warning ->
            val (message, tint) = buildMessage(warning)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = tint, modifier = androidx.compose.ui.Modifier.size(IconSize.Small))
                Text(message, style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

/**
 * Every branch calls the exact same message-building function/string
 * resource its own tab already renders (overtrainingMessage - Activity,
 * hydration_overconsumption_* - Hydration, interactionWarningLabel -
 * Médicament) rather than a parallel copy, so a message can never read
 * differently here than where the user would go to act on it.
 */
@Composable
private fun buildMessage(warning: DashboardSafetyWarning): Pair<String, androidx.compose.ui.graphics.Color> = when (warning) {
    is DashboardSafetyWarning.Overtraining ->
        overtrainingMessage(warning.warning) to
            (if (warning.warning.severity == OvertrainingSeverity.HIGH) semanticRed() else semanticAmber())
    is DashboardSafetyWarning.Overhydration ->
        stringResource(
            if (warning.warning.severity == OverhydrationSeverity.HIGH) R.string.hydration_overconsumption_high
            else R.string.hydration_overconsumption_moderate,
            warning.warning.totalMl,
        ) to (if (warning.warning.severity == OverhydrationSeverity.HIGH) semanticRed() else semanticAmber())
    is DashboardSafetyWarning.MedicationInteraction -> interactionWarningLabel(warning.warning) to semanticRed()
    is DashboardSafetyWarning.MedicationFood -> warning.message to semanticAmber()
    is DashboardSafetyWarning.PantryExpiry ->
        stringResource(R.string.dashboard_safety_pantry_expiry, warning.itemNames.size, warning.itemNames.take(3).joinToString(", ")) to semanticAmber()
}
