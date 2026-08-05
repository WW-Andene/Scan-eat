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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    color: Color = SurfaceVariant.copy(alpha = if (isLightBackground()) 0.6f else 0.28f),
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
        // User-reported: matched to the neomorphic reference sheet's directional
        // light model (one consistent light source, one consistent shadow, not a
        // symmetric ambient blur) - a soft shadow offset toward the bottom-left,
        // drawn behind the Surface below rather than via Modifier.shadow's
        // symmetric elevation shadow (kept on the Surface itself for the base
        // lift; this adds the directional weight on top of it).
        Box(
            Modifier
                .matchParentSize()
                .offset(x = -(spec.elevation * 0.7f), y = spec.elevation * 0.9f)
                .blur(spec.elevation)
                .background(ShadowTint.copy(alpha = 0.4f), shape),
        )
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
            color = color,
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
