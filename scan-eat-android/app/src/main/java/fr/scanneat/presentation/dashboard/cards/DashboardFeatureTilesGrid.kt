package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.History
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Star
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.FeatureTile
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * Dashboard's 4-row meal-planning-tool tile grid — extracted verbatim
 * (§T1 composition-root split) from DashboardScreen's body. Daily logging
 * tasks (weight, fasting, water, activity) live in Journal now, not here.
 */
@Composable
fun DashboardFeatureTilesGrid(
    onOpenRecipes: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenMealPlan: () -> Unit,
    onOpenGrocery: () -> Unit,
    onOpenCustomFoods: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFoodSearch: () -> Unit,
    onOpenSeasonalProduce: () -> Unit,
    onOpenPantry: () -> Unit,
) {
    // Column, not bare sibling Rows - a LazyColumn `item {}` slot has no implicit
    // vertical-stack layout of its own (unlike the LazyColumn itself), so multiple
    // top-level Rows here would overlap without an explicit container. Matches the
    // LazyColumn's own inter-item spacing (Spacing.M) so this single-item grid looks
    // identical to when each row was its own separate item.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FeatureTile(TablerIcons.ClipboardList, stringResource(R.string.dashboard_tile_recipes), Modifier.weight(1f), onClick = onOpenRecipes)
        FeatureTile(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.dashboard_tile_templates), Modifier.weight(1f), onClick = onOpenTemplates)
        FeatureTile(TablerIcons.Calendar, stringResource(R.string.dashboard_tile_mealplan), Modifier.weight(1f), onClick = onOpenMealPlan)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FeatureTile(TablerIcons.ShoppingCart, stringResource(R.string.dashboard_tile_grocery), Modifier.weight(1f), onClick = onOpenGrocery)
        // onOpenCustomFoods had no call site anywhere in the composable -
        // CustomFoodScreen was completely unreachable from any UI gesture.
        FeatureTile(Icons.Rounded.Fastfood, stringResource(R.string.dashboard_tile_customfoods), Modifier.weight(1f), onClick = onOpenCustomFoods)
        FeatureTile(TablerIcons.Star, stringResource(R.string.dashboard_tile_favorites), Modifier.weight(1f), onClick = onOpenFavorites)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        // Previously no single place showed everything logged on a given
        // day - Diary/Weight/Activity/Hydration each embedded their own
        // siloed single-domain mini-calendar with no cross-tracker view.
        // Was sharing TablerIcons.Calendar with Meal Plan's tile above -
        // same icon, two different destinations in the same grid, so users
        // couldn't tell them apart at a glance.
        FeatureTile(Icons.Rounded.EventNote, stringResource(R.string.dashboard_tile_calendar), Modifier.weight(1f), onClick = onOpenCalendar)
        // A UI/UX audit found ScanHistoryScreen (search/sort/favorite/
        // delete) was reachable ONLY via the "View all" link below, itself
        // gated on recentScans.isNotEmpty() - a brand-new user with zero
        // scans had no way to open it at all. This tile is unconditional.
        FeatureTile(TablerIcons.History, stringResource(R.string.dashboard_tile_history), Modifier.weight(1f), onClick = onOpenHistory)
        // Previously an unused spacer slot - FOOD_DB's ~130 curated foods
        // (plus the user's own custom foods) were only ever reachable
        // through a 6-10-result Quick Add autocomplete dropdown, never as
        // a real browsable/filterable search tool in its own right.
        FeatureTile(TablerIcons.Search, stringResource(R.string.dashboard_tile_search), Modifier.weight(1f), onClick = onOpenFoodSearch)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        FeatureTile(Icons.Rounded.Eco, stringResource(R.string.dashboard_tile_seasonal), Modifier.weight(1f), onClick = onOpenSeasonalProduce)
        // User-requested: a real persisted pantry inventory - see
        // PantryScreen's own doc comment. Was an empty weighted spacer slot.
        FeatureTile(Icons.Rounded.Kitchen, stringResource(R.string.dashboard_tile_pantry), Modifier.weight(1f), onClick = onOpenPantry)
        // One remaining empty weighted slot keeps this tile the same size as
        // every other 3-per-row tile above instead of stretching to full width.
        Spacer(Modifier.weight(1f))
    }
    }
}
