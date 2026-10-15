package fr.scanneat.presentation.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.TablerIcons
import compose.icons.tablericons.Barcode
import compose.icons.tablericons.Book
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.Heart
import androidx.compose.material.icons.rounded.Restaurant
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
    // Restructuration: Cuisine and Santé hubs replace the Métabolisme and
    // Réglages tabs - Métabolisme lives in Santé, Réglages behind the gear icon.
    data object Cooking   : TopTab(AppRoutes.COOKING,   R.string.tab_cooking,   androidx.compose.material.icons.Icons.Rounded.Restaurant)
    data object Health    : TopTab(AppRoutes.HEALTH,    R.string.tab_health,    TablerIcons.Heart)
}

val TOP_TABS = listOf(TopTab.Dashboard, TopTab.Diary, TopTab.Scan, TopTab.Cooking, TopTab.Health)

/** Parses UserPreferences.navTabOrder's stored CSV of [TopTab.route]s back into
 *  an ordered list of [TOP_TABS] — falls back to [TOP_TABS]'s own default order
 *  for a blank/first-run value or anything that doesn't cleanly resolve to
 *  every current tab exactly once (a stale value from a future app version
 *  with a different tab set, corruption, etc). */
fun parseTopTabOrder(csv: String): List<TopTab> {
    if (csv.isBlank()) return TOP_TABS
    val byRoute = TOP_TABS.associateBy { it.route }
    val parsed = csv.split(",").mapNotNull { byRoute[it.trim()] }.distinct()
    return if (parsed.size == TOP_TABS.size) parsed else TOP_TABS
}

fun serializeTopTabOrder(tabs: List<TopTab>): String = tabs.joinToString(",") { it.route }

// Tab root routes — bottom nav visible, back arrow hidden
val TAB_ROOT_ROUTES = TOP_TABS.map { it.route }.toSet()
