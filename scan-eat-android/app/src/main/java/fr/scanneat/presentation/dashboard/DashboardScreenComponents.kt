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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.NotebookPaper
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.notebookPenBorder
import fr.scanneat.presentation.ui.theme.rememberNotebookPostItStyle
import kotlin.random.Random

// Shared helper used repeatedly by the orchestrator's feature-tile rows.
@Composable
internal fun FeatureTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // User-reported "rectangle" bug: untinted shadowElevation + no forced .clip(),
    // same fix as ScanEatCard/CalorieBalanceCard.
    // Notebook theme: sketched pen border, paper-toned interior, no
    // rotation - matches ScanEatCard's own current Notebook treatment (see
    // its doc comment on why rotation was dropped: it caused adjacent
    // cards to visually touch/overlap).
    val postIt = rememberNotebookPostItStyle(RoundedCornerShape(CardRadius.CONTROL))
    val tileShape = postIt?.shape ?: RoundedCornerShape(CardRadius.CONTROL)
    val sketchSeed = if (postIt != null) remember { Random.nextInt() } else 0
    Surface(
        onClick = onClick,
        modifier = modifier
            .glassSheen(edgeAlpha = if (postIt != null) 0f else 0.16f, shape = tileShape, glowAlpha = if (postIt != null) 0f else 0.06f)
            .shadow(elevation = if (postIt != null) 1.dp else 3.dp, shape = tileShape)
            .clip(tileShape)
            .then(if (postIt != null) Modifier.notebookPenBorder(postIt.color, sketchSeed) else Modifier),
        shape = tileShape,
        // Aligned with ScanEatCard's own lighter/more-transparent fill (see its doc comment).
        color = if (postIt != null) NotebookPaper.copy(alpha = 0.4f) else SurfaceVariant.copy(alpha = StandardCardAlpha),
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
