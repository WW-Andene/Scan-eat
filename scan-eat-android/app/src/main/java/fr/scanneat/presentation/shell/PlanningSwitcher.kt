package fr.scanneat.presentation.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground

/**
 * Recipes, Meal Templates, Meal Plan, Grocery, and Custom Foods constantly
 * feed into each other (a meal plan is built from recipes/templates and
 * generates a grocery list) but every one of their screens previously took
 * only `onBack` - the sole way to move between any two of them was back out
 * to Dashboard, then forward into the next one. Same icon/label as the
 * existing Dashboard tiles (dashboard_tile_*) for visual consistency between
 * the two entry points to the same destination.
 */
enum class PlanningDestination(val labelRes: Int, val icon: ImageVector) {
    RECIPES(R.string.dashboard_tile_recipes, Icons.Default.RestaurantMenu),
    TEMPLATES(R.string.dashboard_tile_templates, Icons.AutoMirrored.Filled.ListAlt),
    MEAL_PLAN(R.string.dashboard_tile_mealplan, Icons.Default.CalendarMonth),
    GROCERY(R.string.dashboard_tile_grocery, Icons.Default.ShoppingCart),
    CUSTOM_FOODS(R.string.dashboard_tile_customfoods, Icons.Default.Fastfood),
}

/** Dropdown of the other four planning destinations - `current` is excluded so a screen never offers to navigate to itself. */
@Composable
fun PlanningSwitcherMenu(current: PlanningDestination, onNavigate: (PlanningDestination) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.SwapHoriz, stringResource(R.string.planning_switcher_cd), tint = OnBackground)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PlanningDestination.entries.filter { it != current }.forEach { dest ->
            DropdownMenuItem(
                text = { Text(stringResource(dest.labelRes)) },
                leadingIcon = { Icon(dest.icon, null) },
                onClick = { expanded = false; onNavigate(dest) },
            )
        }
    }
}
