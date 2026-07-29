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
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HydrationSuggestedGoalBanner(suggestedGoalMl: Int) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = semanticBlue().copy(0.1f),
        modifier = Modifier.fillMaxWidth(),
        // design-aesthetic-audit §DH: had no shadowElevation at all.
        shadowElevation = 3.dp,
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
            )
        }
    }
}
