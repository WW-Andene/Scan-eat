package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.glassSheen

// Shared helper used repeatedly by the orchestrator's feature-tile rows.
@Composable
internal fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
        }
    }
}
