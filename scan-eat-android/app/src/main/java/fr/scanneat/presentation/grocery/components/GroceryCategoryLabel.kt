package fr.scanneat.presentation.grocery.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.GroceryCategory

@Composable
internal fun categoryLabel(category: GroceryCategory): String = stringResource(
    when (category) {
        GroceryCategory.PRODUCE   -> R.string.grocery_category_produce
        GroceryCategory.DAIRY     -> R.string.grocery_category_dairy
        GroceryCategory.MEAT_FISH -> R.string.grocery_category_meat_fish
        GroceryCategory.BAKERY    -> R.string.grocery_category_bakery
        GroceryCategory.FROZEN    -> R.string.grocery_category_frozen
        GroceryCategory.BEVERAGES -> R.string.grocery_category_beverages
        GroceryCategory.PANTRY    -> R.string.grocery_category_pantry
        GroceryCategory.OTHER     -> R.string.grocery_category_other
    },
)
