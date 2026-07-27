package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.recipes.RecipesViewModel
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun RecipesFilterChipsRow(
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            items(filterOptions) { (filter, label) ->
                FilterChip(
                    selected = goalFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
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
