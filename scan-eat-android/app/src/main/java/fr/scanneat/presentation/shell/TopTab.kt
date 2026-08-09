package fr.scanneat.presentation.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.Barcode
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Settings
import fr.scanneat.R

// F32 (docs/design-audit-step11-icons-illustration-dataviz-copy.md): pilot
// batch of the icon-set migration — Material Rounded → Tabler Icons. Bottom
// nav is the single most visible icon surface in the app, and a small,
// individually-verified batch (each name confirmed against Tabler's own
// generated icon list before use, not guessed from Material's naming) rather
// than all ~250 usages at once. Barcode stands in for the Scan tab (Tabler
// has no direct "QrCode"/"Scan" icon); Heart stands in for Biolism (Material's
// MonitorHeart — a heart with a pulse line — has no exact Tabler equivalent;
// a plain heart still reads as "vital signs" in context). Remaining ~245
// Material icon usages across the rest of the app are unchanged — tracked as
// incremental follow-up batches, same rollout pattern already used for
// CollapsibleFilterBar/ScanEatDivider elsewhere in this codebase.
sealed class TopTab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Scan      : TopTab(AppRoutes.SCAN,      R.string.tab_scan,      TablerIcons.Barcode)
    data object Diary     : TopTab(AppRoutes.DIARY,     R.string.tab_diary,     TablerIcons.Book)
    data object Dashboard : TopTab(AppRoutes.DASHBOARD, R.string.tab_dashboard, TablerIcons.ChartBar)
    data object Biolism   : TopTab(AppRoutes.BIOLISM,   R.string.tab_biolism,   TablerIcons.Heart)
    data object Settings  : TopTab(AppRoutes.SETTINGS,  R.string.tab_settings,  TablerIcons.Settings)
}

val TOP_TABS = listOf(TopTab.Dashboard, TopTab.Diary, TopTab.Scan, TopTab.Biolism, TopTab.Settings)

// Tab root routes — bottom nav visible, back arrow hidden
val TAB_ROOT_ROUTES = TOP_TABS.map { it.route }.toSet()

// Routes where the entire bottom nav is hidden (full-screen modals). Every
// full-screen nested route pushed on top of a tab (see AppNavGraph's
// composable(AppRoutes.X) calls) belongs here - missing one means the tab bar
// stays visible underneath that screen's own TopAppBar back arrow, showing
// two navigation affordances for the same "go back" action at once.
val HIDDEN_NAV_ROUTES = setOf(
    AppRoutes.ONBOARDING, AppRoutes.RESULT, AppRoutes.SCAN_PROFILE,
    AppRoutes.RECIPES, AppRoutes.TEMPLATES, AppRoutes.MEAL_PLAN,
    AppRoutes.GROCERY, AppRoutes.CUSTOM_FOODS, AppRoutes.SCAN_HISTORY,
    // Was missing — Favorites is its own separately-pushed route (AppNavGraph),
    // not a tab root, so the bottom nav stayed visible underneath its own
    // TopAppBar back arrow, showing two "go back" affordances at once.
    AppRoutes.FAVORITES,
    // Same gap, reintroduced for Calendar — it's also a full-screen nested route
    // with its own TopAppBar back arrow (reachable from Dashboard/Diary).
    AppRoutes.CALENDAR,
    // Same gap again for Reminders (reachable from Settings) — RemindersScreen
    // renders its own FloatingScreenScaffold with a back arrow exactly like
    // every route above it, so leaving it out left the bottom nav visible
    // underneath that back arrow too.
    AppRoutes.REMINDERS,
    // Same gap again for Food Search (reachable from Dashboard) — same
    // own-Scaffold-with-back-arrow pattern as every route above it.
    AppRoutes.FOOD_SEARCH,
)
