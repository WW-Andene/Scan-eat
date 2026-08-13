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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.LocalThemeName
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.PrismFillColor
import fr.scanneat.presentation.ui.theme.glassSheen

// Shared helper used repeatedly by the orchestrator's feature-tile rows.
@Composable
internal fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // User-reported "rectangle" bug: untinted shadowElevation + no forced .clip(),
    // same fix as ScanEatCard/CalorieBalanceCard.
    val tileShape = RoundedCornerShape(CardRadius.CONTROL)
    // User-reported: "pourquoi les carte ne sont pas transparentes" (Prism
    // theme) - see ScanEatCard.kt's own doc comment on the same fix.
    val isPrism = LocalThemeName.current == "prism"
    Surface(
        onClick = onClick,
        modifier = modifier
            .glassSheen(edgeAlpha = 0.16f, shape = tileShape, glowAlpha = 0.06f)
            .shadow(elevation = 3.dp, shape = tileShape)
            .clip(tileShape),
        shape = tileShape,
        // Aligned with ScanEatCard's own shared Prism fill (see PrismFillColor's doc comment)
        // - user-requested: same card style everywhere, not a separately-tuned fill here.
        color = if (isPrism) PrismFillColor else SurfaceVariant.copy(alpha = StandardCardAlpha),
        shadowElevation = 0.dp,
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
