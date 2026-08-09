package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
 * The app's one card primitive — glassSheen() top-light + hairline edge over
 * a fill, 16dp corners by default. Generalizes the pattern BioCard() already
 * proved out, so a hand-rolled `Surface(...)` doesn't need to be re-derived
 * (and its glassSheen/radius drifted) on every new screen. glassSheen is on
 * by default — the app's one distinctive surface treatment should be the
 * default, not a per-screen coin flip.
 *
 * Frosted-glass + hierarchy upgrade (app-wide polish pass):
 *  - [color] defaults to a translucent fill so a screen's own ambient
 *    background wash (see [ambientGloom]) bleeds through — this is what
 *    reads as "frosted glass over an atmosphere" rather than a flat tinted
 *    rectangle. Existing call sites that pass an explicit [color] are
 *    unaffected.
 *    User-reported correction (real-device screenshots, Dashboard): an
 *    earlier pass dropped this to 0.24 dark-theme alpha reasoning that 0.42
 *    "dominated the blend" against ambientGloom's ~7-10% glow blobs - but at
 *    0.24, next to the app's actual (mostly static, not glowing) background,
 *    cards read as "almost inseparable" from it instead of a distinct
 *    surface. Raised back to 0.4 - still meaningfully translucent (nowhere
 *    near the old fully-opaque baseline), paired with PrimaryGlassSpec's new
 *    subtle border above so the fill difference isn't the only thing
 *    carrying the card's edge.
 *  - [emphasis]/[accent] pick which [CardEmphasis] tier this card renders at
 *    and which hue its glow/border echo — default (PRIMARY, white accent)
 *    reproduces this primitive's original look plus the new subtle layers,
 *    so no existing call site needs to change to keep working.
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
    // design-aesthetic-audit: Light theme's SurfaceVariant (#F0E7E0) sits only ~1-3
    // RGB units from its own Background (#F6F1EC) - composited at the 0.24 alpha
    // tuned against Dark/OLED (where SurfaceVariant contrasts strongly with a
    // near-black Background), the card's own fill was imperceptible in Light theme.
    // With no visible fill, the card never read as one whole shape - only its
    // shadowElevation shadow (which DOES have real contrast against a light
    // background) showed up, as a disconnected rectangle instead of a filled card.
    // User-requested: one standard glass config app-wide (see StandardCardAlpha's
    // own doc comment) - previously tuned independently as a more-translucent
    // "floats over the ambient wash" look, now the same near-opaque fill every
    // dialog/popup already uses.
    color: Color = SurfaceVariant.copy(alpha = StandardCardAlpha),
    contentPadding: PaddingValues = PaddingValues(Spacing.L),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
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
    Box(
        modifier.fillMaxWidth().glassSheen(
            edgeAlpha = spec.edgeAlpha,
            shape = shape,
            glowTint = accent,
            glowAlpha = spec.glowAlpha,
        ),
    ) {
        // Dropped the previous directional shadow layer (offset + Modifier.blur
        // behind the Surface): Modifier.blur relies on RenderEffect, which
        // silently no-ops on API < 31 / unsupported GPU drivers, leaving that
        // offset ShadowTint box rendered hard-edged instead of blurred - a
        // visible stray rectangle peeking out from the card corner. Surface's
        // own .shadow() below (same approach FeatureTile already uses, which
        // never showed this artifact) is the only shadow now.
        // User-reported: a visibly separate, lighter rounded rectangle floating
        // inside every card (Dashboard screenshot) - blur(3.dp) below had
        // nothing behind it to actually blur (a card IS the screen's own
        // scrolling content, unlike FloatingTopBar/bottom nav's real Haze
        // backdrop blur), so RenderEffect sampled transparent pixels past this
        // Box's own clipped edge and faded the opaque fill inward from it -
        // shrinking the visible fill to a smaller box sitting inside the
        // card's real boundary (drawn by the Surface's shadow/clip below),
        // with the ambient background showing through the gap between them.
        // Dropped: a flat fill inside the same clip has no such edge to fade.
        Box(Modifier.matchParentSize().clip(shape).background(color))
        Surface(
            // Xiaomi/MIUI-observed bug (user screenshot, Light theme): Surface's shadow
            // is computed from [shape]'s outline and renders correctly rounded, but its
            // own background fill isn't reliably force-clipped to that same outline on
            // every rendering path - on the affected device the fill painted as a plain
            // rectangle while the shadow stayed rounded, showing the rounded shadow
            // peeking out past a square-cornered fill at all four corners. Explicit
            // .clip(shape) forces the fill to hard-clip regardless of that path.
            // F16 (docs/design-audit-step6-color-atmosphere.md): shadow drawn explicitly
            // here with a warm-tinted color instead of via Surface's own shadowElevation
            // param, which always renders Compose's neutral default shadow color
            // regardless of the palette — Surface's shadowElevation stays at 0 below so
            // the two don't stack.
            modifier = Modifier.fillMaxWidth()
                .shadow(elevation = spec.elevation, shape = shape, ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(shape)
                // User-reported: no hard border, no color tint - a very slight inner
                // bloom instead, a soft radial vignette centered on the card that
                // fades away moving from the edge in toward the center (rather than
                // glassSheen's hairline top light or the diagonal border this used
                // to have). Scoped to this card only, not glassSheen itself, which
                // stays untouched (see its own doc comment on why a bottom relief
                // shade was deliberately removed from headers/nav).
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.07f)),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.maxDimension * 0.75f,
                        ),
                    )
                }
                .then(
                    if (onClick != null)
                        Modifier.pressScale(interactionSource)
                            .clickable(interactionSource = interactionSource, indication = indication, onClick = onClick)
                    else Modifier
                ),
            shape = shape,
            color = Color.Transparent,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
