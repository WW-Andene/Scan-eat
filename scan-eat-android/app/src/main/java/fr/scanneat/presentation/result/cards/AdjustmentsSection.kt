package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.PersonalAdjustment
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun AdjustmentsSection(adjustments: List<PersonalAdjustment>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.result_adjustments_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        adjustments.filter { !it.veto }.forEach { adj ->
            val color = when {
                adj.points > 0 -> semanticGreen()
                adj.points < 0 -> semanticRed()
                else           -> OnBackground.copy(0.5f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    if (adj.points > 0) "+${adj.points.toInt()}" else "${adj.points.toInt()}",
                    style = MaterialTheme.typography.labelMedium, color = color,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                )
                Text(adj.reason, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f),
                    modifier = Modifier.weight(1f))
            }
        }
    }
}
