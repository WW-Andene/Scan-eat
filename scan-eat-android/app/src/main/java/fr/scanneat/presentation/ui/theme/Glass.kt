package fr.scanneat.presentation.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A soft top-light catch + hairline edge, applied over an existing card
 * background so it reads as a lit surface rather than a flat fill.
 * No real-time blur — the app's own gradients are the only thing behind it.
 * Clips to [shape] so the overlay never peeks past a rounded corner — pass
 * the same shape used by the card's own Surface/background underneath.
 *
 * Frosted-glass rework: the dot-grain texture this used to layer on top
 * (via a since-removed grainTexture()) read as visual noise/false "shimmer"
 * rather than physical texture, so it's gone — [glowTint]/[glowAlpha] (a
 * soft internal glow blob, upper-left) and [reliefAlpha] (a matching dark
 * gradient at the bottom) are the only extra layers over the base sheen+edge
 * now, both kept deliberately subtle so they never fight the content drawn
 * on top of them.
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
        val glow = if (glowAlpha > 0f) Brush.radialGradient(
            colors = listOf(glowTint.copy(alpha = glowAlpha), Color.Transparent),
            center = Offset(size.width * 0.16f, size.height * 0.08f),
            radius = size.maxDimension * 0.75f,
        ) else null
        val sheen = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
            endY = size.height * 0.45f,
        )
        val relief = if (reliefAlpha > 0f) Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = reliefAlpha)),
            startY = size.height * 0.55f,
        ) else null
        // Fades in/out via its own gradient stops instead of a flat color cut
        // off partway across the width (the old `inset` var) - a solid-color
        // line with a hard start/end reads as an abrupt stop rather than a
        // taper, exactly where the "edge" was supposed to be softest.
        val edgeBrush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = edgeAlpha), Color.Transparent),
        )
        onDrawWithContent {
            drawContent()
            glow?.let { drawRect(brush = it) }
            drawRect(brush = sheen)
            relief?.let { drawRect(brush = it) }
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
 * [LocalAnimatedGloom], provided by [ScanEatTheme]) makes both blobs drift
 * slowly along their own independent circular path instead of sitting fully
 * static — a generated, ever-so-slightly moving gloom rather than a fixed
 * gradient. Off by default: it's a continuous per-frame redraw for as long
 * as it's on, unlike every other setting here, which are one-time layout
 * choices. `composed {}` is required (not a plain drawWithCache chain, like
 * [glassSheen] above) because reading a CompositionLocal and driving an
 * infinite animation both need actual composition, not just a draw scope.
 */
fun Modifier.ambientGloom(
    base: Color,
    primary: Color,
    secondary: Color = primary,
): Modifier = composed {
    val animated = LocalAnimatedGloom.current
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "ambientGloomPhase")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ambientGloomPhaseValue",
        ).value
    } else 0f

    this.drawWithCache {
        // Drift radius scoped to a fraction of the screen so the blobs stay
        // gentle and never swing far enough to feel like a spotlight sweeping
        // across the content - opposite phase offsets (secondary uses
        // phase + PI) so the two blobs don't move in lockstep.
        val drift = size.width * 0.06f
        val primaryCenter = Offset(
            size.width * 0.88f + drift * cos(phase),
            size.height * 0.04f + drift * sin(phase) * 0.4f,
        )
        val secondaryCenter = Offset(
            size.width * 0.08f + drift * cos(phase + Math.PI.toFloat()),
            size.height * 0.7f + drift * sin(phase + Math.PI.toFloat()),
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
        onDrawBehind {
            drawRect(base)
            drawRect(primaryBrush)
            drawRect(secondaryBrush)
        }
    }
}
