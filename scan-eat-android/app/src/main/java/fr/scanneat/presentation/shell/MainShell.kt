package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.scanneat.presentation.ui.theme.*

@Composable
fun MainShell(startOnboarding: Boolean = false) {
    val navController   = rememberNavController()
    val backStack       = navController.currentBackStackEntryAsState()
    val currentRoute    = backStack.value?.destination?.route

    val showNav = HIDDEN_NAV_ROUTES.none { currentRoute == it }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets.systemBars,
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
                            icon  = { Icon(tab.icon, stringResource(tab.labelRes),
                                tint = if (isSelected) AccentGreen else IconInactive,
                                modifier = Modifier.size(22.dp)) },
                            label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) AccentGreen else IconInactive,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        AppNavGraph(
            navController    = navController,
            startDestination = if (startOnboarding) AppRoutes.ONBOARDING else TopTab.Dashboard.route,
            modifier         = Modifier.padding(padding),
        )
    }
}
