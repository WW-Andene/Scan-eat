package fr.scanneat.presentation.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
    // User-instructed: popup menus (ScanEatDropdownMenu) get real backdrop
    // blur - the same FrostedGlassStyle/hazeEffect treatment the header/
    // footer use - by rendering into this same-window overlay instead of a
    // separate-window Popup (see PopupOverlayController's own doc comment
    // for why Popup can't share a blur source). Reuses bottomNavHazeState as
    // the blur source itself - the popup is blurring the exact same on-
    // screen content the bottom nav already does, so a second HazeState
    // would just be a second consumer of identical content.
    val popupOverlay = remember { PopupOverlayController() }

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
    // Adaptive layout (F29). Width classes follow Material's window size
    // breakpoints, expressed as sums of distinct scale tokens: 600dp =
    // 512 + 64 + 24 (medium), 840dp = 512 + 256 + 64 + 8 (expanded).
    BoxWithConstraints(Modifier.fillMaxSize().background(Background)) {
        val isMediumOrWider = maxWidth >= 512.dp + 64.dp + 24.dp
        val isExpanded = maxWidth >= 512.dp + 256.dp + 64.dp + 8.dp
        val useRail = isExpanded && showNav
        val haptics = LocalHapticFeedback.current
        val hierarchy = backStack.value?.destination?.hierarchy

        // Shared by the bottom bar and the side rail so both keep identical
        // tap / long-press-to-arm-and-swap behaviour.
        @Composable
        fun NavTabItem(tab: TopTab, modifier: Modifier) {
            val isSelected = hierarchy?.any { it.route == tab.route } == true
            val isArmed = armedNavTab == tab
            val isReplaceTarget = armedNavTab != null && !isArmed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier
                    .then(
                        if (isArmed) Modifier.background(AccentCoral.copy(alpha = 0.14f))
                        else if (isReplaceTarget) Modifier.background(AccentCoral.copy(alpha = 0.06f))
                        else Modifier
                    )
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
                                // collapseFirst = true: a nav tap can happen from any
                                // depth, so the tab's own back stack is collapsed to its
                                // root before restoreState brings it back.
                                navController.switchToTab(tab.route, collapseFirst = true)
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
                Spacer(Modifier.height(Spacing.XS))
                Text(
                    stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) AccentCoral else IconInactive,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val navShape = RoundedCornerShape(CardRadius.PROMINENT)
        // Gated on rememberReducedMotion(), like every other prominent animation.
        val reduceMotion = rememberReducedMotion()

        Row(Modifier.fillMaxSize()) {
            // Expanded widths (tablets, unfolded foldables, landscape large
            // phones): navigation moves to a floating side rail so content
            // keeps its full height instead of sitting above a wide, sparse
            // bottom bar.
            if (useRail) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))
                        .padding(start = FloatingChromeMargin.horizontal, top = Spacing.L, bottom = Spacing.L)
                        .width(96.dp)
                        .shadow(elevation = 8.dp, shape = navShape)
                        .clip(navShape)
                        .hazeEffect(state = bottomNavHazeState, style = FrostedGlassStyle)
                        .glassSheen(edgeAlpha = 0.28f, shape = navShape),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterVertically),
                ) {
                    navTabs.forEach { tab -> NavTabItem(tab, Modifier.fillMaxWidth().height(64.dp)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                CompositionLocalProvider(
                    LocalBottomNavHeight provides if (useRail) 0.dp else BottomNavPillTotalHeight,
                    LocalBottomNavHazeState provides bottomNavHazeState,
                    LocalPopupOverlay provides popupOverlay,
                ) {
                    AppNavGraph(
                        navController    = navController,
                        startDestination = when {
                            startOnboarding    -> AppRoutes.ONBOARDING
                            startRoute != null -> startRoute
                            else               -> TopTab.Dashboard.route
                        },
                        // Readable-width cap on medium+ widths: list/card screens
                        // were stretched edge-to-edge across a whole tablet.
                        // Chrome-less routes (scan camera, onboarding) stay full-bleed.
                        modifier = if (isMediumOrWider && showNav) Modifier.fillMaxHeight().widthIn(max = 512.dp + 256.dp + 64.dp + 8.dp).fillMaxWidth()
                                   else Modifier.fillMaxSize(),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible  = showNav && !useRail,
            enter    = if (reduceMotion) EnterTransition.None else fadeIn(),
            exit     = if (reduceMotion) ExitTransition.None else fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Floating, detached, glassy bottom nav - a single content Row with
            // one modifier chain (one object, one set of bounds). On medium
            // widths it's capped so five tabs don't spread across a tablet.
            Row(
                modifier = Modifier
                    .then(if (isMediumOrWider) Modifier.widthIn(max = 512.dp + 64.dp) else Modifier.fillMaxWidth())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = FloatingChromeMargin.horizontal, vertical = FloatingChromeMargin.vertical)
                    .shadow(elevation = 8.dp, shape = navShape)
                    .clip(navShape)
                    .hazeEffect(state = bottomNavHazeState, style = FrostedGlassStyle)
                    .glassSheen(edgeAlpha = 0.28f, shape = navShape)
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navTabs.forEach { tab -> NavTabItem(tab, Modifier.weight(1f).fillMaxHeight()) }
            }
        }

        // Rendered last (highest z-order) so an open popup menu always sits
        // above both the screen content and the floating chrome - see
        // PopupOverlayController's own doc comment.
        popupOverlay.content?.invoke()
    }
}
