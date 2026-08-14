package fr.scanneat.presentation.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt

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

private val MAX_MENU_HEIGHT: Dp = 384.dp

/**
 * User-instructed, literal: "all popup menu in the app should use the same
 * style as Tableau header." A Compose `Popup` (this component's previous
 * implementation) renders in its own separate Android window, and Haze's
 * blur source is scoped to the originating window's own GraphicsLayer - a
 * documented, structural limitation, not a per-device gap - so real backdrop
 * blur (`hazeEffect`) can never reach content inside a `Popup`. The only way
 * to give this menu the SAME real-time blur FloatingTopBar/MainShell's nav
 * use is to stop rendering via `Popup` and instead push the menu's content
 * into [LocalPopupOverlay] - a same-window overlay MainShell renders above
 * everything else (see [PopupOverlayController]'s own doc comment) - so it
 * shares the exact same window, and therefore the exact same blur source
 * ([LocalBottomNavHazeState]), the header/footer chrome already uses.
 *
 * Anchor position: a zero-size probe sits in the same layout slot the
 * trigger occupies (every one of the 11 call sites places this composable as
 * a sibling of its own trigger inside a shared `Box`, which stacks children
 * at the same top-start origin by default) - [LayoutCoordinates.positionInRoot]
 * from that probe lands in MainShell's own root coordinate space, the one
 * space shared by every screen, so the menu positions correctly under its
 * trigger regardless of which screen's tree this call lives in - the same
 * anchor semantics the previous `Popup`'s own `anchorBounds` provided.
 *
 * Always-below, never-flips-above and the horizontal screen-edge clamp
 * (previously a `PopupPositionProvider`, now inlined into [PopupOverlayMenu]'s
 * own offset calculation below) are unchanged from the `Popup`-based
 * implementation, as is the outside-tap-to-
 * dismiss and system back-press dismiss behavior (`Popup`'s own
 * `PopupProperties(focusable = true)` handled both automatically; replicated
 * here with an explicit scrim + [BackHandler]).
 */
@Composable
fun BoxScope.ScanEatDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorWidth: Dp = Dp.Unspecified,
    // User-instructed: every popup's width should match its trigger's width
    // exactly - except Journal's own "Plus" tab-overflow menu (DiaryHeader.kt),
    // whose items are the other tab labels (e.g. "Dépenses", "Traitement"),
    // routinely wider than the compact icon-only "Plus" trigger itself -
    // clamping to that trigger's width would truncate/wrap every label.
    matchAnchorWidth: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val overlay = LocalPopupOverlay.current
    val hazeState = LocalBottomNavHazeState.current
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // matchParentSize(), not a zero-size probe: anchorSizePx (used below in
    // PopupOverlayMenu to offset the popup below the trigger's own bottom
    // edge) must equal the trigger's real height, not 0 - a same-size probe
    // over the trigger (the trigger itself, the Box's other/first child,
    // still defines the shared Box's own size) reports that real height,
    // instead of collapsing the "below trigger + gap" offset down to
    // "trigger's top-left + gap" (visually flush against the trigger, not
    // below it, when the trigger has any height at all).
    Box(Modifier.matchParentSize().onGloballyPositioned { anchorCoordinates = it })

    BackHandler(enabled = expanded) { onDismissRequest() }

    SideEffect {
        if (overlay == null) return@SideEffect
        if (expanded) {
            overlay.show {
                PopupOverlayMenu(
                    anchorCoordinates = anchorCoordinates,
                    anchorWidth = anchorWidth,
                    matchAnchorWidth = matchAnchorWidth,
                    hazeState = hazeState,
                    onDismissRequest = onDismissRequest,
                    content = content,
                )
            }
        } else {
            overlay.hide()
        }
    }
    // Leaving composition entirely (screen navigated away while the menu was
    // still open) previously dismissed the Popup automatically along with
    // its parent; the overlay's content instead lives in MainShell, one
    // level above every screen, so it needs this explicit cleanup instead.
    DisposableEffect(Unit) {
        onDispose { overlay?.hide() }
    }
}

@Composable
private fun PopupOverlayMenu(
    anchorCoordinates: LayoutCoordinates?,
    anchorWidth: Dp,
    matchAnchorWidth: Boolean,
    hazeState: HazeState,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val coords = anchorCoordinates ?: return
    if (!coords.isAttached) return
    val density = LocalDensity.current
    val gapPx = with(density) { DROPDOWN_MENU_GAP.roundToPx() }
    val anchorPositionPx = coords.positionInRoot()
    val anchorSizePx = coords.size
    val shape = RoundedCornerShape(CardRadius.CONTROL)

    Box(Modifier.fillMaxSize()) {
        // Scrim - invisible, catches an outside tap to dismiss, same as
        // Popup's own PopupProperties(focusable = true) behavior.
        Box(
            Modifier.fillMaxSize()
                .pointerInput(onDismissRequest) { detectTapGestures(onTap = { onDismissRequest() }) },
        )
        Surface(
            shape = shape,
            color = SurfaceVariant.copy(alpha = StandardCardAlpha),
            shadowElevation = 0.dp,
            modifier = Modifier
                .offset {
                    // Deliberately no upward clamp/flip - always below-left of the
                    // anchor. Horizontal clamp only, so a menu near the right edge
                    // doesn't render partly off-screen sideways.
                    val windowWidth = coords.findRootCoordinates().size.width
                    IntOffset(
                        anchorPositionPx.x.roundToInt(),
                        (anchorPositionPx.y + anchorSizePx.height + gapPx).roundToInt(),
                    ).let { IntOffset(it.x.coerceAtMost(windowWidth), it.y) }
                }
                .hazeEffect(state = hazeState, style = FrostedGlassStyle)
                .glassSheen(edgeAlpha = 0.28f, shape = shape)
                // User-instructed: the popup's width should match its trigger
                // button's width exactly, not just a floor it can grow past -
                // widthIn(min=) let a menu with wider text (e.g. a long label)
                // grow past its trigger, no longer aligned edge-to-edge.
                .then(
                    when {
                        anchorWidth == Dp.Unspecified -> Modifier
                        matchAnchorWidth -> Modifier.width(anchorWidth)
                        else -> Modifier.widthIn(min = anchorWidth)
                    }
                )
                // Consumes taps landing on the menu's own padding/background
                // (not on a specific item) so they don't fall through to the
                // scrim behind it and dismiss the menu unintentionally.
                .pointerInput(Unit) { detectTapGestures {} },
        ) {
            Column(Modifier.heightIn(max = MAX_MENU_HEIGHT).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}
