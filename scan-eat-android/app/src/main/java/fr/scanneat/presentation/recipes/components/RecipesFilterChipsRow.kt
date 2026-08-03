package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.recipes.RecipesViewModel
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CollapsibleFilterBar
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * Popup menu behind a "Filtres : <current>" button (see CollapsibleFilterBar's
 * own doc comment for why this is a popup, not an inline expandable list).
 */
@Composable
internal fun RecipesFilterChipsRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    goalFilter: RecipesViewModel.GoalFilter,
    onFilterChange: (RecipesViewModel.GoalFilter) -> Unit,
    filtered: Int,
    total: Int,
) {
    val filterOptions = listOf(
        RecipesViewModel.GoalFilter.ALL         to stringResource(R.string.recipes_filter_all),
        RecipesViewModel.GoalFilter.HIGH_PROTEIN to stringResource(R.string.recipes_filter_high_protein),
        RecipesViewModel.GoalFilter.LOW_CARB    to stringResource(R.string.recipes_filter_low_carb),
        RecipesViewModel.GoalFilter.LOW_FAT     to stringResource(R.string.recipes_filter_low_fat),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        CollapsibleFilterBar(
            expanded = expanded, onToggle = onToggle,
            summaryLabel = stringResource(R.string.foodsearch_filters_label, filterOptions.first { it.first == goalFilter }.second),
        ) {
            filterOptions.forEach { (filter, label) ->
                val isSelected = goalFilter == filter
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = { if (isSelected) Icon(Icons.Rounded.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp)) },
                    onClick = { onFilterChange(filter); onToggle() },
                )
            }
        }
        if (goalFilter != RecipesViewModel.GoalFilter.ALL && total > 0) {
            Text(
                stringResource(R.string.recipes_filter_results, filtered, total),
                style = MaterialTheme.typography.labelSmall,
                color = OnBackground.copy(0.5f),
            )
        }
    }
}
