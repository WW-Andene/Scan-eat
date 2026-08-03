package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import fr.scanneat.R
import fr.scanneat.presentation.biolism.BiolismScreen
import fr.scanneat.presentation.premium.PremiumGate
import fr.scanneat.presentation.calendar.CalendarScreen
import fr.scanneat.presentation.customfood.CustomFoodScreen
import fr.scanneat.presentation.dashboard.DashboardScreen
import fr.scanneat.presentation.foodsearch.FoodSearchScreen
import fr.scanneat.presentation.diary.DiaryScreen
import fr.scanneat.presentation.grocery.GroceryScreen
import fr.scanneat.presentation.history.ScanHistoryScreen
import fr.scanneat.presentation.mealplan.MealPlanScreen
import fr.scanneat.presentation.onboarding.OnboardingScreen
import fr.scanneat.presentation.profile.ProfileScreen
import fr.scanneat.presentation.recipes.RecipesScreen
import fr.scanneat.presentation.reminders.RemindersScreen
import fr.scanneat.presentation.result.ResultScreen
import fr.scanneat.presentation.scan.ScanScreen
import fr.scanneat.presentation.settings.SettingsScreen
import fr.scanneat.presentation.templates.TemplatesScreen
import fr.scanneat.presentation.ui.theme.ScoreRevealEasing
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
                isTabSwitch() -> fadeIn(tween(200, easing = ScoreRevealEasing))
                else -> slideInHorizontally(tween(300, easing = ScoreRevealEasing)) { it } + fadeIn(tween(300, easing = ScoreRevealEasing))
            }
        },
        exitTransition   = {
            when {
                reducedMotion -> fadeOut(instant)
                isTabSwitch() -> fadeOut(tween(200, easing = ScoreRevealEasing))
                else -> slideOutHorizontally(tween(300, easing = ScoreRevealEasing)) { -it } + fadeOut(tween(300, easing = ScoreRevealEasing))
            }
        },
        popEnterTransition = {
            when {
                reducedMotion -> fadeIn(instant)
                isTabSwitch() -> fadeIn(tween(200, easing = ScoreRevealEasing))
                else -> slideInHorizontally(tween(300, easing = ScoreRevealEasing)) { -it } + fadeIn(tween(300, easing = ScoreRevealEasing))
            }
        },
        popExitTransition  = {
            when {
                reducedMotion -> fadeOut(instant)
                isTabSwitch() -> fadeOut(tween(200, easing = ScoreRevealEasing))
                else -> slideOutHorizontally(tween(300, easing = ScoreRevealEasing)) { it } + fadeOut(tween(300, easing = ScoreRevealEasing))
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
                // Previously navigated without popUpTo, leaving ONBOARDING under
                // SCAN_PROFILE on the back stack - pressing back from the profile
                // screen re-entered onboarding even though it was already complete.
                // Mirrors onDone's popUpTo so either post-onboarding path leaves the
                // Scan tab as the actual back-stack root.
                onGoToProfile = {
                    navController.navigate(TopTab.Scan.route) {
                        popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                    }
                    navController.navigate(AppRoutes.SCAN_PROFILE)
                },
            )
        }

        // ── Tab roots ─────────────────────────────────────────────────────
        composable(TopTab.Scan.route) {
            // launchSingleTop avoids stacking a second "result" entry if
            // onResultReady somehow fires twice for the same scan (e.g. a
            // double-tap/duplicate callback) - without it, popping back would
            // need two presses to actually leave Result instead of one.
            ScanScreen(onResultReady = { id -> navController.navigate(AppRoutes.result(id, fresh = true)) { launchSingleTop = true } })
        }

        composable(TopTab.Diary.route) { backStackEntry ->
            // isTabRoot=true suppresses the back arrow inside DiaryScreen.
            // pendingSelectedDate: Calendar's "Open in Journal" hands a date back via
            // this entry's own SavedStateHandle (see AppRoutes.CALENDAR below) - the
            // standard Navigation-Compose "return a result" pattern.
            DiaryScreen(
                onBack = {},
                isTabRoot = true,
                onOpenCalendar = { navController.navigate(AppRoutes.CALENDAR) },
                pendingSelectedDate = backStackEntry.savedStateHandle.get<String>("diary_selected_date"),
                onPendingDateConsumed = { backStackEntry.savedStateHandle.remove<String>("diary_selected_date") },
                pendingTab = backStackEntry.savedStateHandle.get<String>("diary_selected_tab"),
                onPendingTabConsumed = { backStackEntry.savedStateHandle.remove<String>("diary_selected_tab") },
            )
        }

        composable(TopTab.Dashboard.route) {
            DashboardScreen(
                onBack          = {},
                isTabRoot       = true,
                onOpenHistory   = { navController.navigate(AppRoutes.SCAN_HISTORY) },
                onOpenRecipes   = { navController.navigate(AppRoutes.RECIPES) },
                onOpenTemplates = { navController.navigate(AppRoutes.TEMPLATES) },
                onOpenMealPlan  = { navController.navigate(AppRoutes.MEAL_PLAN) },
                onOpenGrocery        = { navController.navigate(AppRoutes.GROCERY) },
                onOpenCustomFoods     = { navController.navigate(AppRoutes.CUSTOM_FOODS) },
                onOpenFavorites      = { navController.navigate(AppRoutes.FAVORITES) },
                onOpenResult         = { id -> navController.navigate(AppRoutes.result(id)) },
                onOpenCalendar       = { navController.navigate(AppRoutes.CALENDAR) },
                onOpenFoodSearch     = { navController.navigate(AppRoutes.FOOD_SEARCH) },
                onOpenExpenses       = {
                    navController.getBackStackEntry(TopTab.Diary.route).savedStateHandle["diary_selected_tab"] = "EXPENSES"
                    navController.navigate(TopTab.Diary.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenScan           = {
                    navController.navigate(TopTab.Scan.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }

        composable(TopTab.Biolism.route) {
            PremiumGate(
                lockedMessage = stringResource(R.string.settings_premium_required_biolism),
                onOpenSettings = {
                    navController.navigate(TopTab.Settings.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            ) { BiolismScreen() }
        }

        composable(AppRoutes.FOOD_SEARCH) {
            FoodSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenResult = { id -> navController.navigate(AppRoutes.result(id)) },
            )
        }

        composable(TopTab.Settings.route) {
            SettingsScreen(
                onBack = {},
                isTabRoot = true,
                onOpenProfile = { navController.navigate(AppRoutes.SCAN_PROFILE) },
                onOpenReminders = { navController.navigate(AppRoutes.REMINDERS) },
            )
        }

        // ── Full-screen nested routes ──────────────────────────────────────
        composable(
            route     = AppRoutes.RESULT,
            arguments = listOf(
                navArgument("scanId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
            ),
        ) {
            ResultScreen(
                onBack = { navController.popBackStack() },
                // AlternativeCard's "here's something better you already found"
                // previously had no way to actually open it - same nav pattern
                // ScanHistoryScreen/DashboardScreen already use below.
                onOpenResult = { id -> navController.navigate(AppRoutes.result(id)) },
                onOpenProfile = { navController.navigate(AppRoutes.SCAN_PROFILE) },
                onLog  = {
                    // collapseToTabRoot() first - the popUpTo(startDestination){saveState=true}
                    // below only saves each TAB's own state, it doesn't remove whatever was
                    // pushed on top of the tab Result was reached from (just Result itself
                    // when reached directly from Scan/Dashboard, but History/Favorites/
                    // Recherche when reached through one of those instead) from that saved
                    // stack. Without this, the intermediate screen(s) stayed saved on top of
                    // that tab's own back stack: switching back to it later restored straight
                    // onto Result (or History/Favorites/Recherche) instead of the tab itself.
                    navController.collapseToTabRoot()
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
        //
        // Recipes/Templates/MealPlan/Grocery/CustomFoods constantly feed into
        // each other but previously had no way to reach one another directly -
        // every lateral move required backing out to Dashboard first (see
        // PlanningSwitcherMenu's own doc comment). onNavigateToPlanning wires
        // each of the five to a plain push of whichever sibling the user picks.
        composable(AppRoutes.RECIPES) {
            RecipesScreen(onBack = { navController.popBackStack() }, onNavigateToPlanning = { navController.navigateToPlanning(it) })
        }
        composable(AppRoutes.TEMPLATES) {
            TemplatesScreen(onBack = { navController.popBackStack() }, onNavigateToPlanning = { navController.navigateToPlanning(it) })
        }
        composable(AppRoutes.MEAL_PLAN) {
            MealPlanScreen(onBack = { navController.popBackStack() }, onNavigateToPlanning = { navController.navigateToPlanning(it) })
        }
        composable(AppRoutes.GROCERY) {
            GroceryScreen(onBack = { navController.popBackStack() }, onNavigateToPlanning = { navController.navigateToPlanning(it) })
        }
        composable(AppRoutes.CUSTOM_FOODS) {
            CustomFoodScreen(onBack = { navController.popBackStack() }, onNavigateToPlanning = { navController.navigateToPlanning(it) })
        }
        composable(AppRoutes.CALENDAR)     {
            CalendarScreen(
                onBack = { navController.popBackStack() },
                // Calendar can be reached from either Diary or Dashboard (both have
                // onOpenCalendar) - always explicitly switching to the Diary tab (same
                // pattern ResultScreen's onLog already uses) works from either entry
                // point, unlike popBackStack() which would land back on Dashboard when
                // that's where the user actually came from.
                onOpenDate = { date ->
                    // collapseToTabRoot() first - same fix as ResultScreen's onLog above,
                    // generalized: Calendar can be reached from Diary OR Dashboard's
                    // onOpenCalendar, and the popUpTo(startDestination){saveState=true}
                    // below only saves each TAB's own back stack, it doesn't remove
                    // Calendar itself from that saved stack.
                    navController.collapseToTabRoot()
                    navController.navigate(TopTab.Diary.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    navController.getBackStackEntry(TopTab.Diary.route).savedStateHandle["diary_selected_date"] = date.toString()
                },
            )
        }
        composable(AppRoutes.REMINDERS)    { RemindersScreen(onBack = { navController.popBackStack() }) }
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

/**
 * Pops entries off the back stack until the current destination is a tab root
 * (or nothing is left to pop). A single `popBackStack()` before a tab-switch
 * `navigate(...) { popUpTo(startDestination) { saveState = true } }` only
 * strips ONE level - correct when the screen initiating the switch (Result,
 * Calendar) sits directly on top of its tab, but Result is also reachable two
 * levels deep (Dashboard → History/Favorites/Recherche → Result), in which
 * case a single pop left History/Favorites/Recherche stuck on top of the
 * Dashboard tab's saved branch - the exact "stuck screen on return to this
 * tab" bug class ResultScreen.onLog and CalendarScreen.onOpenDate were each
 * already special-cased for at one level, generalized here to any depth.
 */
private fun NavHostController.collapseToTabRoot() {
    while (currentDestination?.route !in TAB_ROOT_ROUTES) {
        if (!popBackStack()) break
    }
}

/** See PlanningSwitcherMenu's doc comment - a plain push, same as reaching any of these from Dashboard. */
private fun NavHostController.navigateToPlanning(dest: PlanningDestination) {
    val route = when (dest) {
        PlanningDestination.RECIPES      -> AppRoutes.RECIPES
        PlanningDestination.TEMPLATES    -> AppRoutes.TEMPLATES
        PlanningDestination.MEAL_PLAN    -> AppRoutes.MEAL_PLAN
        PlanningDestination.GROCERY      -> AppRoutes.GROCERY
        PlanningDestination.CUSTOM_FOODS -> AppRoutes.CUSTOM_FOODS
    }
    navigate(route)
}
