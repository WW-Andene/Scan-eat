package fr.scanneat.presentation.hydration.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import fr.scanneat.R
import fr.scanneat.domain.engine.health.OverhydrationSeverity
import fr.scanneat.domain.engine.health.OverhydrationWarning
import fr.scanneat.presentation.ui.theme.*

/**
 * User-requested: is a real water surplus (e.g. 10L/day) caught and flagged?
 * Previously no such check existed at all - see
 * HydrationViewModel.overhydrationWarning's own doc comment. Same
 * red-tinted, bordered, elevated treatment MedicationInteractionWarningBanner
 * uses for a safety-relevant caution, not the informational-blue
 * HydrationSuggestedGoalBanner styling above it on this same screen.
 */
@Composable
internal fun HydrationOverconsumptionBanner(warning: OverhydrationWarning) {
    val messageRes = if (warning.severity == OverhydrationSeverity.HIGH) R.string.hydration_overconsumption_high
        else R.string.hydration_overconsumption_moderate
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(0.1f),
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        shadowElevation = 0.dp,
        border = BorderStroke(2.dp, semanticRed().copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.AlertTriangle, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Compact))
            Column {
                Text(stringResource(R.string.hydration_overconsumption_title), style = MaterialTheme.typography.labelMedium, color = semanticRed(), fontWeight = FontWeight.Bold)
                Text(stringResource(messageRes, warning.totalMl), style = MaterialTheme.typography.bodySmall, color = semanticRed().copy(0.8f))
            }
        }
    }
}
