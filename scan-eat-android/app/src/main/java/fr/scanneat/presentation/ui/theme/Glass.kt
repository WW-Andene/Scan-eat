package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
 * True while MainShell's root Box is painting a decorative background
 * pattern (e.g. the ocean-foam theme, see OceanFoamBackground.kt) behind
 * every screen instead of a flat color fill. [ambientGloom] reads this to
 * skip its own opaque base paint so that root-level pattern can actually
 * reach the screen instead of being fully hidden behind an identical-
 * looking opaque re-paint on every single screen.
 */
val LocalDecorativeBackgroundActive = staticCompositionLocalOf { false }

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
 * [base] is skipped while [LocalDecorativeBackgroundActive] is true —
 * MainShell's root Box already painted a decorative pattern behind every
 * screen in that case, and re-painting [base] opaquely on top of it here
 * would hide that pattern completely rather than letting it read through
 * under the two glow washes above.
 */
@Composable
fun Modifier.ambientGloom(
    base: Color,
    primary: Color,
    secondary: Color = primary,
): Modifier {
    val paintBase = !LocalDecorativeBackgroundActive.current
    return this.drawWithCache {
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
            if (paintBase) drawRect(base)
            drawRect(primaryBrush)
            drawRect(secondaryBrush)
        }
    }
}
