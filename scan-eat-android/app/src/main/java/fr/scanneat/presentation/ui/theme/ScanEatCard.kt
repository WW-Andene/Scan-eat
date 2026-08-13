package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.shadow
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
    // User-reported: "pourquoi les carte ne sont pas transparentes" (Prism
    // theme) - the whole point of that theme's full-bleed background image
    // (ambientGloom's isPrism branch, Glass.kt) is for it to show through;
    // every other theme keeps its own considered [color] fill.
    val isPrism = LocalThemeName.current == "prism"
    // User-reported: an outer Box (glassSheen's own clip + hairline draw)
    // wrapped around an inner Surface (its own separate shadow/clip/
    // background/border) rendered as two independently-clipped objects
    // stacked on top of each other - visible as a stray rectangle at the
    // card edge where the two layers' bounds didn't line up (worst with
    // Prism's transparent fill, where there was nothing to hide the seam).
    // Rebuilt as a single Column carrying shadow, clip, fill, border, the
    // hairline sheen, and the click ripple all in one modifier chain - one
    // object, one set of bounds, nothing to mismatch.
    Column(
        modifier.fillMaxWidth()
            .shadow(elevation = spec.elevation, shape = shape, clip = false)
            .clip(shape)
            .background(if (isPrism) Color.Transparent else color, shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)), shape)
            .glassSheen(edgeAlpha = spec.edgeAlpha, shape = shape, glowTint = accent, glowAlpha = spec.glowAlpha)
            .then(
                if (onClick != null)
                    Modifier.pressScale(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = indication, onClick = onClick)
                else Modifier
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
