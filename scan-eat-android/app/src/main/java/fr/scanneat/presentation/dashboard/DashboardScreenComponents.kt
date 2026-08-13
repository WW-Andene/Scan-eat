package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.ScanEatCard

// Shared helper used repeatedly by the orchestrator's feature-tile rows.
// User-reported: this used to be its own hand-rolled Surface+glassSheen+
// shadow construction, independent from (and still carrying the same
// two-layer-mismatch bug class as) ScanEatCard's own chrome - "les tiles de
// fonctionnalité n'utilisent pas le card style, c'est encore l'ancien."
// Delegates to ScanEatCard directly now, so this card and every other one in
// the app share the exact same (now-fixed) construction and Prism fill.
@Composable
internal fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ScanEatCard(
        modifier = modifier,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(Spacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        onClick = onClick,
    ) {
        Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(26.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
    }
}
