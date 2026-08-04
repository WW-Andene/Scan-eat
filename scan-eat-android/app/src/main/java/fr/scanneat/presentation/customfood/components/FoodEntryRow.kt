package fr.scanneat.presentation.customfood.components

import compose.icons.tablericons.Edit
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.result.HintIconButton
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.ChipBackgroundAccent
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.STATUS_BORDER_ALPHA

@Composable
internal fun FoodEntryRow(entry: FoodEntry, isCustom: Boolean, hints: ProductHints, onDelete: () -> Unit, onEdit: () -> Unit) {
    // Was a hand-rolled Row+background+clip - the one list row in this app
    // not built on ScanEatCard, reading flatter/duller next to every sibling
    // row (DiaryEntryCard, RecipeCard, GroceryItemRow, ...) that gets the
    // shared card's glassSheen/hierarchy treatment.
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (isCustom) {
                    Surface(
                        shape = RoundedCornerShape(CardRadius.BADGE),
                        color = ChipBackgroundAccent,
                    ) {
                        Text(
                            stringResource(R.string.customfood_custom_badge),
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(
                    stringResource(R.string.customfood_macro_summary, entry.kcal.toInt(), entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface.copy(0.55f),
                )
                // Nutrient density score: protein (4 kcal/g) + fiber (2 kcal/g) vs total kcal.
                // Surfaces how "nutrient-dense" vs calorie-dense a food is at a glance.
                if (entry.kcal > 0) {
                    val score = ((entry.proteinG * 4 + entry.fiberG * 2) / entry.kcal * 100).toInt().coerceIn(0, 100)
                    val (scoreColor, scoreLabel) = when {
                        score >= 40 -> semanticGreen() to "D+$score"
                        score >= 20 -> Gold to "D$score"
                        else        -> semanticRed() to "D$score"
                    }
                    Surface(shape = RoundedCornerShape(CardRadius.BADGE), color = scoreColor.copy(0.15f), border = BorderStroke(1.dp, scoreColor.copy(alpha = STATUS_BORDER_ALPHA))) {
                        Text(scoreLabel, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = scoreColor)
                    }
                }
            }
        }
        // The "💡 Bon à savoir" hint panel was previously reachable only from a
        // scanned product's Result screen - CustomFoodRepository.toProduct() already
        // works on any FoodEntry (custom or a plain FOOD_DB search hit), so both get
        // the same benefits/risks/facts. Sized to 16dp to match this row's existing
        // compact Edit/Close icons instead of the panel's default 24dp.
        HintIconButton(hints = hints, iconSize = 16.dp)
        if (isCustom) {
            // Left at IconButton's default 48dp touch target (Material/WCAG minimum) -
            // a UI/UX audit found this row forcing both controls to 32dp.
            IconButton(onClick = onEdit) {
                Icon(
                    TablerIcons.Edit, stringResource(R.string.common_edit),
                    tint = OnSurface.copy(0.5f),
                    modifier = Modifier.size(IconSize.Small),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    TablerIcons.X, stringResource(R.string.common_delete),
                    tint = OnSurface.copy(0.5f),
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
    }
}
