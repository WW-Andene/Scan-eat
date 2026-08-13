package fr.scanneat.presentation.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
 * pass). Purely decorative — glows top out around 14-20% alpha (raised from
 * an original 7-10%, which read as too faint/diffuse to register as a light
 * source at all against reference dashboards with a visible warm glow) —
 * still gentle enough not to compete with foreground content, and glass
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
    // Notebook theme: paper + ruled horizontal lines + a left-edge spiral
    // binding replace the radial "gloom" blobs entirely - a glowing light
    // pool doesn't belong on a sheet of paper, and every screen already
    // calls this one function for its outermost background, so gating here
    // is what makes the paper look apply app-wide with no per-screen change.
    val isNotebook = LocalThemeName.current == "notebook"

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
        // User-reported: next to reference dashboards with a visible warm light
        // pool behind the glass, this background read as flat - at 0.10/0.07
        // alpha over a wide 0.9x/1.1x-screen-width radius, the wash was too
        // faint and too diffuse to register as a light source at all, just a
        // barely-there tint. Roughly doubled the alpha and pulled the radius
        // in so each blob reads as a defined pool of light instead of a haze
        // spread thin across the whole screen.
        val primaryBrush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.20f), Color.Transparent),
            center = primaryCenter,
            radius = size.width * 0.72f,
        )
        val secondaryBrush = Brush.radialGradient(
            colors = listOf(secondary.copy(alpha = 0.14f), Color.Transparent),
            center = secondaryCenter,
            radius = size.width * 0.9f,
        )
        val t = timeSec
        val maxRadius = size.minDimension * 0.32f
        // Ring geometry precomputed here (drawWithCache, not per-frame) since
        // it only depends on `size`, same reasoning the brushes above are
        // hoisted out of onDrawBehind.
        val ringColumnWidth = 30.dp.toPx()
        // User-reported: "moins de trou, trous plus gros" - fewer, larger
        // holes (spacing widened, radius grown) to match the reference
        // mockup's sparser, chunkier hole-punch column.
        val ringSpacing = 72.dp.toPx()
        val ringRadius = 8.dp.toPx()
        onDrawBehind {
            if (isNotebook) {
                // User-requested: "enlever les ligne du background" - the
                // horizontal ruled lines this used to draw are gone; plain
                // paper fill plus the spiral binding only.
                drawRect(NotebookPaper)
                // Left-edge spiral binding, redrawn per reference image:
                // user-clarified the earlier zigzag coil "toi tu l'a mal
                // fais" - a real spiral binding is one metal loop PER hole
                // (an oval passing through/around each punched hole), not
                // one continuous wavy line threading corner to corner.
                // drawNotebookCoilLoop draws that per-hole loop; called once
                // per hole alongside its shadow/fill below.
                var ringY = ringSpacing / 2f
                while (ringY < size.height) {
                    val ringCenter = Offset(ringColumnWidth / 2f, ringY)
                    drawBlurredShadowCircle(Offset(ringCenter.x + 1.5.dp.toPx(), ringCenter.y + 2.dp.toPx()), ringRadius + 2.dp.toPx(), 4.dp.toPx(), ShadowTint.copy(alpha = 0.5f))
                    drawCircle(color = NotebookPaper, radius = ringRadius + 3.dp.toPx(), center = ringCenter)
                    drawCircle(color = NotebookInk.copy(alpha = 0.55f), radius = ringRadius, center = ringCenter)
                    drawNotebookCoilLoop(ringCenter, ringRadius)
                    ringY += ringSpacing
                }
                return@onDrawBehind
            }
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
 * Foreground hole-punch overlay - draws the same ring column
 * [ambientGloom]'s notebook branch draws as a BACKGROUND layer, but usable
 * as a plain `Modifier` on top of arbitrary content (a `drawWithContent`
 * overlay, not `drawBehind`). Needed because [ambientGloom] is a
 * background wash - on the Scan screen the live camera preview is a
 * full-bleed `AndroidView` that completely covers whatever's drawn behind
 * it, so [ambientGloom]'s own column there was coded but literally
 * invisible (user-reported: "pas de spirale dans le décors"). Applying
 * this instead, on top of the camera preview, matches the notebook
 * mockups the user supplied - a photo taped into a notebook still shows
 * the binding holes sitting on top of it at the page edge, not hidden
 * behind it. User-reported follow-up: "les spirale n'existe pas, seulement
 * les trou" - no metal-coil ring stroke, just a plain punched hole.
 */
fun Modifier.notebookSpiralBinding(): Modifier = this.drawWithCache {
    val ringColumnWidth = 24.dp.toPx()
    val ringSpacing = 68.dp.toPx()
    val ringRadius = 7.dp.toPx()
    onDrawWithContent {
        drawContent()
        var ringY = ringSpacing / 2f
        while (ringY < size.height) {
            val ringCenter = Offset(ringColumnWidth / 2f, ringY)
            drawBlurredShadowCircle(Offset(ringCenter.x + 1.5.dp.toPx(), ringCenter.y + 2.dp.toPx()), ringRadius + 2.dp.toPx(), 4.dp.toPx(), Color.Black.copy(alpha = 0.5f))
            drawCircle(color = Color.Black.copy(alpha = 0.6f), radius = ringRadius, center = ringCenter)
            drawNotebookCoilLoop(ringCenter, ringRadius)
            ringY += ringSpacing
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
 * with a translucent `containerColor` (e.g. `SurfaceVariant.copy(alpha = StandardCardAlpha)`)
 * at the call site for the closest achievable match to the app's card glass.
 */
fun Modifier.glassPopupSurface(shape: Shape = RoundedCornerShape(CardRadius.CONTROL)): Modifier = this
    // MIUI-observed bug (see ScanEatCard.kt): ambientColor/spotColor-tinted
    // Modifier.shadow renders as a solid, hard-edged grey rectangle instead of
    // a soft shadow on some OEM skins. Reverted to the neutral default shadow
    // color.
    .shadow(elevation = 6.dp, shape = shape)
    .glassSheen(edgeAlpha = 0.22f, shape = shape, glowAlpha = 0.05f)

/**
 * Draws one real spiral-binding wire loop through a single punched hole -
 * a wide oval passing through the hole and extending past it on both
 * sides, matching the reference notebook mockup's coil (one loop per
 * hole, not one continuous line threading corner to corner). User-
 * corrected: "spirale c'est comme la référence parce que toi tu l'as mal
 * fait" - the earlier zigzag misread "continuous coil" as one wavy path;
 * a real spiral-bound coil reads as a distinct oval ring per hole. A
 * lighter highlight arc on the loop's upper-left gives it a rounded
 * metal-wire sheen instead of a flat stroke.
 */
private fun DrawScope.drawNotebookCoilLoop(center: Offset, holeRadius: Float) {
    val loopWidth = holeRadius * 3.4f
    val loopHeight = holeRadius * 2.3f
    val metal = Color(0xFF6E6A63)
    val topLeft = Offset(center.x - loopWidth / 2f, center.y - loopHeight / 2f)
    val loopSize = Size(loopWidth, loopHeight)
    drawOval(color = metal, topLeft = topLeft, size = loopSize, style = Stroke(width = 3.dp.toPx()))
    // Highlight arc: a shorter, offset stroke along the top-left quadrant
    // only, the classic "rounded wire catching light" cue.
    drawArc(
        color = Color.White.copy(alpha = 0.35f),
        startAngle = 200f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = topLeft,
        size = loopSize,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

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

/**
 * User-requested (Notebook theme): "les cercle et gauge doivent être en
 * trait de crayon de couleur" - a colored-pencil/crayon-textured circular
 * progress ring instead of Material's smooth [androidx.compose.material3.CircularProgressIndicator]
 * arc. Drawn as many short, alpha-jittered radial strokes packed along the
 * progress arc (same "visible individual strokes, not a perfectly even
 * line" idea as [WeeklyBarsCard]'s crayon bars), rather than one continuous
 * stroke - a single arc with `pathEffect` dashing reads as "dashed line,"
 * not "hand-colored," which is why this hand-draws each short segment
 * instead. `trackColor` (the unfilled remainder of the ring) is drawn the
 * same way at low alpha so the two read as one continuous hand-drawn
 * circle rather than a smooth track behind a textured fill.
 *
 * Deliberately covers only the app's most prominent single ring (Result's
 * ScoreRing/DualScoreRing) in this pass, not all ~13 CircularProgressIndicator
 * call sites app-wide (Hydration, TodayMacroCard, ActiveFastCard, etc.) -
 * same "flag the remaining scope honestly" approach as the post-it card
 * conversion.
 */
fun DrawScope.drawCrayonRing(progress: Float, color: Color, trackColor: Color, strokeWidthPx: Float) {
    val radius = (size.minDimension - strokeWidthPx) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val segmentDeg = 3f
    val totalSegments = (360f / segmentDeg).toInt()
    val filledSegments = (totalSegments * progress).toInt().coerceIn(0, totalSegments)
    val rng = Random(center.x.toInt() * 31 + center.y.toInt())
    for (i in 0 until totalSegments) {
        val startAngle = -90f + i * segmentDeg
        val isFilled = i < filledSegments
        val jitterWidth = strokeWidthPx * (0.85f + rng.nextFloat() * 0.3f)
        val jitterRadius = radius + (rng.nextFloat() - 0.5f) * strokeWidthPx * 0.15f
        drawArc(
            color = if (isFilled) color else trackColor,
            startAngle = startAngle,
            sweepAngle = segmentDeg * 0.8f,
            useCenter = false,
            topLeft = Offset(center.x - jitterRadius, center.y - jitterRadius),
            size = androidx.compose.ui.geometry.Size(jitterRadius * 2, jitterRadius * 2),
            style = Stroke(width = jitterWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            alpha = if (isFilled) 0.75f + rng.nextFloat() * 0.25f else 0.4f,
        )
    }
}

/**
 * User-requested (Notebook theme): "fait en sorte que tout les texte es des
 * offset variable de 1dp pour un rendu naturel" - a small per-instance
 * random x/y nudge (±1dp), rolled once via `remember` and held stable
 * across recomposition (same reasoning [rememberNotebookPostItStyle]'s own
 * doc comment gives), so text doesn't sit on a perfectly mechanical grid -
 * closer to how handwriting/pasted notes never land pixel-perfectly
 * aligned. Opt-in per `Text()` call via `.notebookTextJitter()`.
 *
 * NOT applied to every `Text()` call in the app - Compose's `Text()` is
 * called directly (via Material3, not through a single shared wrapper) at
 * several hundred call sites app-wide, so making literally all of them
 * jitter would mean touching every one individually. Applied instead to
 * the most prominent, highest-visibility text under Notebook theme
 * (header title, score grade/number) as a representative implementation -
 * broader coverage is a larger, separate follow-up.
 */
fun Modifier.notebookTextJitter(): Modifier = composed {
    if (LocalThemeName.current != "notebook") return@composed this
    // User-reported: "le Offset des textes et tailles n'es pas assez
    // aléatoire" - widened from +/-1dp to +/-3dp and added a small rotation
    // jitter (none existed before), so jittered text reads as hand-placed
    // rather than a barely-perceptible nudge.
    val dx = remember { (Random.nextFloat() - 0.5f) * 6f }
    val dy = remember { (Random.nextFloat() - 0.5f) * 6f }
    val rotation = remember { (Random.nextFloat() - 0.5f) * 6f }
    this
        .offset(dx.dp, dy.dp)
        .graphicsLayer { rotationZ = rotation }
}

/**
 * User-requested: "ajoutes les ombres... pour les spirales" - a soft
 * blurred drop shadow behind each spiral-binding ring, since a plain
 * offset circle (Compose's `drawCircle` has no blur of its own) would just
 * look like a second flat ring rather than a shadow. Uses the platform
 * Canvas's real `BlurMaskFilter` (via `nativeCanvas`) rather than Compose's
 * `Modifier.blur()` - this is a raw draw call inside an existing
 * DrawScope (ambientGloom's background layer, notebookSpiralBinding's
 * foreground overlay), not a composable with its own layout node, so
 * `Modifier.blur()` isn't applicable here the way it is for
 * FloatingTopBar's header shadow.
 */
private fun DrawScope.drawBlurredShadowCircle(center: Offset, radius: Float, blurRadiusPx: Float, color: Color) {
    drawContext.canvas.nativeCanvas.drawCircle(
        center.x, center.y, radius,
        android.graphics.Paint().apply {
            this.color = color.toArgb()
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        },
    )
}
