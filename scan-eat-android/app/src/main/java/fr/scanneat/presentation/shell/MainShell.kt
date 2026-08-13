package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.delay

/** Same reasoning as DiaryHeader.kt's ARM_AUTO_CANCEL_MS - the bottom nav is
 *  visible across the whole app, not scoped to one screen, so a stray armed
 *  tab here would be even easier to forget about and trigger unintentionally
 *  much later than the Journal header's equivalent. */
private const val NAV_ARM_AUTO_CANCEL_MS = 10_000L

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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

    // User-requested: long-press a nav tab to arm it, then tap another one
    // to swap their positions - persisted via MainShellViewModel/
    // UserPreferences so a custom layout survives an app restart.
    val shellViewModel: MainShellViewModel = hiltViewModel()
    val navOrderCsv = shellViewModel.navTabOrder.collectAsStateWithLifecycle()
    val navTabs = remember(navOrderCsv.value) { parseTopTabOrder(navOrderCsv.value) }
    var armedNavTab by remember { mutableStateOf<TopTab?>(null) }
    LaunchedEffect(armedNavTab) {
        if (armedNavTab != null) {
            delay(NAV_ARM_AUTO_CANCEL_MS)
            armedNavTab = null
        }
    }

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
        // User-requested (Notebook theme): "nav bar become page reminder on
        // right side, become longer and on the said tab" - a vertical column
        // of colored index-tab bookmarks along the right edge (the mockups
        // the user provided show exactly this: small colored square tabs
        // stacked down a notebook's right edge), replacing the floating
        // bottom-center pill this app uses for every other theme. The
        // click/long-press-to-reorder behavior is identical either way -
        // only the layout/visual treatment branches on theme, factored into
        // navTabClickHandler below so that logic isn't duplicated between
        // the two layouts.
        val haptics = LocalHapticFeedback.current
        val navTabClickHandler: (TopTab) -> Unit = { tab ->
            val armed = armedNavTab
            if (armed != null) {
                if (armed != tab) {
                    val fromIdx = navTabs.indexOf(armed)
                    val toIdx = navTabs.indexOf(tab)
                    val newOrder = navTabs.toMutableList()
                    newOrder[fromIdx] = tab
                    newOrder[toIdx] = armed
                    shellViewModel.setNavTabOrder(serializeTopTabOrder(newOrder))
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                armedNavTab = null
            } else {
                navController.navigate(tab.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            }
        }
        val navTabLongClickHandler: (TopTab) -> Unit = { tab ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            armedNavTab = if (armedNavTab == tab) null else tab
        }
        if (LocalThemeName.current == "notebook") {
            AnimatedVisibility(
                visible  = showNav,
                enter    = if (reduceMotion) EnterTransition.None else fadeIn(),
                exit     = if (reduceMotion) ExitTransition.None else fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                val hierarchy = backStack.value?.destination?.hierarchy
                val tabColors = NotebookPostItColors
                // User-reported: tabs weren't flush against the screen edge
                // and grew the wrong direction when expanding - both caused
                // by the same root cause. Column had no horizontalAlignment
                // (defaults to Start), so its width was set by its WIDEST
                // child (the expanded 88dp tab) and every other, narrower
                // tab sat flush to that width's LEFT edge - leaving a gap
                // between the narrower tabs and the true screen edge on the
                // right, and making an expanding tab grow further right
                // (away from the edge) instead of left (into the screen,
                // staying flush). Alignment.End anchors every tab's own
                // right edge to the column's (and thus the screen's) right
                // edge regardless of its current width, so growth is always
                // leftward and every tab - expanded or not - stays flush.
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    navTabs.forEachIndexed { i, tab ->
                        val isSelected = hierarchy?.any { it.route == tab.route } == true
                        val isArmed = armedNavTab == tab
                        val isReplaceTarget = armedNavTab != null && !isArmed
                        val tabColor = tabColors[i % tabColors.size]
                        // User-requested: "animation de développer/rétracté
                        // pour les marque page des onglet quand dessus" - a
                        // touch device has no true hover, so "dessus" is
                        // read as "pressed" (the closest touch equivalent):
                        // the tab widens to reveal its label while actively
                        // pressed, not just when it's the selected tab, and
                        // animates back with the same spring rather than
                        // snapping.
                        val tabInteractionSource = remember { MutableInteractionSource() }
                        val isPressed by tabInteractionSource.collectIsPressedAsState()
                        val expanded = isSelected || isPressed
                        // User-requested: "plus espacé et un peu plus gros",
                        // then "les marques page doivent être un peu plus
                        // rentré lorspas sélectionné" - the resting
                        // (unselected/unpressed) width was pulled back in
                        // further (80dp -> 64dp) so tabs read as tucked into
                        // the edge at rest, only sticking out fully once
                        // selected or pressed.
                        val tabWidth by animateDpAsState(if (expanded) 104.dp else 64.dp, label = "notebookTabWidth")
                        val tabShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)
                        // User-requested: "espacement aléatoire pour les
                        // marques page, comme un vrai notebook" - each tab's
                        // own top spacing rolled once (a real stack of index
                        // tabs isn't machine-uniform), on top of the
                        // Column's own 10dp spacedBy baseline.
                        val extraSpacing = remember { (0..8).random().dp }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = extraSpacing)
                                .height(64.dp)
                                .width(tabWidth)
                                // User-requested: "ajoutes les ombres pour
                                // les marque page" - each tab already had a
                                // flat elevation shadow; bumped slightly and
                                // left as-is otherwise (same tinted-shadow
                                // approach as every other card in this
                                // theme, not a special case).
                                .shadow(elevation = 4.dp, shape = tabShape)
                                .clip(tabShape)
                                // User-requested: "sensé être opaque pas
                                // transparent" - flat fully-opaque fill for
                                // every state (previously 0.65-0.95f alpha).
                                // Armed/replace-target feedback (previously
                                // carried by alpha) now uses a border
                                // instead, so it stays visible without
                                // reintroducing transparency.
                                .background(tabColor)
                                .then(
                                    if (isArmed) Modifier.border(2.dp, NotebookInk, tabShape)
                                    else if (isReplaceTarget) Modifier.border(1.dp, NotebookInk.copy(alpha = 0.4f), tabShape)
                                    else Modifier
                                )
                                .combinedClickable(
                                    interactionSource = tabInteractionSource,
                                    indication = LocalIndication.current,
                                    onClick = { navTabClickHandler(tab) },
                                    onLongClick = { navTabLongClickHandler(tab) },
                                )
                                .padding(start = Spacing.S),
                        ) {
                            Icon(tab.icon, stringResource(tab.labelRes), tint = NotebookInk, modifier = Modifier.size(IconSize.Nav))
                            if (expanded) {
                                Spacer(Modifier.width(Spacing.XS))
                                Text(
                                    stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall,
                                    color = NotebookInk, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            return@Box
        }
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
            // Replaces Material3's NavigationBar/NavigationBarItem with a plain Row
            // of custom items so long-press-to-arm can be layered on cleanly.
            //
            // User-reported: tab switching itself became slow/unresponsive/
            // misfiring. Two prior attempts hand-rolled the tap-vs-hold gesture
            // with a custom awaitEachGesture loop consuming every pointer event
            // manually - functionally repairable each time a new failure mode
            // showed up, but a plain tap had no ripple/press feedback at all
            // (nothing indicated a tap had registered until navigation actually
            // completed) and evidently still misfired in practice. Replaced with
            // Modifier.combinedClickable(onClick, onLongClick) - Compose's own
            // battle-tested primitive for exactly this case, with built-in ripple
            // feedback and correct touch-slop/timing handled by the framework
            // instead of hand-timed code. Trade-off: long-press duration is now
            // the platform's standard timeout (~500ms), not a custom 2s.
            val haptics = LocalHapticFeedback.current

            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hierarchy = backStack.value?.destination?.hierarchy
                navTabs.forEach { tab ->
                    val isSelected = hierarchy?.any { it.route == tab.route } == true
                    val isArmed = armedNavTab == tab
                    val isReplaceTarget = armedNavTab != null && !isArmed
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isArmed) Modifier.background(AccentCoral.copy(alpha = 0.14f))
                                else if (isReplaceTarget) Modifier.background(AccentCoral.copy(alpha = 0.06f))
                                else Modifier
                            )
                            // Keyed on navOrderCsv.value (not just tab.route): the
                            // click/long-click lambdas below close over navTabs, a
                            // plain recomputed val, not a State-backed read - a tab
                            // whose slot doesn't move in a swap would otherwise keep
                            // running with the stale pre-swap list captured before
                            // that swap.
                            .combinedClickable(
                                onClick = {
                                    val armed = armedNavTab
                                    if (armed != null) {
                                        if (armed != tab) {
                                            val fromIdx = navTabs.indexOf(armed)
                                            val toIdx = navTabs.indexOf(tab)
                                            val newOrder = navTabs.toMutableList()
                                            newOrder[fromIdx] = tab
                                            newOrder[toIdx] = armed
                                            shellViewModel.setNavTabOrder(serializeTopTabOrder(newOrder))
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        armedNavTab = null
                                    } else {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState    = true
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    armedNavTab = if (armedNavTab == tab) null else tab
                                },
                            ),
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
