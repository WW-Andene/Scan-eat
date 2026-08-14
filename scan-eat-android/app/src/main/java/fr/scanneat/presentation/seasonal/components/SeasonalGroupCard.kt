package fr.scanneat.presentation.seasonal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import java.text.Collator
import java.util.Locale
import fr.scanneat.presentation.ui.theme.OnBackgroundMuted

/** Extracted from SeasonalProduceScreen (§T1 composition-root split). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeasonalGroupCard(
    titleRes: Int,
    items: List<SeasonalProduce>,
    isFrench: Boolean,
    selectedProduct: SeasonalProduce?,
    onProductClick: (SeasonalProduce) -> Unit,
) {
    // Was in SeasonalProduceDb's own declaration order (roughly grouped by
    // month) - fine for a handful of items, but scanning ~15-20 chips per
    // group to find one specific product meant reading the whole row instead
    // of jumping straight to it. Collator (not a plain String sort) so
    // accented French names ("Épinard", "Étoile" if ever added) sort next to
    // their unaccented neighbors the way a French speaker expects, rather
    // than a raw Unicode ordering pushing every accented letter to the end.
    val collator = remember(isFrench) { Collator.getInstance(if (isFrench) Locale.FRENCH else Locale.ENGLISH) }
    val sortedItems = remember(items, isFrench, collator) {
        items.sortedWith(compareBy(collator) { if (isFrench) it.nameFr else it.nameEn })
    }
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            sortedItems.forEach { produce ->
                val selected = produce == selectedProduct
                Text(
                    if (isFrench) produce.nameFr else produce.nameEn,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) AccentCoral else OnBackground,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CardRadius.BADGE))
                        .background(if (selected) AccentCoral.copy(alpha = 0.15f) else SurfaceVariant.copy(alpha = 0.6f))
                        .clickable { onProductClick(produce) }
                        .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                )
            }
        }
        if (selectedProduct != null && selectedProduct in items) {
            Text(
                stringResource(R.string.seasonal_pairings_hint),
                style = MaterialTheme.typography.labelSmall,
                color = OnBackgroundMuted,
            )
        }
    }
}
