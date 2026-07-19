package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * How strongly a card should announce itself relative to its neighbors —
 * the "hiérarchisation" from the app-wide polish pass: previously every card
 * on a screen got the exact same glassSheen()/border treatment regardless of
 * whether it was the one number a screen exists to show (Dashboard's calorie
 * balance, Result's score) or a minor supporting stat three scrolls down.
 *  - HERO: the single focal element on a screen — stronger glow, a visible
 *    tinted border, and a touch more grain. Use for at most one element per
 *    screen; using it everywhere defeats the point of a hierarchy.
 *  - PRIMARY: the default — matches this card primitive's original look
 *    (same edge/glow strength ScanEatCard already shipped with) plus the new
 *    subtle glow/grain layers everyone gets for free.
 *  - SECONDARY: supporting/minor content — quieter still, no grain.
 */
enum class CardEmphasis { HERO, PRIMARY, SECONDARY }

private data class GlassSpec(val glowAlpha: Float, val edgeAlpha: Float, val grainDensity: Int, val borderAlpha: Float)
private val HeroGlassSpec      = GlassSpec(glowAlpha = 0.12f, edgeAlpha = 0.34f, grainDensity = 70, borderAlpha = 0.22f)
private val PrimaryGlassSpec   = GlassSpec(glowAlpha = 0.06f, edgeAlpha = 0.16f, grainDensity = 40, borderAlpha = 0f)
private val SecondaryGlassSpec = GlassSpec(glowAlpha = 0.03f, edgeAlpha = 0.10f, grainDensity = 0,  borderAlpha = 0f)

/**
 * The app's one card primitive — glassSheen() top-light + hairline edge over
 * a fill, 16dp corners by default. Generalizes the pattern BioCard() already
 * proved out, so a hand-rolled `Surface(...)` doesn't need to be re-derived
 * (and its glassSheen/radius drifted) on every new screen. glassSheen is on
 * by default — the app's one distinctive surface treatment should be the
 * default, not a per-screen coin flip.
 *
 * Frosted-glass + hierarchy upgrade (app-wide polish pass):
 *  - [color] now defaults to a translucent (not fully opaque) fill, so a
 *    screen's own ambient background wash (see [ambientGloom]) bleeds
 *    through very slightly — this is what actually reads as "frosted glass
 *    over an atmosphere" rather than a flat tinted rectangle. Existing call
 *    sites that pass an explicit [color] are unaffected.
 *  - [emphasis]/[accent] pick which [CardEmphasis] tier this card renders at
 *    and which hue its glow/border echo — default (PRIMARY, white accent)
 *    reproduces this primitive's original look plus the new subtle layers,
 *    so no existing call site needs to change to keep working.
 */
@Composable
fun ScanEatCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(CardRadius.CARD),
    color: Color = SurfaceVariant.copy(alpha = 0.86f),
    contentPadding: PaddingValues = PaddingValues(14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    emphasis: CardEmphasis = CardEmphasis.PRIMARY,
    accent: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = when (emphasis) {
        CardEmphasis.HERO      -> HeroGlassSpec
        CardEmphasis.PRIMARY   -> PrimaryGlassSpec
        CardEmphasis.SECONDARY -> SecondaryGlassSpec
    }
    Box(
        modifier.fillMaxWidth().glassSheen(
            edgeAlpha = spec.edgeAlpha,
            shape = shape,
            glowTint = accent,
            glowAlpha = spec.glowAlpha,
            grainDensity = spec.grainDensity,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = if (spec.borderAlpha > 0f) BorderStroke(1.dp, accent.copy(alpha = spec.borderAlpha)) else null,
        ) {
            Column(Modifier.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
        }
    }
}
