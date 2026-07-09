package fr.scanneat.presentation.shell

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import fr.scanneat.R

sealed class TopTab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Scan      : TopTab(AppRoutes.SCAN,      R.string.tab_scan,      Icons.Default.QrCodeScanner)
    data object Diary     : TopTab(AppRoutes.DIARY,     R.string.tab_diary,     Icons.Default.MenuBook)
    data object Dashboard : TopTab(AppRoutes.DASHBOARD, R.string.tab_dashboard, Icons.Default.BarChart)
    data object Biolism   : TopTab(AppRoutes.BIOLISM,   R.string.tab_biolism,   Icons.Default.MonitorHeart)
    data object Settings  : TopTab(AppRoutes.SETTINGS,  R.string.tab_settings,  Icons.Default.Settings)
}

val TOP_TABS = listOf(TopTab.Scan, TopTab.Diary, TopTab.Dashboard, TopTab.Biolism, TopTab.Settings)

// Tab root routes — bottom nav visible, back arrow hidden
val TAB_ROOT_ROUTES = TOP_TABS.map { it.route }.toSet()

// Routes where the entire bottom nav is hidden (full-screen modals)
val HIDDEN_NAV_ROUTES = setOf(AppRoutes.ONBOARDING, AppRoutes.RESULT)
