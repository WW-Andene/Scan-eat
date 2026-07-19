package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * A soft top-light catch + hairline edge, applied over an existing card
 * background so it reads as a lit surface rather than a flat fill.
 * No real-time blur — the app's own gradients are the only thing behind it.
 * Clips to [shape] so the overlay never peeks past a rounded corner — pass
 * the same shape used by the card's own Surface/background underneath.
 *
 * Frosted-glass upgrade (app-wide polish pass): three more layers on top of
 * the original sheen+edge, all backward compatible (every existing call
 * site with no new arguments renders the same recipe, just deeper) —
 *  - [glowTint]/[glowAlpha]: a soft internal glow blob in the upper-left,
 *    echoing the card's own accent color (pass Gold/Teal/AccentCoral/etc.
 *    for a card that should feel "lit from within" in its section's own
 *    hue) rather than a flat neutral sheen everywhere.
 *  - [reliefAlpha]: a matching soft dark gradient at the bottom, so the
 *    surface reads as gently domed/carved instead of a flat sheen line.
 *  - [grainDensity]: a handful of near-invisible dots (see [grainTexture]'s
 *    own doc comment) giving the surface a touch of physical texture
 *    instead of a perfectly smooth flat fill. 0 disables it.
 * All three default to small, deliberately subtle values — this is meant to
 * layer onto *every* existing glassSheen()/ScanEatCard() call across the app
 * without anyone needing to touch call sites, so the defaults must never be
 * strong enough to fight the content drawn on top of them.
 */
fun Modifier.glassSheen(
    edgeAlpha: Float = 0.28f,
    shape: Shape = RoundedCornerShape(16.dp),
    glowTint: Color = Color.White,
    glowAlpha: Float = 0.05f,
    reliefAlpha: Float = 0.05f,
    grainDensity: Int = 0,
): Modifier = this
    .clip(shape)
    .grainTexture(density = grainDensity)
    .drawWithCache {
        val glow = if (glowAlpha > 0f) Brush.radialGradient(
            colors = listOf(glowTint.copy(alpha = glowAlpha), Color.Transparent),
            center = Offset(size.width * 0.16f, size.height * 0.08f),
            radius = size.maxDimension * 0.75f,
        ) else null
        val sheen = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
            endY = size.height * 0.45f,
        )
        val relief = if (reliefAlpha > 0f) Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = reliefAlpha)),
            startY = size.height * 0.55f,
        ) else null
        val inset = size.width * 0.14f
        onDrawWithContent {
            drawContent()
            glow?.let { drawRect(brush = it) }
            drawRect(brush = sheen)
            relief?.let { drawRect(brush = it) }
            drawLine(
                color = Color.White.copy(alpha = edgeAlpha),
                start = Offset(inset, 0.5f),
                end = Offset(size.width - inset, 0.5f),
                strokeWidth = 1.5f,
            )
        }
    }

/**
 * A very subtle procedural grain — [density] sparse, near-invisible dots at
 * a fixed seed (deterministic across recompositions — this is texture, not
 * animated noise — and recomputed only when [density] or the surface's own
 * size changes, via drawWithCache, not on every frame/recomposition). The
 * "give surfaces some texture/relief" ask from the app-wide polish pass:
 * kept sparse and low-alpha by design so it reads as "this surface has
 * substance" rather than visible static, and never interferes with the
 * legibility of text/content drawn on top. density=0 (the default when
 * called standalone) draws nothing — [glassSheen] is the usual entry point
 * and opts in explicitly via its own grainDensity parameter.
 */
fun Modifier.grainTexture(density: Int = 0, alpha: Float = 0.05f, seed: Long = 42L): Modifier =
    if (density <= 0) this else this.drawWithCache {
        val rng = Random(seed)
        val points = List(density) { Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height) }
        onDrawWithContent {
            drawContent()
            points.forEach { p -> drawCircle(color = Color.White.copy(alpha = alpha), radius = 0.8f, center = p) }
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
 */
fun Modifier.ambientGloom(
    base: Color,
    primary: Color,
    secondary: Color = primary,
): Modifier = this.drawWithCache {
    val primaryBrush = Brush.radialGradient(
        colors = listOf(primary.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(size.width * 0.88f, size.height * 0.04f),
        radius = size.width * 0.9f,
    )
    val secondaryBrush = Brush.radialGradient(
        colors = listOf(secondary.copy(alpha = 0.07f), Color.Transparent),
        center = Offset(size.width * 0.08f, size.height * 0.7f),
        radius = size.width * 1.1f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(primaryBrush)
        drawRect(secondaryBrush)
    }
}
