package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 */
@Composable
fun FloatingTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.L, vertical = Spacing.S)
            .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT), grainDensity = 30),
    ) {
        Surface(
            shape           = RoundedCornerShape(CardRadius.PROMINENT),
            color           = SurfaceVariant.copy(alpha = 0.92f),
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
