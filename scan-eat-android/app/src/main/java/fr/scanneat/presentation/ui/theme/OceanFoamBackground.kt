package fr.scanneat.presentation.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================
// OCEAN FOAM BACKGROUND — procedural bottom-view wave-foam simulation.
//
// "Bottom view" = looking straight up through the water at the underside of
// the surface, the way underwater photography sees it: no wave silhouette or
// horizon, just the water's own turbulence (foam patches, depth-driven blue
// gradient) overlaid with the thin, web-like caustic lines sunlight makes
// when it refracts through a moving surface (the pattern on a sunlit pool
// floor). Algorithm, in layers:
//
//  1. Value noise (hashed lattice + smoothstep interpolation) — the one
//     primitive everything else builds on.
//  2. FBM (fractal Brownian motion) — several octaves of (1) at doubling
//     frequency/halving amplitude, giving organic multi-scale turbulence
//     instead of one uniform blob size.
//  3. Domain warping — a SECOND fbm() sampled at a coordinate offset by the
//     FIRST fbm()'s own output: fbm(p + k·fbm(p)). This is what turns plain
//     lumpy noise into the swirling, marbled look real foam actually has —
//     without it the pattern reads as blotchy static, not moving water.
//  4. Caustics — several sine-wave ripples at different angles/frequencies,
//     summed and folded through abs()/a power curve to collapse them into
//     thin bright filaments rather than a smooth wave — the specific detail
//     that reads as "light through water," not just "wavy pattern."
//  5. Compositing — a depth-based blue gradient (warped-fbm value: low =
//     deep/dark, high = shallow/light) + caustic highlights blended on top +
//     a foam mask (smoothstepped high band of the warped fbm) blended
//     toward near-white for the actual foam patches.
//
// Deliberately STATIC once generated (recomputed only when the container's
// size changes, same drawWithCache pattern as ambientGloom/glassSheen in
// this same package) — continuously re-animating a per-pixel procedural
// texture at 60fps would be a real, needless battery cost for a decorative
// background, and nothing else in this app's design system animates a
// background at all (see glassSheen's own "no real-time blur" note).
// [seed] varies the exact pattern — the same seed always reproduces the same
// pattern, so the app has one stable, recognizable "look" rather than a
// different random result on every recomposition or app restart.
// ============================================================================

/** Squirrel3-style integer hash — fast, dependency-free, decorrelates neighboring lattice points well enough for value noise. */
private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h shr 13)) * 1274126177
    h = h xor (h shr 16)
    return (h and 0x7fffffff) / 2147483647f
}

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

/** 2D value noise in [0,1] — bilinear-interpolated hashed lattice, smoothstepped for a C1-continuous (no visible facets) result. */
private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
    val fx = smoothstep(x - x0); val fy = smoothstep(y - y0)
    val h00 = hash(x0, y0, seed);     val h10 = hash(x0 + 1, y0, seed)
    val h01 = hash(x0, y0 + 1, seed); val h11 = hash(x0 + 1, y0 + 1, seed)
    val top = h00 + (h10 - h00) * fx
    val bottom = h01 + (h11 - h01) * fx
    return top + (bottom - top) * fy
}

/** Sum of [octaves] doublings of [valueNoise] — amplitude halves each octave so coarse structure dominates while finer octaves add roughness. Result normalized to [0,1]. */
private fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 4): Float {
    var amplitude = 0.5f
    var frequency = 1f
    var sum = 0f
    var norm = 0f
    repeat(octaves) { i ->
        sum += valueNoise(x * frequency, y * frequency, seed + i * 101) * amplitude
        norm += amplitude
        amplitude *= 0.5f
        frequency *= 2f
    }
    return sum / norm
}

/**
 * fbm(p + k·fbm(p)) — domain warping. The two warp-offset samples use a
 * different seed than the final sample (and than each other, via the x/y
 * coordinate offset) so the warp doesn't just retrace the same lattice —
 * that would cancel into a flat re-scale instead of an actual swirl.
 */
private fun warpedFbm(x: Float, y: Float, seed: Int, warpStrength: Float = 1.6f): Float {
    val warpX = fbm(x, y, seed + 991, octaves = 3)
    val warpY = fbm(x + 5.2f, y + 1.3f, seed + 991, octaves = 3)
    return fbm(x + warpStrength * warpX, y + warpStrength * warpY, seed, octaves = 5)
}

/**
 * Thin, bright, web-like light filaments — several angled sine ripples,
 * summed then folded through abs()/pow() so both troughs and crests read as
 * bright "caustic" lines rather than a smooth wave. A plain average of
 * abs(sin(...)) alone reads as a soft plaid, not the thin, high-contrast
 * filaments real caustics show — the power curve is what compresses the
 * mid-tones down to leave only the sharpest ridges bright.
 */
private fun caustics(x: Float, y: Float, seed: Int): Float {
    val angles = floatArrayOf(0.3f, 1.1f, 2.0f, 2.7f)
    var sum = 0f
    angles.forEachIndexed { i, angle ->
        val dx = cos(angle); val dy = sin(angle)
        val freq = 3.5f + i * 1.3f
        val phase = hash(seed, i, 7) * 6.2831853f
        sum += abs(sin((x * dx + y * dy) * freq + phase))
    }
    val avg = sum / angles.size
    return (1f - avg).coerceIn(0f, 1f).pow(3f)
}

private val OceanFoamDeep     = Color(0xFF063A52)
private val OceanFoamMid      = Color(0xFF0E6E8C)
private val OceanFoamShallow  = Color(0xFF3FB6C4)
private val OceanFoamWhite    = Color(0xFFF3FCFC)
private val OceanFoamCaustic  = Color(0xFFBDF3EC)

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val tt = t.coerceIn(0f, 1f)
    return Color(
        red   = a.red + (b.red - a.red) * tt,
        green = a.green + (b.green - a.green) * tt,
        blue  = a.blue + (b.blue - a.blue) * tt,
        alpha = 1f,
    )
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Renders one frame of the bottom-view foam pattern into a small offscreen
 * bitmap at [gridW]x[gridH] samples (not full screen resolution — the
 * caller upscales with bilinear filtering, which both keeps this cheap and
 * gives the soft, painterly edge real foam/water photography has, rather
 * than a crisp per-pixel-accurate but sample-limited texture).
 */
private fun renderOceanFoamBitmap(gridW: Int, gridH: Int, seed: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(gridW, gridH, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(gridW * gridH)
    // Noise-space scale: how many noise "cells" the grid spans. Lower =
    // larger, softer blobs (bigger swells); higher = busier, choppier foam.
    val scaleX = 3.4f
    val scaleY = 5.2f
    for (gy in 0 until gridH) {
        val ny = gy.toFloat() / gridH * scaleY
        for (gx in 0 until gridW) {
            val nx = gx.toFloat() / gridW * scaleX
            val depth = warpedFbm(nx, ny, seed)
            val light = caustics(nx, ny, seed)

            var color = if (depth < 0.5f) lerpColor(OceanFoamDeep, OceanFoamMid, depth / 0.5f)
                        else lerpColor(OceanFoamMid, OceanFoamShallow, (depth - 0.5f) / 0.5f)
            color = lerpColor(color, OceanFoamCaustic, light * 0.55f)
            val foamT = smoothstep(((depth - 0.72f) / 0.28f).coerceIn(0f, 1f))
            color = lerpColor(color, OceanFoamWhite, foamT * 0.85f)

            pixels[gy * gridW + gx] = color.toArgbInt()
        }
    }
    bitmap.setPixels(pixels, 0, gridW, 0, 0, gridW, gridH)
    return bitmap
}

/**
 * Full-bleed procedural "bottom view" ocean foam background — see this
 * file's header comment for the algorithm. Drop-in alternative to
 * [ambientGloom] for a container that wants this decorative theme instead
 * of the default flat-color + glow wash.
 *
 * [seed] varies the exact pattern (same seed = identical pattern every
 * time, for a stable, recognizable "look" rather than a different random
 * result on every recomposition/app restart).
 */
fun Modifier.oceanFoamBackground(seed: Int = 1): Modifier = this.drawWithCache {
    // ~1 sample per 6dp — dense enough that the bilinear upscale below reads
    // as smooth, organic turbulence rather than visible grid cells, while
    // staying at most a few thousand samples even on a large tablet screen.
    val gridW = (size.width / 6f).toInt().coerceIn(24, 160)
    val gridH = (size.height / 6f).toInt().coerceIn(32, 220)
    val bitmap = renderOceanFoamBitmap(gridW, gridH, seed).asImageBitmap()
    onDrawBehind {
        drawImage(
            image = bitmap,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}
