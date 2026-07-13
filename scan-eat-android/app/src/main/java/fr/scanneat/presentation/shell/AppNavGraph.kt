package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import fr.scanneat.presentation.biolism.BiolismScreen
import fr.scanneat.presentation.customfood.CustomFoodScreen
import fr.scanneat.presentation.dashboard.DashboardScreen
import fr.scanneat.presentation.diary.DiaryScreen
import fr.scanneat.presentation.grocery.GroceryScreen
import fr.scanneat.presentation.history.ScanHistoryScreen
import fr.scanneat.presentation.mealplan.MealPlanScreen
import fr.scanneat.presentation.onboarding.OnboardingScreen
import fr.scanneat.presentation.profile.ProfileScreen
import fr.scanneat.presentation.recipes.RecipesScreen
import fr.scanneat.presentation.result.ResultScreen
import fr.scanneat.presentation.scan.ScanScreen
import fr.scanneat.presentation.settings.SettingsScreen
import fr.scanneat.presentation.templates.TemplatesScreen
import fr.scanneat.presentation.ui.theme.rememberReducedMotion

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    // System "Remove animations" setting must gate nav transitions too, not just
    // the score-reveal animation — a slide+fade on every screen push/pop is
    // exactly the kind of motion that setting exists to suppress. When reduced,
    // collapse every transition to an instant (zero-duration) fade so content
    // still swaps but nothing slides/animates.
    val reducedMotion = rememberReducedMotion()
    val instant = tween<Float>(durationMillis = 0)
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
        enterTransition  = {
            when {
                reducedMotion -> fadeIn(instant)
                isTabSwitch() -> fadeIn(tween(200))
                else -> slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
            }
        },
        exitTransition   = {
            when {
                reducedMotion -> fadeOut(instant)
                isTabSwitch() -> fadeOut(tween(200))
                else -> slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
            }
        },
        popEnterTransition = {
            when {
                reducedMotion -> fadeIn(instant)
                isTabSwitch() -> fadeIn(tween(200))
                else -> slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))
            }
        },
        popExitTransition  = {
            when {
                reducedMotion -> fadeOut(instant)
                isTabSwitch() -> fadeOut(tween(200))
                else -> slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
            }
        },
    ) {
        // ── Onboarding ────────────────────────────────────────────────────
        composable(AppRoutes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(TopTab.Scan.route) {
                        popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                    }
                },
                onGoToProfile = { navController.navigate(AppRoutes.SCAN_PROFILE) },
            )
        }

        // ── Tab roots ─────────────────────────────────────────────────────
        composable(TopTab.Scan.route) {
            ScanScreen(onResultReady = { id -> navController.navigate(AppRoutes.result(id)) })
        }

        composable(TopTab.Diary.route) {
            // isTabRoot=true suppresses the back arrow inside DiaryScreen.
            DiaryScreen(onBack = {}, isTabRoot = true)
        }

        composable(TopTab.Dashboard.route) {
            DashboardScreen(
                onBack          = {},
                onOpenHistory   = { navController.navigate(AppRoutes.SCAN_HISTORY) },
                onOpenRecipes   = { navController.navigate(AppRoutes.RECIPES) },
                onOpenTemplates = { navController.navigate(AppRoutes.TEMPLATES) },
                onOpenMealPlan  = { navController.navigate(AppRoutes.MEAL_PLAN) },
                onOpenGrocery        = { navController.navigate(AppRoutes.GROCERY) },
                onOpenCustomFoods     = { navController.navigate(AppRoutes.CUSTOM_FOODS) },
                onOpenFavorites      = { navController.navigate(AppRoutes.FAVORITES) },
            )
        }

        composable(TopTab.Biolism.route) { BiolismScreen() }

        composable(TopTab.Settings.route) {
            SettingsScreen(
                onBack = {},
                isTabRoot = true,
                onOpenProfile = { navController.navigate(AppRoutes.SCAN_PROFILE) },
            )
        }

        // ── Full-screen nested routes ──────────────────────────────────────
        composable(
            route     = AppRoutes.RESULT,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType; defaultValue = 0L }),
        ) {
            ResultScreen(
                onBack = { navController.popBackStack() },
                onLog  = {
                    // Match MainShell's tab-switch options exactly — a bare
                    // navigate{launchSingleTop} pushed Diary on top of Scan→Result
                    // instead of switching tabs, so system back from Diary
                    // returned to Result (not tab behavior) and this Diary
                    // instance didn't share saved state with the one the bottom
                    // bar restores.
                    navController.navigate(TopTab.Diary.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(AppRoutes.SCAN_PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
        // Weight/Fasting/Hydration/Activity are no longer separately pushed
        // routes — they're embedded as Journal sub-tabs (see DiaryScreen.kt).
        composable(AppRoutes.RECIPES)      { RecipesScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.TEMPLATES)    { TemplatesScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.MEAL_PLAN)    { MealPlanScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.GROCERY)      { GroceryScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.CUSTOM_FOODS) { CustomFoodScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.SCAN_HISTORY) {
            ScanHistoryScreen(
                onOpenResult = { id -> navController.navigate(AppRoutes.result(id)) },
                onBack       = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.FAVORITES) {
            ScanHistoryScreen(
                onOpenResult = { id -> navController.navigate(AppRoutes.result(id)) },
                onBack       = { navController.popBackStack() },
                startFavoritesOnly = true,
            )
        }
    }
}

/** Tab-root ↔ tab-root switches use fade-through; everything else is a peer-level push/pop (slide). */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TAB_ROOT_ROUTES && targetState.destination.route in TAB_ROOT_ROUTES
