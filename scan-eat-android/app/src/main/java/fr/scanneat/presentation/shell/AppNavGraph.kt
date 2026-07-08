package fr.scanneat.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import fr.scanneat.presentation.activity.ActivityScreen
import fr.scanneat.presentation.biolism.BiolismScreen
import fr.scanneat.presentation.customfood.CustomFoodScreen
import fr.scanneat.presentation.dashboard.DashboardScreen
import fr.scanneat.presentation.diary.DiaryScreen
import fr.scanneat.presentation.fasting.FastingScreen
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
import fr.scanneat.presentation.weight.WeightScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        modifier         = modifier,
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
            // isTabRoot=true suppresses the back arrow inside DiaryScreen
            DiaryScreen(onBack = {}, isTabRoot = true)
        }

        composable(TopTab.Dashboard.route) {
            DashboardScreen(
                onBack          = {},
                onOpenWeight    = { navController.navigate(AppRoutes.WEIGHT) },
                onOpenFasting   = { navController.navigate(AppRoutes.FASTING) },
                onOpenHydration = { navController.navigate(AppRoutes.HYDRATION) },
                onOpenActivity  = { navController.navigate(AppRoutes.ACTIVITY) },
                onOpenHistory   = { navController.navigate(AppRoutes.SCAN_HISTORY) },
                onOpenRecipes   = { navController.navigate(AppRoutes.RECIPES) },
                onOpenTemplates = { navController.navigate(AppRoutes.TEMPLATES) },
                onOpenMealPlan  = { navController.navigate(AppRoutes.MEAL_PLAN) },
                onOpenGrocery        = { navController.navigate(AppRoutes.GROCERY) },
                onOpenCustomFoods     = { navController.navigate(AppRoutes.CUSTOM_FOODS) },
                onOpenProfile   = { navController.navigate(AppRoutes.SCAN_PROFILE) },
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
                onLog  = { navController.navigate(TopTab.Diary.route) { launchSingleTop = true } },
            )
        }

        composable(AppRoutes.SCAN_PROFILE) { ProfileScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.WEIGHT)       { WeightScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.FASTING)      { FastingScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.HYDRATION)    { HydrationScreen(onBack = { navController.popBackStack() }) }
        composable(AppRoutes.ACTIVITY)     { ActivityScreen(onBack = { navController.popBackStack() }) }
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
    }
}
