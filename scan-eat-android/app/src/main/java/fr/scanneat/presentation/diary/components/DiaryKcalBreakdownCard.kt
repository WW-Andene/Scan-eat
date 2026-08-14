package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticBlue
import kotlin.math.roundToInt


@Composable
internal fun DiaryKcalBreakdownCard(totalKcal: Double, bySlot: Map<MealSlot, List<DiaryEntry>>) {
    val total = totalKcal.coerceAtLeast(1.0)
    val slotColors = mapOf(
        MealSlot.BREAKFAST to AccentCoral.copy(0.7f),
        MealSlot.LUNCH     to Gold.copy(0.8f),
        MealSlot.SNACK     to semanticAmber().copy(0.6f),
        MealSlot.DINNER    to semanticBlue().copy(0.6f),
    )
    ScanEatCard(
        emphasis = CardEmphasis.SECONDARY,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(stringResource(R.string.diary_kcal_breakdown_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            MealSlot.values().forEach { slot ->
                val slotKcal = bySlot[slot]?.sumOf { it.consumed.energyKcal } ?: 0.0
                val frac = (slotKcal / total).toFloat().coerceAtLeast(0f)
                if (frac > 0f) {
                    Box(Modifier.weight(frac).fillMaxHeight().background(slotColors[slot] ?: OnSurface.copy(0.3f)))
                }
            }
            val loggedFrac = MealSlot.values().sumOf { slot -> bySlot[slot]?.sumOf { it.consumed.energyKcal } ?: 0.0 } / total
            if (loggedFrac < 0.99f) {
                Box(Modifier.weight((1f - loggedFrac.toFloat()).coerceAtLeast(0f)).fillMaxHeight().background(OnSurface.copy(0.08f)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            MealSlot.values().forEach { slot ->
                val slotKcal = (bySlot[slot]?.sumOf { it.consumed.energyKcal } ?: 0.0)
                if (slotKcal > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(6.dp).background(slotColors[slot] ?: OnSurface.copy(0.3f), RoundedCornerShape(3.dp)))
                        Text(
                            "${slot.shortLabel()} ${slotKcal.roundToInt()}kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurface.copy(0.55f),
                        )
                    }
                }
            }
        }
    }
}
