package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.FoodSearchFilter
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun FiltersSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    filter: FoodSearchFilter,
    onFilterChange: (FoodSearchFilter) -> Unit,
) {
    val filterOptions = listOf(
        FoodSearchFilter.ALL            to stringResource(R.string.foodsearch_filter_all),
        FoodSearchFilter.HIGH_PROTEIN    to stringResource(R.string.foodsearch_filter_protein),
        FoodSearchFilter.LOW_CARB        to stringResource(R.string.foodsearch_filter_low_carb),
        FoodSearchFilter.HIGH_FIBER      to stringResource(R.string.foodsearch_filter_fiber),
        FoodSearchFilter.IRON_SOURCE     to stringResource(R.string.foodsearch_filter_iron),
        FoodSearchFilter.CALCIUM_SOURCE  to stringResource(R.string.foodsearch_filter_calcium),
    )
    // User-reported: this filter pill sat visibly further right than Templates'/
    // Recipes' — CollapsibleFilterBar already applies Spacing.L horizontally
    // itself (see its own doc comment), so passing another Spacing.L here
    // doubled the inset. Kept only the vertical spacing this call site needs.
    CollapsibleFilterBar(
        expanded = expanded, onToggle = onToggle,
        summaryLabel = stringResource(R.string.foodsearch_filters_label, filterOptions.first { it.first == filter }.second),
        modifier = Modifier.padding(vertical = Spacing.XS),
    ) {
        filterOptions.forEach { (f, label) ->
            val isSelected = filter == f
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (isSelected) Icon(TablerIcons.Check, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact)) },
                onClick = { onFilterChange(f); onToggle() },
            )
        }
    }
}
