package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.scanneat.presentation.activity.ActivityScreen
import fr.scanneat.presentation.biolism.BiolismScreen
import fr.scanneat.presentation.dashboard.DashboardScreen
import fr.scanneat.presentation.diary.DiaryScreen
import fr.scanneat.presentation.fasting.FastingScreen
import fr.scanneat.presentation.customfood.CustomFoodScreen
import fr.scanneat.presentation.grocery.GroceryScreen
import fr.scanneat.presentation.history.ScanHistoryScreen
import fr.scanneat.presentation.hydration.HydrationScreen
import fr.scanneat.presentation.mealplan.MealPlanScreen
import fr.scanneat.presentation.onboarding.OnboardingScreen
import fr.scanneat.presentation.profile.ProfileScreen
import fr.scanneat.presentation.recipes.RecipesScreen
import fr.scanneat.presentation.result.ResultScreen
import fr.scanneat.presentation.scan.ScanScreen
import fr.scanneat.presentation.settings.SettingsScreen
import fr.scanneat.presentation.templates.TemplatesScreen
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.weight.WeightScreen

sealed class TopTab(val route: String, val label: String, val icon: ImageVector) {
    data object Scan      : TopTab("scan",      "Scanner",  Icons.Default.QrCodeScanner)
    data object Diary     : TopTab("diary",     "Journal",  Icons.Default.MenuBook)
    data object Dashboard : TopTab("dashboard", "Tableau",  Icons.Default.BarChart)
    data object Biolism   : TopTab("biolism",   "Biolism",  Icons.Default.MonitorHeart)
    data object Settings  : TopTab("settings",  "Réglages", Icons.Default.Settings)
}

private val TOP_TABS = listOf(TopTab.Scan, TopTab.Diary, TopTab.Dashboard, TopTab.Biolism, TopTab.Settings)

// Tab root routes — bottom nav visible, back arrow hidden
private val TAB_ROOT_ROUTES = TOP_TABS.map { it.route }.toSet()

// Routes where the entire bottom nav is hidden (full-screen modals)
private val HIDDEN_NAV_ROUTES = setOf("onboarding", "result/{scanId}")

@Composable
fun MainShell(startOnboarding: Boolean = false, theme: String = "oled") {
    val navController   = rememberNavController()
    val backStack       = navController.currentBackStackEntryAsState()
    val currentRoute    = backStack.value?.destination?.route

    val showNav = HIDDEN_NAV_ROUTES.none { currentRoute == it }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            AnimatedVisibility(visible = showNav, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar(
                    containerColor = SurfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier.height(64.dp),
                ) {
                    val hierarchy = backStack.value?.destination?.hierarchy
                    TOP_TABS.forEach { tab ->
                        val isSelected = hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = isSelected,
                            onClick  = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = { Icon(tab.icon, tab.label,
                                tint = if (isSelected) AccentGreen else IconInactive,
                                modifier = Modifier.size(22.dp)) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) AccentGreen else IconInactive) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = AccentGreen,
                                selectedTextColor   = AccentGreen,
                                unselectedIconColor = IconInactive,
                                unselectedTextColor = IconInactive,
                                indicatorColor      = AccentGreen.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController    = navController,
            startDestination = if (startOnboarding) "onboarding" else TopTab.Scan.route,
            modifier         = Modifier.padding(padding),
        ) {
            // ── Onboarding ────────────────────────────────────────────────────
            composable("onboarding") {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(TopTab.Scan.route) {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onGoToProfile = { navController.navigate("scan_profile") },
                )
            }

            // ── Tab roots ─────────────────────────────────────────────────────
            composable(TopTab.Scan.route) {
                ScanScreen(onResultReady = { id -> navController.navigate("result/$id") })
            }

            composable(TopTab.Diary.route) {
                // isTabRoot=true suppresses the back arrow inside DiaryScreen
                DiaryScreen(onBack = {}, isTabRoot = true)
            }

            composable(TopTab.Dashboard.route) {
                DashboardScreen(
                    onBack          = {},
                    onOpenWeight    = { navController.navigate("weight") },
                    onOpenFasting   = { navController.navigate("fasting") },
                    onOpenHydration = { navController.navigate("hydration") },
                    onOpenActivity  = { navController.navigate("activity") },
                    onOpenHistory   = { navController.navigate("scan_history") },
                    onOpenRecipes   = { navController.navigate("recipes") },
                    onOpenTemplates = { navController.navigate("templates") },
                    onOpenMealPlan  = { navController.navigate("meal_plan") },
                    onOpenGrocery        = { navController.navigate("grocery") },
                    onOpenCustomFoods     = { navController.navigate("custom_foods") },
                    onOpenProfile   = { navController.navigate("scan_profile") },
                )
            }

            composable(TopTab.Biolism.route) { BiolismScreen(theme = theme) }

            composable(TopTab.Settings.route) {
                SettingsScreen(
                    onBack = {},
                    isTabRoot = true,
                    onOpenProfile = { navController.navigate("scan_profile") },
                )
            }

            // ── Full-screen nested routes ──────────────────────────────────────
            composable(
                route     = "result/{scanId}",
                arguments = listOf(navArgument("scanId") { type = NavType.LongType; defaultValue = 0L }),
            ) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onLog  = { navController.navigate(TopTab.Diary.route) { launchSingleTop = true } },
                )
            }

            composable("scan_profile") { ProfileScreen(onBack = { navController.popBackStack() }) }
            composable("weight")       { WeightScreen(onBack = { navController.popBackStack() }) }
            composable("fasting")      { FastingScreen(onBack = { navController.popBackStack() }) }
            composable("hydration")    { HydrationScreen(onBack = { navController.popBackStack() }) }
            composable("activity")     { ActivityScreen(onBack = { navController.popBackStack() }) }
            composable("recipes")      { RecipesScreen(onBack = { navController.popBackStack() }) }
            composable("templates")    { TemplatesScreen(onBack = { navController.popBackStack() }) }
            composable("meal_plan")    { MealPlanScreen(onBack = { navController.popBackStack() }) }
            composable("grocery")      { GroceryScreen(onBack = { navController.popBackStack() }) }
            composable("custom_foods") { CustomFoodScreen(onBack = { navController.popBackStack() }) }
            composable("scan_history") {
                ScanHistoryScreen(
                    onOpenResult = { id -> navController.navigate("result/$id") },
                    onBack       = { navController.popBackStack() },
                )
            }
        }
    }
}
