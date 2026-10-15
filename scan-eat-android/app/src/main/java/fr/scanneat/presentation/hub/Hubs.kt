package fr.scanneat.presentation.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import compose.icons.TablerIcons
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Book
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.FileInvoice
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Pill
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Star
import fr.scanneat.R
import fr.scanneat.presentation.shell.AppRoutes

/** Cuisine: plan, shop, and the user's own foods. */
@Composable
fun CookingHub(navigate: (String) -> Unit, openDiaryTab: (String) -> Unit, onOpenSettings: () -> Unit) = HubScreen(
    title = R.string.tab_cooking,
    onOpenSettings = onOpenSettings,
    groups = listOf(
        HubGroup(R.string.hub_group_plan, listOf(
            HubEntry(TablerIcons.Calendar, R.string.dashboard_tile_mealplan, R.string.hub_mealplan_sub) { navigate(AppRoutes.MEAL_PLAN) },
            HubEntry(TablerIcons.ClipboardList, R.string.dashboard_tile_recipes, R.string.hub_recipes_sub) { navigate(AppRoutes.RECIPES) },
            HubEntry(Icons.AutoMirrored.Filled.ListAlt, R.string.dashboard_tile_templates, R.string.hub_templates_sub) { navigate(AppRoutes.TEMPLATES) },
        )),
        HubGroup(R.string.hub_group_shop, listOf(
            HubEntry(TablerIcons.ShoppingCart, R.string.dashboard_tile_grocery, R.string.hub_grocery_sub) { navigate(AppRoutes.GROCERY) },
            HubEntry(Icons.Rounded.Kitchen, R.string.dashboard_tile_pantry, R.string.hub_pantry_sub) { navigate(AppRoutes.PANTRY) },
            HubEntry(TablerIcons.FileInvoice, R.string.diary_tab_expenses, R.string.hub_expenses_sub) { openDiaryTab("EXPENSES") },
        )),
        HubGroup(R.string.hub_group_foods, listOf(
            HubEntry(TablerIcons.Search, R.string.dashboard_tile_search, R.string.hub_search_sub) { navigate(AppRoutes.FOOD_SEARCH) },
            HubEntry(Icons.Rounded.Fastfood, R.string.dashboard_tile_customfoods, R.string.hub_customfoods_sub) { navigate(AppRoutes.CUSTOM_FOODS) },
            HubEntry(TablerIcons.Star, R.string.dashboard_tile_favorites, R.string.hub_favorites_sub) { navigate(AppRoutes.FAVORITES) },
            HubEntry(Icons.Rounded.Eco, R.string.dashboard_tile_seasonal, R.string.hub_seasonal_sub) { navigate(AppRoutes.SEASONAL_PRODUCE) },
        )),
    ),
)

/** Santé: treatments, symptoms, safety, and the metabolism module. */
@Composable
fun HealthHub(navigate: (String) -> Unit, openDiaryTab: (String) -> Unit, onOpenSettings: () -> Unit) = HubScreen(
    title = R.string.tab_health,
    onOpenSettings = onOpenSettings,
    groups = listOf(
        HubGroup(R.string.hub_group_followup, listOf(
            HubEntry(TablerIcons.Pill, R.string.diary_tab_treatment, R.string.hub_treatment_sub) { openDiaryTab("TREATMENT") },
            HubEntry(TablerIcons.Heart, R.string.dashboard_tile_symptoms, R.string.hub_symptoms_sub) { navigate(AppRoutes.SYMPTOMS) },
            HubEntry(Icons.Rounded.SmokeFree, R.string.dashboard_tile_withdrawal, R.string.hub_withdrawal_sub) { navigate(AppRoutes.WITHDRAWAL) },
            HubEntry(Icons.Rounded.MonitorHeart, R.string.tab_biolism, R.string.hub_biolism_sub) { navigate(AppRoutes.BIOLISM) },
        )),
        HubGroup(R.string.hub_group_safety, listOf(
            HubEntry(TablerIcons.ShieldCheck, R.string.my_safety_title, R.string.hub_safety_sub) { navigate(AppRoutes.MY_SAFETY) },
            HubEntry(Icons.Rounded.Warning, R.string.reactions_title, R.string.hub_reactions_sub) { navigate(AppRoutes.REACTIONS) },
            HubEntry(TablerIcons.Bell, R.string.recalls_title, R.string.hub_recalls_sub) { navigate(AppRoutes.RECALLS) },
        )),
        HubGroup(R.string.hub_group_info, listOf(
            HubEntry(TablerIcons.Book, R.string.sources_title, R.string.hub_sources_sub) { navigate(AppRoutes.SOURCES) },
        )),
    ),
)
