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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
 * available (pre-Android 12, and several MIUI/Xiaomi builds that disable the
 * RenderEffect blur APIs on newer Android versions too); [tint] is the
 * translucent colour composited over the live blurred content everywhere
 * else — that's what actually reads as "glass" rather than a plain scrim.
 * `noiseFactor = 0f` deliberately: the library's default film-grain dithering
 * risks reading as the same dot/grain texture this rework was asked to
 * remove, so it's off.
 *
 * backgroundColor was plain [SurfaceVariant] - on the OLED theme that's
 * #241F29 sitting directly on a pure-black (#000000) [Background], so any
 * device that falls back to this opaque fill (confirmed via a real MIUI
 * device's screenshot) rendered it as a flat, hard-edged, visibly lighter
 * rectangle with no blend into the screen at all - reading exactly as "an
 * ugly floating text box," not glass. Two earlier passes tried blending it
 * (65/35, then 88/12) toward [Background] instead of eliminating the delta
 * outright, but a third screenshot from the same device (header AND bottom
 * nav both still a clearly visible lighter box, this time with scrolled
 * content - an OutlinedTextField's own outline/label - bleeding straight
 * through the now near-transparent tint) confirmed any non-zero blend still
 * reads as a rectangle against pure black, and that thinning the tint
 * further just trades "visible box" for "visible content ghosting through
 * the box." backgroundColor is now exactly [Background] (zero blend) so the
 * no-blur fallback is truly invisible against the screen behind it, and the
 * live tint is bumped back up enough to actually obscure scrolled content
 * instead of merely dimming it - [glassSheen]'s edge highlight remains the
 * only thing marking the chrome's outline on every device.
 */
/**
 * Shared outer margin for both floating chrome pieces (FloatingTopBar here,
 * MainShell's bottom nav) — user-reported ratio of 1(sides):2(top for the
 * header/bottom for the nav). horizontal is unchanged from the original
 * Spacing.L both bars already used - only vertical (top for the header,
 * bottom for the nav) changes, to 2x that same value, and is now shared so
 * both call sites reference the same number instead of the two independent
 * literals they used before, which is also what let the footer's bottom
 * margin drift out of sync with the header's top margin.
 */
object FloatingChromeMargin {
    val horizontal: Dp = Spacing.L
    val vertical: Dp = Spacing.L * 2
}

val FrostedGlassStyle: HazeStyle
    @Composable get() = run {
        // design-aesthetic-audit: the OLED fix above only verified the no-blur
        // fallback path against a near-black Background - Light theme has the same
        // underlying problem this doc already describes for OLED, just inverted:
        // SurfaceVariant (#F0E7E0) sits only ~1-3 RGB units from Background
        // (#F6F1EC), so a 0.55-alpha tint barely darkens the live-blurred content
        // it's compositing over, leaving glassSheen's hairline + the Surface's own
        // shadowElevation shadow (both of which DO have real contrast in Light
        // theme) as the only visible shape - a disconnected rectangle instead of a
        // whole glass pill.
        val tintAlpha = if (isLightBackground()) 0.82f else 0.55f
        HazeStyle(
            backgroundColor = Background,
            tint            = HazeTint(SurfaceVariant.copy(alpha = tintAlpha)),
            blurRadius      = 16.dp,
            noiseFactor     = 0f,
        )
    }

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
    hasNavigationIcon: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    accent: Color = Color.White,
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            // User-reported: outer margin ratio should be 1(sides):2(top/bottom),
            // and match MainShell's bottom nav margin exactly so the header's top
            // gap and the nav's bottom gap read as the same size.
            .padding(horizontal = FloatingChromeMargin.horizontal, vertical = FloatingChromeMargin.vertical)
            .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = accent),
    ) {
        Surface(
            shape           = RoundedCornerShape(CardRadius.PROMINENT),
            color           = Color.Transparent,
            // F16 (docs/design-audit-step6-color-atmosphere.md): shadow tinted warm
            // instead of Compose's neutral default, matching ScanEatCard's own fix —
            // Surface's own shadowElevation stays 0 so the two don't stack.
            shadowElevation = 0.dp,
            modifier        = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(CardRadius.PROMINENT), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.PROMINENT))
                .hazeEffect(state = hazeState, style = FrostedGlassStyle),
        ) {
            Row(
                // User-reported: on tab-root screens (no back arrow), the leading
                // side previously got Spacing.XS (icon-slot case) or an
                // approximated Spacing.M spacer (~15dp, not an exact match to the
                // content below's Spacing.L inset). The leading side is now
                // Spacing.XS when there's a real back-arrow icon (unchanged from
                // before, for the 16 push/detail screens that always show one),
                // or 0 when there isn't, so the no-icon branch below can set the
                // leading inset to an exact value instead of stacking a second,
                // redundant padding source on top of it. The trailing side always
                // keeps Spacing.XS (unchanged breathing room before actions).
                modifier          = Modifier.fillMaxWidth().height(56.dp).padding(start = if (hasNavigationIcon) Spacing.XS else 0.dp, end = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasNavigationIcon) {
                    // Fixed-width leading slot for a real back arrow - matches
                    // TouchTarget/IconButton's own footprint.
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
                } else {
                    // No icon to show: the leading inset is exactly Spacing.L,
                    // matching the content below's own outer Spacing.L margin
                    // pixel-for-pixel instead of approximating it.
                    Spacer(Modifier.width(Spacing.L))
                }
                Box(Modifier.weight(1f)) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

/** FloatingTopBar's own pill height (56dp title row + FloatingChromeMargin.vertical top/bottom) — not including the device's own status-bar inset, which [FloatingScreenScaffold] adds separately. */
val FloatingTopBarHeight = 56.dp + FloatingChromeMargin.vertical * 2

/** MainShell's floating bottom nav's own pill height (64dp NavigationBar + FloatingChromeMargin.vertical top/bottom) — not including the device's own navigation-bar inset. */
val FloatingBottomNavHeight = 64.dp + FloatingChromeMargin.vertical * 2

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
    hasNavigationIcon: Boolean = true,
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
            title             = title,
            hazeState         = headerHazeState,
            navigationIcon    = navigationIcon,
            hasNavigationIcon = hasNavigationIcon,
            actions           = actions,
            accent            = accent,
            modifier          = Modifier.align(Alignment.TopCenter),
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
