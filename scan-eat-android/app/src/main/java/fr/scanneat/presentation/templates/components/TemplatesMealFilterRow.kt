package fr.scanneat.presentation.templates.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*

/**
 * Was a permanently-visible LazyRow - now hidden behind a "Filtres: <current>"
 * toggle, same collapsible pattern Recherche/FoodSearch and Recipes use (see
 * CollapsibleFilterBar).
 */
@Composable
internal fun TemplatesMealFilterRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    selected: MealSlot?,
    onSelect: (MealSlot?) -> Unit,
) {
    val mealOptions = listOf<MealSlot?>(null) + MealSlot.values().toList()
    val allLabel = stringResource(R.string.recipes_filter_all)
    CollapsibleFilterBar(
        expanded = expanded, onToggle = onToggle,
        summaryLabel = stringResource(R.string.foodsearch_filters_label, selected?.label() ?: allLabel),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            items(mealOptions, key = { it?.name ?: "all" }) { slot ->
                FilterChip(
                    selected = selected == slot,
                    onClick = { onSelect(slot) },
                    label = { Text(slot?.label() ?: allLabel) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                )
            }
        }
    }
}
