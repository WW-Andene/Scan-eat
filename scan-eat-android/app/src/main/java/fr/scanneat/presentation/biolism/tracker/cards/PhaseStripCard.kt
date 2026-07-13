package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.KetoPhaseInfo
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun PhaseStrip(phase: KetoPhaseInfo, ketoHours: Double, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(0.08f)),
    ) {
        // Progress underline
        Box(
            modifier = Modifier
                .fillMaxWidth(phase.progressPct.toFloat() / 100f)
                .height(2.dp)
                .align(Alignment.BottomStart)
                .background(color.copy(0.7f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.weight(1f)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                Text(phase.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                Text(phase.description, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(String.format(Locale.US, "%.1f h", ketoHours), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                Text("${phase.progressPct.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            }
        }
    }
}
