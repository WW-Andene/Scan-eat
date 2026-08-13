package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How strongly a card should announce itself relative to its neighbors —
 * the "hiérarchisation" from the app-wide polish pass: previously every card
 * on a screen got the exact same glassSheen()/border treatment regardless of
 * whether it was the one number a screen exists to show (Dashboard's calorie
 * balance, Result's score) or a minor supporting stat three scrolls down.
 *  - HERO: the single focal element on a screen — stronger glow and a
 *    visible tinted border. Use for at most one element per screen; using
 *    it everywhere defeats the point of a hierarchy.
 *  - PRIMARY: the default — matches this card primitive's original look
 *    (same edge/glow strength ScanEatCard already shipped with).
 *  - SECONDARY: supporting/minor content — quieter still.
 */
enum class CardEmphasis { HERO, PRIMARY, SECONDARY }

/**
 * User-requested: one standard glass config for every card/panel/popup in the
 * app (header/nav are the one deliberate exception - they use a real
 * backdrop blur via Haze, see FrostedGlassStyle in FloatingBars.kt, not a
 * static fill). Previously ScanEatCard's own default (~0.22-0.28 dark, tuned
 * for a translucent card floating over the screen's ambientGloom wash) and
 * every popup/dialog's own alpha (0.85-0.94, tuned for a solid, readable
 * modal) drifted independently - GlassAlertDialog wrapped ScanEatCard
 * directly and inherited its low alpha, which is what read as "too
 * transparent, not standard" for a popup. One shared constant now backs
 * both: ScanEatCard's own [ScanEatCard.color] default below, and every
 * dialog built on [GlassAlertDialog].
 */
val StandardCardAlpha: Float @Composable get() = if (isLightBackground()) 0.9f else 0.85f

/**
 * Higher-opacity variant for dialogs that render directly over the live
 * camera preview (ScanStateOverlay.kt's MedicationFound/NonConsumableFound
 * AlertDialogs) rather than over the app's own static background. Those
 * dialogs are AlertDialogs, which Compose renders in their own Android
 * PopupWindow - a separate window from the camera-preview surface behind
 * it, so [glassPopupSurface]'s real-time backdrop blur can't reach it (see
 * that function's own doc comment for why). [StandardCardAlpha] was tuned
 * assuming the blur would soften whatever's visible underneath; without it,
 * that alpha over a busy, high-contrast camera feed left the dialog's own
 * text unreadable - user-reported. Near-opaque instead, since translucency
 * with no blur just means "camera image showing through," not "glass."
 */
val CameraOverlayDialogAlpha: Float @Composable get() = if (isLightBackground()) 0.97f else 0.96f

/**
 * Shared Prism-theme glass fill/border, used by every piece of "card-style"
 * chrome in the app (ScanEatCard, FeatureTile, FloatingTopBar, MainShell's
 * bottom nav - user-requested: "utilise ce style de carte... partout").
 * One constant instead of each call site picking its own alpha, so a future
 * "make it more visible" request only needs to change it here once.
 */
val PrismFillColor: Color get() = Color.White.copy(alpha = 0.25f)
val PrismBorderAlpha: Float = 0.28f

// internal (not private) so a card that can't use ScanEatCard directly - e.g.
// CalorieBalanceCard, which overlays a streak badge on the outer Box via
// BoxScope.align, a slot ScanEatCard's content: ColumnScope.() -> Unit
// doesn't expose - can still render at the HERO tier without re-declaring
// (and risking drifting from) these same numbers as separate literals.
internal data class GlassSpec(val glowAlpha: Float, val edgeAlpha: Float, val elevation: Dp)
internal val HeroGlassSpec      = GlassSpec(glowAlpha = 0.12f, edgeAlpha = 0.34f, elevation = 10.dp)
private val PrimaryGlassSpec   = GlassSpec(glowAlpha = 0.06f, edgeAlpha = 0.16f, elevation = 6.dp)
private val SecondaryGlassSpec = GlassSpec(glowAlpha = 0.03f, edgeAlpha = 0.10f, elevation = 3.dp)

/**
 * The app's one card primitive — a translucent fill with a hairline top-edge
 * highlight, 16dp corners by default. Generalizes the pattern BioCard()
 * already proved out, so a hand-rolled `Surface(...)` doesn't need to be
 * re-derived (and its treatment/radius drifted) on every new screen.
 *
 * Rebuilt from scratch twice already (user-reported both times: a stray
 * unclipped rectangle - a plain transparent/colorless "box" - stayed visible
 * inside the rounded card). First attempt used a raw `Modifier.shadow`
 * chain; second attempt swapped to Material3's `Surface`, assuming its
 * `shadowElevation` used a different, cleaner path - it doesn't:
 * `Surface`'s own internal implementation applies the exact same
 * `Modifier.shadow(clip = false)` + separate `.clip()` construction this
 * file was already trying to move away from, so nothing actually changed.
 * The elevation-shadow API itself (raw or via Surface) is what keeps
 * producing a second compositing layer with its own bounds, independent
 * of the visible clipped shape underneath it. This version drops elevation
 * shadow entirely: a single [Modifier.clip] + [Modifier.background] +
 * [Modifier.border] chain on the one content [Column], nothing else - one
 * shape, one clip, no separate shadow layer for anything to mismatch against. The
 * hairline top-edge highlight is drawn in the same [drawWithCache] pass,
 * inside that one already-clipped node.
 *
 * Frosted-glass + hierarchy upgrade (app-wide polish pass):
 *  - [color] defaults to a translucent fill so a screen's own ambient
 *    background wash (see [ambientGloom]) bleeds through — this is what
 *    reads as "frosted glass over an atmosphere" rather than a flat tinted
 *    rectangle. Existing call sites that pass an explicit [color] are
 *    unaffected.
 *  - [emphasis]/[accent] pick which [CardEmphasis] tier this card renders at
 *    and which hue its glow/border echo — default (PRIMARY, white accent)
 *    reproduces this primitive's original look.
 *  - [onClick], when non-null, makes the card tappable and applies
 *    [pressScale] alongside the tap ripple — both share one interaction
 *    source, which a caller-supplied `Modifier.clickable` on [modifier]
 *    couldn't give pressScale access to (it owns the ripple internally).
 *    A caller that needs a tappable card should use this instead of adding
 *    its own `Modifier.clickable`.
 */
@Composable
fun ScanEatCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius.CARD),
    color: Color = SurfaceVariant.copy(alpha = StandardCardAlpha),
    contentPadding: PaddingValues = PaddingValues(Spacing.L),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    emphasis: CardEmphasis = CardEmphasis.PRIMARY,
    accent: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = when (emphasis) {
        CardEmphasis.HERO      -> HeroGlassSpec
        CardEmphasis.PRIMARY   -> PrimaryGlassSpec
        CardEmphasis.SECONDARY -> SecondaryGlassSpec
    }
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    // User-reported: "pourquoi les carte ne sont pas transparentes" (Prism
    // theme) - the whole point of that theme's full-bleed background image
    // (ambientGloom's isPrism branch, Glass.kt) is for it to show through;
    // every other theme keeps its own considered [color] fill. User-reported
    // round 2: fully Color.Transparent, paired with the border's already-low
    // 0.14 alpha, left the card with no visible fill or edge at all against
    // Prism's busy polygon artwork - a card that reads as "nothing here"
    // instead of "a card." A faint frosted-glass tint (not the opaque
    // [color] every other theme uses) keeps the background legible through
    // it while still giving the card a visible boundary/fill, and the
    // border is brightened to match for the same legibility reason.
    // User-requested: 0.10, then 0.20, still too faint - now [PrismFillColor]
    // (0.25, same white hue) shared with every other piece of card-style
    // chrome in the app (FeatureTile, FloatingTopBar, MainShell's bottom
    // nav) so they all read as one consistent glass system.
    val isPrism = LocalThemeName.current == "prism"
    val hairlineBrush = Brush.horizontalGradient(
        colors = listOf(Color.Transparent, Color.White.copy(alpha = spec.edgeAlpha), Color.Transparent),
    )
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isPrism) PrismBorderAlpha else 0.14f)
    // User-reported: putting the chrome (clip/background/border/hairline) AND
    // the content layout on the exact same Column node made the fill read as
    // "masked" wherever content sat - the card's own paint and the content's
    // layout were entangled on one node instead of being independent layers.
    // Split into a Box that owns only the chrome (fill/border/hairline/click)
    // and a Column child that owns only content layout/padding - the content
    // now sits as a genuinely separate layer on top, like the "transparent
    // PNG over the card" the fill was always meant to read as.
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isPrism) PrismFillColor else color, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        brush       = hairlineBrush,
                        start       = Offset(0f, 0.5f),
                        end         = Offset(size.width, 0.5f),
                        strokeWidth = 1.5f,
                    )
                }
            }
            .then(
                if (onClick != null)
                    Modifier.pressScale(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = indication, onClick = onClick)
                else Modifier
            ),
    ) {
        Column(
            Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}
