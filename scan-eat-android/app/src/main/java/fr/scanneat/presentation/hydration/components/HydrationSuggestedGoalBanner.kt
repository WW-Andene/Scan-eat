package fr.scanneat.presentation.hydration.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HydrationSuggestedGoalBanner(suggestedGoalMl: Int, onApply: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = semanticBlue().copy(0.1f),
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        // design-aesthetic-audit §DH: had no shadowElevation at all.
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Icon(Icons.Rounded.TipsAndUpdates, null, tint = semanticBlue(), modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.hydration_suggested_goal_hint, suggestedGoalMl),
                style = MaterialTheme.typography.bodySmall,
                color = semanticBlue(),
                modifier = Modifier.weight(1f),
            )
            // Was purely informational - the user had to remember this number, open
            // the separate goal-editor dialog, and retype it themselves. One tap
            // closes the loop on the app's own recommendation.
            TextButton(onClick = { onApply(suggestedGoalMl) }) {
                Text(stringResource(R.string.hydration_suggested_goal_apply), style = MaterialTheme.typography.labelSmall, color = semanticBlue())
            }
        }
    }
}
