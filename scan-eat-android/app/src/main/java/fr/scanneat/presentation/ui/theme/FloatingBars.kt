package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Real, RenderEffect-backed backdrop blur for the app's floating glass chrome
 * (real optical blur of whatever scrolls underneath, not just an alpha/
 * gradient approximation — see [glassSheen]'s own doc comment for why that
 * alone doesn't read as "frosted glass"). [backgroundColor] is the opaque
 * fallback Haze draws on API levels/devices where RenderEffect blur isn't
 * available (pre-Android 12); [tint] is the translucent colour composited
 * over the live blurred content everywhere else — that's what actually reads
 * as "glass" rather than a plain scrim. `noiseFactor = 0f` deliberately: the
 * library's default film-grain dithering risks reading as the same dot/grain
 * texture this rework was asked to remove, so it's off.
 */
val FrostedGlassStyle: HazeStyle
    @Composable get() = HazeStyle(
        backgroundColor = SurfaceVariant,
        tint            = HazeTint(SurfaceVariant.copy(alpha = 0.38f)),
        blurRadius      = 12.dp,
        noiseFactor     = 0f,
    )

/**
 * Shared [HazeState] for MainShell's bottom nav: created once in MainShell
 * and provided across the AppNavGraph boundary via [androidx.compose.runtime.CompositionLocalProvider],
 * so every top-tab screen's own [FloatingScreenScaffold] (a different
 * composable subtree than MainShell's persistent nav) can register its
 * scrolling content as that same nav's blur source. The no-arg default here
 * only matters for previews/tests that never wire the real provider.
 */
val LocalBottomNavHazeState = compositionLocalOf { HazeState() }

/**
 * The app-wide "floating header" — a detached, glassy pill instead of the
 * previous edge-to-edge TopAppBar, matching the floating treatment the
 * bottom nav (MainShell.kt) now uses, so both pieces of chrome read as one
 * matched pair rather than two different systems. Same title/navigationIcon/
 * actions slots Material3's TopAppBar already exposes, so existing call
 * sites swap in directly. `colors` is intentionally dropped — the old
 * `containerColor = Background` trick existed only to make an edge-to-edge
 * bar blend invisibly into the screen, the opposite of what a floating card
 * should do. Handles its own status-bar inset (TopAppBar did this internally
 * too) since it no longer delegates to TopAppBar for layout.
 *
 * [accent] mirrors ScanEatCard's own param of the same name — defaults to
 * White (glassSheen's own default, so every existing call site is
 * unaffected) but lets a section with its own brand hue (Biolism's Gold)
 * tint the glow instead of hand-rolling this whole component a second time.
 *
 * [hazeState] is the blur source registered by the screen's own scrolling
 * content (see [FloatingScreenScaffold]) — the Surface below draws a real
 * backdrop blur of whatever's passing underneath it via [FrostedGlassStyle],
 * with [glassSheen]'s gradient/edge-highlight layered on top for the "light
 * catching a glass edge" finish.
 */
@Composable
fun FloatingTopBar(
    title: @Composable () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    accent: Color = Color.White,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.L, vertical = Spacing.S)
            .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = accent),
    ) {
        Surface(
            shape           = RoundedCornerShape(CardRadius.PROMINENT),
            color           = Color.Transparent,
            shadowElevation = 8.dp,
            modifier        = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle),
        ) {
            Row(
                modifier          = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fixed-width leading slot whether or not navigationIcon is
                // empty - matches TopAppBar's own behaviour (a tab-root
                // screen with no back arrow still reserves the same leading
                // space, e.g. DiaryScreen's isTabRoot case), so swapping in
                // doesn't shift any title that previously relied on it.
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
                Box(Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

/** FloatingTopBar's own pill height (56dp title row + Spacing.S margin top/bottom) — not including the device's own status-bar inset, which [FloatingScreenScaffold] adds separately. */
val FloatingTopBarHeight = 72.dp

/** MainShell's floating bottom nav's own pill height (64dp NavigationBar + Spacing.S margin top/bottom) — not including the device's own navigation-bar inset. */
val FloatingBottomNavHeight = 80.dp

/**
 * Wraps a screen's content in the app's floating-chrome layout: a full-bleed
 * [Box] with [content] filling the whole frame and [FloatingTopBar] overlaid
 * on top of it (not a `Scaffold` slot that pads content away from the bar),
 * so a scrolled list passes underneath the header's own translucent
 * glassSheen() instead of stopping short of it. Replaces the previous
 * `Scaffold(topBar = { FloatingTopBar(...) }) { padding -> ... }` pattern —
 * [content] still receives a [PaddingValues] to feed straight into a
 * LazyColumn/Column's own `contentPadding`, exactly like Scaffold's content
 * lambda did, just sized to clear the floating header (and, for a top-level
 * tab screen sitting above MainShell's own floating bottom nav, the bottom
 * chrome too) instead of the old Scaffold-consumed inset.
 *
 * [showBottomNavClearance] should be true only for the handful of screens
 * that are direct destinations of MainShell's bottom nav (Dashboard, Diary,
 * etc. — anything not in `HIDDEN_NAV_ROUTES`); every push-navigation
 * sub-screen reached on top of them already hides that nav bar, so reserving
 * space for it there would just leave a dead gap at the bottom.
 */
@Composable
fun FloatingScreenScaffold(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    accent: Color = Color.White,
    showBottomNavClearance: Boolean = false,
    // Mirrors Scaffold's own snackbarHost slot — a handful of screens show a
    // SnackbarHost here (e.g. undo-delete, action-failed toasts); rendered as
    // its own bottom-center overlay so callers don't each have to re-solve
    // "where does this float now that there's no Scaffold" individually.
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val headerHazeState = remember { HazeState() }
    val bottomNavHazeState = LocalBottomNavHazeState.current
    Box(modifier.fillMaxSize()) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        // Reserved unconditionally, mirroring topInset above - showBottomNavClearance
        // only gates the app's OWN FloatingBottomNavHeight pill. Previously the raw
        // system nav-bar inset was never reserved at all on push/detail screens (the
        // majority of screens, not just bottom-nav-tab destinations), so content and
        // bottom-anchored buttons on those screens could render underneath a gesture/
        // 3-button navigation bar.
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Nested so each hazeSource attaches to its own layout node — the
        // outer one only exists (and only feeds MainShell's shared nav
        // state) on genuine bottom-nav-tab screens.
        Box(
            if (showBottomNavClearance) Modifier.fillMaxSize().hazeSource(bottomNavHazeState) else Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().hazeSource(headerHazeState)) {
                content(
                    PaddingValues(
                        top    = topInset + FloatingTopBarHeight,
                        bottom = bottomInset + if (showBottomNavClearance) FloatingBottomNavHeight else 0.dp,
                    ),
                )
            }
        }
        FloatingTopBar(
            title          = title,
            hazeState      = headerHazeState,
            navigationIcon = navigationIcon,
            actions        = actions,
            accent         = accent,
            modifier       = Modifier.align(Alignment.TopCenter),
        )
        // Previously omitted "+ bottomInset" here even though content's own
        // calculation above includes it - a Snackbar on a bottom-nav-tab screen
        // with a gesture nav bar rendered bottomInset pixels too low, appearing to
        // sit underneath/behind the floating bottom nav instead of above it.
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset + if (showBottomNavClearance) FloatingBottomNavHeight else 0.dp)) {
            snackbarHost()
        }
    }
}
