package fr.scanneat.presentation.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A hairline top edge, applied over an existing card background so it reads
 * as a lit surface rather than a flat fill. No real-time blur — the app's
 * own gradients (or [ambientGloom]) are the only thing behind it. Clips to
 * [shape] so the overlay never peeks past a rounded corner — pass the same
 * shape used by the card's own Surface/background underneath.
 *
 * Declared light source: top-center, zenithal — every highlight in this file
 * (this hairline, [ambientGloom]'s glow blobs) faces up for that reason. Any
 * future glow/highlight added anywhere else in the app should face the same
 * direction rather than picking one ad hoc, or depth will read as inconsistent
 * once more than one surface is on screen at once.
 *
 * Reference pass: compared directly against Whispering Wishes' own glass
 * chrome (backdrop-filter: blur() + box-shadow + a single hairline border,
 * nothing else layered on top) after this app's version kept showing a
 * visible tint artifact tied to the header title / nav label text, on
 * every header and the nav bar alike, that survived several earlier
 * targeted fixes. This used to also draw a top "sheen" wash, a corner glow
 * blob, and a bottom "relief" shade - three extra translucent full-rect
 * layers stacked directly over that same text on every floating header/nav
 * bar in the app. Matching the simpler, proven-clean formula: blur (via
 * hazeEffect at the call site) + shadowElevation (also at the call site) +
 * this hairline only. [glowTint]/[glowAlpha]/[reliefAlpha] stay as
 * accepted-but-unused parameters so existing call sites don't need to change.
 */
fun Modifier.glassSheen(
    edgeAlpha: Float = 0.28f,
    shape: Shape = RoundedCornerShape(16.dp),
    glowTint: Color = Color.White,
    glowAlpha: Float = 0.05f,
    reliefAlpha: Float = 0.05f,
): Modifier = this
    .clip(shape)
    .drawWithCache {
        // Fades in/out via its own gradient stops instead of a flat color cut
        // off partway across the width (the old `inset` var) - a solid-color
        // line with a hard start/end reads as an abrupt stop rather than a
        // taper, exactly where the "edge" was supposed to be softest.
        val edgeBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = edgeAlpha), Color.Transparent),
        )
        onDrawWithContent {
            drawContent()
            drawLine(
                brush = edgeBrush,
                start = Offset(0f, 0.5f),
                end = Offset(size.width, 0.5f),
                strokeWidth = 1.5f,
            )
        }
    }

/**
 * Full-bleed ambient background wash — the screen's own flat [base] color
 * plus two soft, low-alpha radial "glow" blobs in [primary]/[secondary],
 * positioned off-center for a volumetric, non-flat feel instead of a
 * perfectly flat fill (the "volumetric gloom" from the app-wide polish
 * pass). Purely decorative and deliberately subtle — glows top out around
 * 7-10% alpha — so it never competes with foreground content, and glass
 * cards drawn on top of it (via the now-translucent ScanEatCard, see its own
 * doc comment) let a hint of this wash bleed through, which is what actually
 * reads as "frosted glass over an atmosphere" rather than two independent
 * effects. Intended as the outermost layer behind a screen's Scaffold/
 * Column/LazyColumn content — apply directly to that container's own
 * modifier in place of a plain `.background(Background)`.
 *
 * Settings > Appearance > "Animated background" (read here via
 * [LocalAnimatedGloom], provided by [ScanEatTheme]) adds two things on top
 * of the static version above, it does not replace it: the two glow blobs
 * drift slowly along their own independent circular path instead of
 * sitting fully still, AND a generated rain-on-a-puddle ripple pattern
 * plays over them — several rings, each spawned from its own fixed point,
 * expanding outward and fading as they grow, looping continuously, the
 * same top-down look as rain hitting standing water. Off by default: it's
 * a continuous per-frame redraw for as long as it's on, unlike every other
 * setting here, which are one-time layout choices. `composed {}` is
 * required (not a plain drawWithCache chain, like [glassSheen] above)
 * because reading a CompositionLocal and driving a per-frame clock both
 * need actual composition, not just a draw scope.
 */
private data class RippleSpec(
    val originXFrac: Float, val originYFrac: Float,
    val periodSec: Float, val phaseSec: Float, val usePrimary: Boolean,
)

fun Modifier.ambientGloom(
    base: Color,
    primary: Color,
    secondary: Color = primary,
): Modifier = composed {
    // app-audit §G4/§IV: every other continuous/prominent animation in the app
    // (rememberBreathingPulse, MainShell's tab transitions, expand/collapse
    // sections) is gated on rememberReducedMotion() - this one only checked
    // the user's own "Animated background" Settings toggle, so a user with
    // the system-level "remove animations" accessibility setting on (e.g. for
    // vestibular disorders) still got the continuous drift+ripple effect if
    // they'd also opted into the app's own toggle.
    val animated = LocalAnimatedGloom.current && !rememberReducedMotion()

    // Blob drift phase - rememberInfiniteTransition suits this one on its
    // own (a single float looping 0..2π), unlike the ripple clock below
    // which several independent ripples all need to read at once.
    val driftPhase = if (animated) {
        val transition = rememberInfiniteTransition(label = "ambientGloomDrift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ambientGloomDriftValue",
        ).value
    } else 0f

    // Free-running clock, not rememberInfiniteTransition - a handful of
    // independent ripples each need their own phase/period read every
    // frame, which one shared elapsed-time value drives more simply than a
    // separate Animatable per ripple. Restarts (and its LaunchedEffect
    // cancels) whenever the toggle flips, so no frames are spent while it's off.
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0L) start = now
                timeSec = (now - start) / 1_000_000_000f
            }
        }
    }

    // Fixed spawn points/timing, remembered once per composition so ripples
    // don't jump around on recomposition - Random-seeded (not a literal
    // hand-placed list) purely so different screens don't all show the exact
    // same ripple layout.
    val ripples = remember {
        val rng = Random(20260729)
        List(6) {
            RippleSpec(
                originXFrac = rng.nextFloat(),
                originYFrac = rng.nextFloat(),
                periodSec   = 3.5f + rng.nextFloat() * 3f,
                phaseSec    = rng.nextFloat() * 6f,
                usePrimary  = it % 2 == 0,
            )
        }
    }

    this.drawWithCache {
        // Drift radius scoped to a fraction of the screen so the blobs stay
        // gentle and never swing far enough to feel like a spotlight
        // sweeping across the content - opposite phase offsets (secondary
        // uses phase + PI) so the two blobs don't move in lockstep.
        val drift = size.width * 0.06f
        val primaryCenter = Offset(
            size.width * 0.88f + drift * cos(driftPhase),
            size.height * 0.04f + drift * sin(driftPhase) * 0.4f,
        )
        val secondaryCenter = Offset(
            size.width * 0.08f + drift * cos(driftPhase + Math.PI.toFloat()),
            size.height * 0.7f + drift * sin(driftPhase + Math.PI.toFloat()),
        )
        val primaryBrush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.10f), Color.Transparent),
            center = primaryCenter,
            radius = size.width * 0.9f,
        )
        val secondaryBrush = Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = 0.07f), Color.Transparent),
            center = secondaryCenter,
            radius = size.width * 1.1f,
        )
        val t = timeSec
        val maxRadius = size.minDimension * 0.32f
        onDrawBehind {
            drawRect(base)
            drawRect(primaryBrush)
            drawRect(secondaryBrush)
            if (animated) {
                ripples.forEach { r ->
                    val cycle = ((t + r.phaseSec) % r.periodSec) / r.periodSec
                    val center = Offset(size.width * r.originXFrac, size.height * r.originYFrac)
                    val tint = if (r.usePrimary) primary else secondary
                    // A real ripple radiates as more than one ring - a second,
                    // slightly-delayed ring from the same origin (cycle - 0.3)
                    // reads as a rain-drop splash instead of one static circle
                    // expanding on its own.
                    drawRippleRing(cycle, center, maxRadius, tint)
                    drawRippleRing(cycle - 0.3f, center, maxRadius, tint)
                }
            }
        }
    }
}

/**
 * The glass treatment for `DropdownMenu`-based popups: tinted shadow +
 * hairline sheen, same recipe as [ScanEatCard]'s Surface — minus real-time
 * backdrop blur, which `Modifier.hazeEffect` cannot provide here. Material3's
 * `DropdownMenu` renders inside its own `Popup`/`PopupWindow`, a separate
 * Android window from the screen content behind it; Haze captures the blur
 * source via a `GraphicsLayer` that belongs to the *originating* window's
 * Compose tree, so a popup in a different window has no access to it — this
 * is a structural Android/Compose limitation, not a per-device rendering gap.
 * [glassSheen]'s hairline and [ShadowTint]'s shadow tint are pure draw-scope
 * effects with no cross-window dependency, so they still apply cleanly; pair
 * with a translucent `containerColor` (e.g. `SurfaceVariant.copy(alpha = 0.94f)`)
 * at the call site for the closest achievable match to the app's card glass.
 */
fun Modifier.glassPopupSurface(shape: Shape = RoundedCornerShape(CardRadius.CONTROL)): Modifier = this
    .shadow(elevation = 6.dp, shape = shape, ambientColor = ShadowTint, spotColor = ShadowTint)
    .glassSheen(edgeAlpha = 0.22f, shape = shape, glowAlpha = 0.05f)

private fun DrawScope.drawRippleRing(cycle: Float, center: Offset, maxRadius: Float, tint: Color) {
    if (cycle <= 0f || cycle >= 1f) return
    val radius = cycle * maxRadius
    val fade = 1f - cycle
    val alpha = 0.16f * fade * fade
    if (alpha <= 0.001f) return
    drawCircle(
        color = tint,
        radius = radius,
        center = center,
        alpha = alpha,
        style = Stroke(width = 1.5f + 2f * fade),
    )
}
