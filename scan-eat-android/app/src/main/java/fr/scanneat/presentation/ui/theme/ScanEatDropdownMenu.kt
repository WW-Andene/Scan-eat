package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
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

private val MAX_MENU_HEIGHT: Dp = 320.dp

/**
 * User-reported: popups used to grow as wide as their widest menu item's text
 * (up to Material3 DropdownMenuItem's own 280dp internal max), reading as
 * "too wide" and no longer visually tied to the small trigger button that
 * opened them. User-requested (round 2): not just narrower - the exact same
 * width as the trigger button, not an app-wide fixed cap. [anchorWidth] is
 * the trigger's own measured width (see [Modifier.reportWidthTo]) and is
 * applied to the popup's Column verbatim.
 */
@Composable
fun ScanEatDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorWidth: Dp,
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
        // Verification pass: this shared popup surface (every DropdownMenu in
        // the app) had no Prism branch at all, unlike every other piece of
        // card-style chrome (ScanEatCard/BioCard/FloatingTopBar/MainShell's
        // nav/DiaryHeader/dialogs).
        val isPrism = LocalThemeName.current == "prism"
        Surface(
            shape = RoundedCornerShape(CardRadius.CONTROL),
            color = if (isPrism) PrismFillColor else SurfaceVariant.copy(alpha = StandardCardAlpha),
            shadowElevation = 0.dp,
            modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.CONTROL)),
        ) {
            Column(Modifier.width(anchorWidth).heightIn(max = MAX_MENU_HEIGHT).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

/**
 * Measures this composable's own laid-out width and reports it (in Dp) to
 * [onWidth] - paired with a trigger button so [ScanEatDropdownMenu]'s
 * `anchorWidth` can be set to "whatever width this button ended up being"
 * without every call site hand-rolling its own onGloballyPositioned/density
 * conversion.
 */
@Composable
fun Modifier.reportWidthTo(onWidth: (Dp) -> Unit): Modifier {
    val density = LocalDensity.current
    return this.then(
        Modifier.onGloballyPositioned { coordinates ->
            onWidth(with(density) { coordinates.size.width.toDp() })
        }
    )
}
