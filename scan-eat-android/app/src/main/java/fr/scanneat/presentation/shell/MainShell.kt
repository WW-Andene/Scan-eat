package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import fr.scanneat.presentation.ui.theme.*

@Composable
fun MainShell(
    startOnboarding: Boolean = false,
    startRoute: String? = null,
) {
    val navController   = rememberNavController()
    val backStack       = navController.currentBackStackEntryAsState()
    val currentRoute    = backStack.value?.destination?.route

    val showNav = HIDDEN_NAV_ROUTES.none { currentRoute == it }
    val bottomNavHazeState = remember { HazeState() }

    // User-requested: long-press a nav tab and drag it onto another one to
    // swap their positions - persisted via MainShellViewModel/UserPreferences
    // so a custom layout survives an app restart.
    val shellViewModel: MainShellViewModel = hiltViewModel()
    val navOrderCsv = shellViewModel.navTabOrder.collectAsStateWithLifecycle()
    val navTabs = remember(navOrderCsv.value) { parseTopTabOrder(navOrderCsv.value) }

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
        // Gated on rememberReducedMotion(), like every other prominent animation
        // in the app (see Motion.kt's own doc comment) - this one was added
        // without the check, unlike ambientGloom/pressScale/rememberHeroEntrance.
        val reduceMotion = rememberReducedMotion()
        AnimatedVisibility(
            visible  = showNav,
            enter    = if (reduceMotion) EnterTransition.None else fadeIn(),
            exit     = if (reduceMotion) ExitTransition.None else fadeOut(),
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
                    // Shared with FloatingTopBar (see FloatingChromeMargin's own doc
                    // comment) - keeps this bottom margin identical to the header's
                    // top margin instead of the two independent literals this used
                    // to be.
                    .padding(horizontal = FloatingChromeMargin.horizontal, vertical = FloatingChromeMargin.vertical)
                    .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT)),
            ) {
            Surface(
                shape           = RoundedCornerShape(CardRadius.PROMINENT),
                color           = Color.Transparent,
                // MIUI-observed bug (see ScanEatCard.kt): ambientColor/spotColor-tinted
                // Modifier.shadow renders as a solid, hard-edged grey rectangle instead
                // of a soft shadow on some OEM skins. Reverted to the neutral default
                // shadow color — Surface's own shadowElevation stays 0 so the two don't
                // stack.
                shadowElevation = 0.dp,
                modifier        = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(CardRadius.PROMINENT))
                    .clip(RoundedCornerShape(CardRadius.PROMINENT))
                    .hazeEffect(state = bottomNavHazeState, style = FrostedGlassStyle),
            ) {
            // Replaces Material3's NavigationBar/NavigationBarItem (which owns its
            // own click/ripple gesture detection with no hook for a second,
            // independent long-press-drag gesture on the same item) with a plain
            // Row of custom items so drag-to-swap can be layered on cleanly - see
            // DiaryTabButton's identical detectTapGestures-instead-of-onClick
            // reasoning in DiaryHeader.kt.
            val view = LocalView.current
            val haptics = LocalHapticFeedback.current
            var draggedNavTab by remember { mutableStateOf<TopTab?>(null) }
            var dragNavPointerScreenPos by remember { mutableStateOf(Offset.Zero) }
            var dragOverNavTab by remember { mutableStateOf<TopTab?>(null) }
            val navTabBounds = remember { mutableStateMapOf<TopTab, Rect>() }

            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hierarchy = backStack.value?.destination?.hierarchy
                navTabs.forEach { tab ->
                    val isSelected = hierarchy?.any { it.route == tab.route } == true
                    val isDragSource = draggedNavTab == tab
                    val isDropTarget = draggedNavTab != null && dragOverNavTab == tab && !isDragSource
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                val loc = IntArray(2)
                                view.getLocationOnScreen(loc)
                                val topLeft = coords.localToRoot(Offset.Zero) + Offset(loc[0].toFloat(), loc[1].toFloat())
                                navTabBounds[tab] = Rect(topLeft, coords.size.toSize())
                            }
                            .graphicsLayer {
                                alpha  = if (isDragSource) 0.4f else 1f
                                scaleX = if (isDropTarget) 1.15f else 1f
                                scaleY = if (isDropTarget) 1.15f else 1f
                            }
                            .pointerInput(tab.route) {
                                detectTapGestures(
                                    onTap = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    },
                                )
                            }
                            // User-requested: hold, drag, and drop a nav tab onto
                            // another one to swap their positions. Coexists with the
                            // detectTapGestures above the same way DiaryHeader's does
                            // - a plain short tap is never claimed by a long-press
                            // detector, so ordinary navigation still works untouched.
                            .pointerInput(tab.route) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { localOffset ->
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggedNavTab = tab
                                        val origin = navTabBounds[tab]?.topLeft ?: Offset.Zero
                                        dragNavPointerScreenPos = origin + localOffset
                                        dragOverNavTab = navTabBounds.entries.firstOrNull { it.value.contains(dragNavPointerScreenPos) }?.key
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragNavPointerScreenPos += dragAmount
                                        dragOverNavTab = navTabBounds.entries.firstOrNull { it.value.contains(dragNavPointerScreenPos) }?.key
                                    },
                                    onDragEnd = {
                                        val from = draggedNavTab
                                        val to = dragOverNavTab
                                        if (from != null && to != null && from != to) {
                                            val fromIdx = navTabs.indexOf(from)
                                            val toIdx = navTabs.indexOf(to)
                                            val newOrder = navTabs.toMutableList()
                                            newOrder[fromIdx] = to
                                            newOrder[toIdx] = from
                                            shellViewModel.setNavTabOrder(serializeTopTabOrder(newOrder))
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        draggedNavTab = null
                                        dragOverNavTab = null
                                    },
                                    onDragCancel = {
                                        draggedNavTab = null
                                        dragOverNavTab = null
                                    },
                                )
                            },
                    ) {
                        Icon(
                            tab.icon, stringResource(tab.labelRes),
                            tint = if (isSelected) AccentCoral else IconInactive,
                            modifier = Modifier.size(IconSize.Nav),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) AccentCoral else IconInactive,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            }
            }
        }
    }
}
