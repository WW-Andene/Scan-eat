package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import fr.scanneat.presentation.ui.theme.*

@Composable
fun MainShell(startOnboarding: Boolean = false, startRoute: String? = null) {
    val navController   = rememberNavController()
    val backStack       = navController.currentBackStackEntryAsState()
    val currentRoute    = backStack.value?.destination?.route

    val showNav = HIDDEN_NAV_ROUTES.none { currentRoute == it }
    val bottomNavHazeState = remember { HazeState() }

    // True floating chrome: a Box, not a Scaffold, so AppNavGraph's own screens
    // fill the entire frame and the bottom nav is a z-ordered overlay on top of
    // them instead of a Scaffold slot that pads content away from it — scrolling
    // a list all the way down now shows cards passing underneath the nav's own
    // real backdrop blur (see FrostedGlassStyle in FloatingBars.kt), rather than
    // stopping short of it. Each screen reserves its own bottom clearance for
    // this via FloatingScreenScaffold's BottomNavClearance instead of this Box
    // consuming it via Scaffold's contentWindowInsets/padding, and registers its
    // own scrolling content as this nav's blur source via the same
    // LocalBottomNavHazeState provided below (a different composable subtree
    // than this one, hence the CompositionLocal instead of a direct param).
    Box(Modifier.fillMaxSize().background(Background)) {
        CompositionLocalProvider(LocalBottomNavHazeState provides bottomNavHazeState) {
            AppNavGraph(
                navController    = navController,
                startDestination = when {
                    startOnboarding    -> AppRoutes.ONBOARDING
                    startRoute != null -> startRoute
                    else               -> TopTab.Dashboard.route
                },
                modifier         = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible  = showNav,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Floating/detached bottom nav — margin on every side instead of the
            // previous edge-to-edge bar, rounded on all four corners (not just the
            // top two), glassy + elevated so it reads as a chrome piece hovering
            // over the content rather than fused to the screen edge. Handles its
            // own nav-bar inset (windowInsets = 0 below) so the floating gap is the
            // *only* gap, instead of stacking on top of NavigationBar's own default
            // system-bar padding.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.L, vertical = Spacing.S)
                    .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT)),
            ) {
            Surface(
                shape           = RoundedCornerShape(CardRadius.PROMINENT),
                color           = Color.Transparent,
                shadowElevation = 8.dp,
                modifier        = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius.PROMINENT))
                    .hazeEffect(state = bottomNavHazeState, style = FrostedGlassStyle),
            ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                windowInsets   = WindowInsets(0.dp),
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
                        icon  = { Icon(tab.icon, stringResource(tab.labelRes),
                            tint = if (isSelected) AccentCoral else IconInactive,
                            modifier = Modifier.size(IconSize.Nav)) },
                        label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) AccentCoral else IconInactive,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = AccentCoral,
                            selectedTextColor   = AccentCoral,
                            unselectedIconColor = IconInactive,
                            unselectedTextColor = IconInactive,
                            indicatorColor      = AccentCoral.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
            }
            }
        }
    }
}
