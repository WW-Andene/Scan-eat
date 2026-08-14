package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * User-reported: `ScanEatDropdownMenu`'s popup width was decoupled from its
 * trigger button's width (wrap-content Column), so the popup could render
 * narrower or wider than the button that opened it, breaking the visual
 * alignment expected of a menu anchored directly under its trigger. Each of
 * the 11 call sites wraps its own trigger Surface/button, so this helper is
 * attached to that trigger via `Modifier.then(...)` to measure its width in
 * px and expose it as Dp, ready to pass into `ScanEatDropdownMenu`'s
 * `anchorWidth` parameter - one shared implementation instead of duplicating
 * the `onSizeChanged` + `LocalDensity` conversion boilerplate 11 times.
 */
@Composable
fun rememberTrackedWidth(): Pair<Dp, Modifier> {
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0) }
    val modifier = Modifier.onSizeChanged { widthPx = it.width }
    val widthDp = with(density) { widthPx.toDp() }
    return widthDp to modifier
}

/**
 * User-reported: Material3's `DropdownMenu` flips ABOVE its trigger whenever
 * its own built-in `DropdownMenuPositionProvider` judges there isn't enough
 * room below - not overridable via a public parameter in this project's
 * Material3 version. Reported on Recherche's "Filtres" pill: depending on
 * scroll position, the popup rendered over the top app bar/search field
 * instead of under the button that opened it. Every one of this app's 11
 * `DropdownMenu` call sites shared the exact same shape/color/elevation/
 * glass-surface/offset boilerplate, so this is a drop-in replacement for all
 * of them, not just the reported one - a plain `Popup` with a position
 * provider that always anchors below-left of the trigger and never flips,
 * with the content itself height-capped and internally scrollable (same
 * ~288dp-class cap Material3's own DropdownMenu defaults to) so a long menu
 * opened near the bottom of the screen stays fully below its button and
 * simply scrolls, rather than moving anywhere else to "fit".
 */
private class AlwaysBelowPositionProvider(private val verticalGapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Deliberately no upward clamp/flip - x/y always below-left of the
        // anchor. Horizontal clamp only, so a menu near the right edge
        // doesn't render partly off-screen sideways (an unrelated axis from
        // the reported "renders above" issue, but the same "never go where
        // it doesn't fit visually" principle).
        val x = anchorBounds.left.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = anchorBounds.bottom + verticalGapPx
        return IntOffset(x, y)
    }
}

private val MAX_MENU_HEIGHT: Dp = 384.dp

@Composable
fun ScanEatDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val gapPx = with(density) { DROPDOWN_MENU_GAP.roundToPx() }
    Popup(
        popupPositionProvider = AlwaysBelowPositionProvider(gapPx),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(CardRadius.CONTROL),
            color = SurfaceVariant.copy(alpha = StandardCardAlpha),
            shadowElevation = 0.dp,
            modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)).widthIn(min = anchorWidth),
        ) {
            Column(Modifier.heightIn(max = MAX_MENU_HEIGHT).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}
