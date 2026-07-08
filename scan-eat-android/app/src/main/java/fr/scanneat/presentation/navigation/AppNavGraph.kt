package fr.scanneat.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import fr.scanneat.presentation.activity.ActivityScreen
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

sealed class Screen(val route: String) {
    data object Onboarding  : Screen("onboarding")
    data object Scan        : Screen("scan")
    data object Result      : Screen("result/{scanId}") {
        fun createRoute(id: Long) = "result/$id"
    }
    data object Diary       : Screen("diary")
    data object Dashboard   : Screen("dashboard")
    data object Settings    : Screen("settings")
    data object Profile     : Screen("profile")
    data object Weight      : Screen("weight")
    data object Fasting     : Screen("fasting")
    data object Hydration   : Screen("hydration")
    data object Activity    : Screen("activity")
    data object ScanHistory : Screen("scan_history")
    data object Recipes     : Screen("recipes")
    data object Templates   : Screen("templates")
    data object MealPlan    : Screen("meal_plan")
    data object Grocery     : Screen("grocery")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route,
) {
    fun nav(route: String) = navController.navigate(route)
    fun back() = navController.popBackStack()

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = { nav(Screen.Scan.route).also { navController.popBackStack(Screen.Onboarding.route, inclusive = true) } },
                onGoToProfile = { nav(Screen.Profile.route) },
            )
        }

        composable(Screen.Scan.route) {
            ScanScreen(
                onResultReady   = { id -> nav(Screen.Result.createRoute(id)) },
                onOpenDiary     = { nav(Screen.Diary.route) },
                onOpenDashboard = { nav(Screen.Dashboard.route) },
                onOpenSettings  = { nav(Screen.Settings.route) },
                onOpenProfile   = { nav(Screen.Profile.route) },
            )
        }

        composable(
            route     = Screen.Result.route,
            arguments = listOf(navArgument("scanId") { type = NavType.LongType; defaultValue = 0L }),
        ) {
            ResultScreen(onBack = { back() }, onLog = { nav(Screen.Diary.route) })
        }

        composable(Screen.Diary.route)     { DiaryScreen(onBack = { back() }) }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onBack          = { back() },
                onOpenWeight    = { nav(Screen.Weight.route) },
                onOpenFasting   = { nav(Screen.Fasting.route) },
                onOpenHydration = { nav(Screen.Hydration.route) },
                onOpenActivity  = { nav(Screen.Activity.route) },
                onOpenHistory   = { nav(Screen.ScanHistory.route) },
                onOpenRecipes   = { nav(Screen.Recipes.route) },
                onOpenTemplates = { nav(Screen.Templates.route) },
                onOpenMealPlan  = { nav(Screen.MealPlan.route) },
                onOpenGrocery   = { nav(Screen.Grocery.route) },
            )
        }

        composable(Screen.Settings.route)   { SettingsScreen(onBack = { back() }) }
        composable(Screen.Profile.route)    { ProfileScreen(onBack = { back() }) }
        composable(Screen.Weight.route)     { WeightScreen(onBack = { back() }) }
        composable(Screen.Fasting.route)    { FastingScreen(onBack = { back() }) }
        composable(Screen.Hydration.route)  { HydrationScreen(onBack = { back() }) }
        composable(Screen.Activity.route)   { ActivityScreen(onBack = { back() }) }
        composable(Screen.Recipes.route)    { RecipesScreen(onBack = { back() }) }
        composable(Screen.Templates.route)  { TemplatesScreen(onBack = { back() }) }
        composable(Screen.MealPlan.route)   { MealPlanScreen(onBack = { back() }) }
        composable(Screen.Grocery.route)    { GroceryScreen(onBack = { back() }) }

        composable(Screen.ScanHistory.route) {
            ScanHistoryScreen(
                onOpenResult = { id -> nav(Screen.Result.createRoute(id)) },
                onBack       = { back() },
            )
        }
    }
}
