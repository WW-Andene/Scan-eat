package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
 */
@Composable
fun FloatingTopBar(
    title: @Composable () -> Unit,
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
            color           = SurfaceVariant.copy(alpha = 0.7f),
            shadowElevation = 8.dp,
            modifier        = Modifier.fillMaxWidth(),
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
    Box(modifier.fillMaxSize()) {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = if (showBottomNavClearance) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
        content(
            PaddingValues(
                top    = topInset + FloatingTopBarHeight,
                bottom = if (showBottomNavClearance) bottomInset + FloatingBottomNavHeight else 0.dp,
            ),
        )
        FloatingTopBar(
            title          = title,
            navigationIcon = navigationIcon,
            actions        = actions,
            accent         = accent,
            modifier       = Modifier.align(Alignment.TopCenter),
        )
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (showBottomNavClearance) FloatingBottomNavHeight else 0.dp)) {
            snackbarHost()
        }
    }
}
