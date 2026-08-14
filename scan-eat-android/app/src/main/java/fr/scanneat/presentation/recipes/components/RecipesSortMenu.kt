package fr.scanneat.presentation.recipes.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.recipes.RecipesViewModel
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatDropdownMenu
import fr.scanneat.presentation.ui.theme.rememberTrackedWidth

/**
 * User-requested: "develop the tool" for Recipes - same shape as History's
 * own HistorySortMenu, letting the list be ordered beyond the implicit
 * favorites-first/most-recent default (e.g. "which of my recipes has the
 * most protein?" previously meant scanning the whole list by eye).
 */
@Composable
internal fun RecipesSortMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    currentSort: RecipesViewModel.RecipeSort,
    onSortChange: (RecipesViewModel.RecipeSort) -> Unit,
) {
    val (anchorWidth, widthTracker) = rememberTrackedWidth()
    Box {
        IconButton(onClick = { onExpandedChange(true) }, modifier = widthTracker) {
            Icon(Icons.Rounded.Sort, stringResource(R.string.recipes_sort_cd), tint = OnBackground)
        }
        ScanEatDropdownMenu(
            expanded = expanded, onDismissRequest = { onExpandedChange(false) }, anchorWidth = anchorWidth,
        ) {
            val options = listOf(
                RecipesViewModel.RecipeSort.RECENT to stringResource(R.string.recipes_sort_recent),
                RecipesViewModel.RecipeSort.NAME_AZ to stringResource(R.string.recipes_sort_name),
                RecipesViewModel.RecipeSort.PROTEIN_DENSITY_DESC to stringResource(R.string.recipes_sort_protein),
                RecipesViewModel.RecipeSort.KCAL_ASC to stringResource(R.string.recipes_sort_kcal),
            )
            options.forEach { (value, label) ->
                val isSelected = currentSort == value
                DropdownMenuItem(
                    text = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = {
                        if (isSelected) Icon(TablerIcons.Check, contentDescription = null, tint = AccentCoral)
                    },
                    modifier = Modifier.semantics { selected = isSelected; role = Role.RadioButton },
                    onClick = { onSortChange(value); onExpandedChange(false) },
                )
            }
        }
    }
}
