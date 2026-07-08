package fr.scanneat.presentation.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class TopTab(val route: String, val label: String, val icon: ImageVector) {
    data object Scan      : TopTab(AppRoutes.SCAN,      "Scanner",  Icons.Default.QrCodeScanner)
    data object Diary     : TopTab(AppRoutes.DIARY,     "Journal",  Icons.Default.MenuBook)
    data object Dashboard : TopTab(AppRoutes.DASHBOARD, "Tableau",  Icons.Default.BarChart)
    data object Biolism   : TopTab(AppRoutes.BIOLISM,   "Biolism",  Icons.Default.MonitorHeart)
    data object Settings  : TopTab(AppRoutes.SETTINGS,  "Réglages", Icons.Default.Settings)
}

val TOP_TABS = listOf(TopTab.Scan, TopTab.Diary, TopTab.Dashboard, TopTab.Biolism, TopTab.Settings)

// Tab root routes — bottom nav visible, back arrow hidden
val TAB_ROOT_ROUTES = TOP_TABS.map { it.route }.toSet()

// Routes where the entire bottom nav is hidden (full-screen modals)
val HIDDEN_NAV_ROUTES = setOf(AppRoutes.ONBOARDING, AppRoutes.RESULT)
