package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardEmphasis
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import kotlin.math.roundToInt

@Composable
internal fun DiaryProteinPerSlotCard(bySlot: Map<MealSlot, List<DiaryEntry>>) {
    val maxSlotProt = MealSlot.values().maxOfOrNull { slot ->
        bySlot[slot]?.sumOf { it.consumed.proteinG } ?: 0.0
    }?.coerceAtLeast(1.0) ?: 1.0
    ScanEatCard(
        emphasis = CardEmphasis.SECONDARY,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(stringResource(R.string.diary_protein_per_slot_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        MealSlot.values().forEach { slot ->
            val prot = bySlot[slot]?.sumOf { it.consumed.proteinG } ?: 0.0
            if (prot > 0.0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(slot.shortLabel(), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f), modifier = Modifier.width(36.dp))
                    LinearProgressIndicator(
                        progress = { (prot / maxSlotProt).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = AccentCoral,
                        trackColor = OnSurface.copy(0.08f),
                    )
                    Text("${prot.roundToInt()}g", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.55f), modifier = Modifier.width(30.dp))
                }
            }
        }
    }
}
