package fr.scanneat.presentation.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HistoryTopScannedRow(topScanned: List<Triple<String, Int, Long>>, onOpenResult: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        topScanned.forEach { (name, count, dbId) ->
            Surface(
                onClick  = { if (dbId > 0) onOpenResult(dbId) },
                modifier = Modifier.weight(1f)
                    .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                shape    = RoundedCornerShape(CardRadius.CONTROL),
                color    = SurfaceVariant.copy(alpha = 0.42f),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.S),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        count.toString(),
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = AccentCoral,
                    )
                    Text(
                        name,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = OnSurface.copy(0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
