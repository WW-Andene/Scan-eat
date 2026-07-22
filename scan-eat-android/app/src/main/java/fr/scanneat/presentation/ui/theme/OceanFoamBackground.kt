package fr.scanneat.presentation.ui.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

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
// Animated, but throttled — not a real-time 60fps redraw. A coroutine ticks
// a "time" value a few times a second (see OceanFoamTickMs below) and only
// THAT tick regenerates the offscreen bitmap; ordinary frames in between
// just recomposite the same cached bitmap, same as the fully-static version
// this replaced. Depth/foam drifts slowly (a swell moving), caustics shimmer
// faster on top of it (each of the 4 ripple layers at a slightly different
// rate, so the light doesn't visibly slide in lockstep) — full per-pixel
// noise regeneration at display refresh rate would be a real, needless
// battery cost for a decorative background; a handful of regenerations per
// second reads as continuous motion at a small fraction of that cost.
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
 *
 * [timePhase] shifts each ripple layer by a different multiple of itself
 * (1x, 1.35x, 1.7x, 2.05x) so the whole filament web visibly crawls/
 * flickers over time rather than four layers sliding in perfect lockstep,
 * which would read as one slab moving instead of light shimmering.
 */
private fun caustics(x: Float, y: Float, seed: Int, timePhase: Float = 0f): Float {
    val angles = floatArrayOf(0.3f, 1.1f, 2.0f, 2.7f)
    var sum = 0f
    angles.forEachIndexed { i, angle ->
        val dx = cos(angle); val dy = sin(angle)
        val freq = 3.5f + i * 1.3f
        val phase = hash(seed, i, 7) * 6.2831853f
        sum += abs(sin((x * dx + y * dy) * freq + phase + timePhase * (1f + i * 0.35f)))
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
 *
 * [time] drives the animation: the depth/foam layer drifts through the
 * noise field slowly (a swell moving), while the caustic layer shimmers
 * several times faster on top of it (see [caustics]'s own doc comment) —
 * two different speeds read as "light flickering over moving water"
 * instead of the whole image sliding as one flat plane.
 */
private fun renderOceanFoamBitmap(gridW: Int, gridH: Int, seed: Int, time: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(gridW, gridH, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(gridW * gridH)
    // Noise-space scale: how many noise "cells" the grid spans. Lower =
    // larger, softer blobs (bigger swells); higher = busier, choppier foam.
    val scaleX = 3.4f
    val scaleY = 5.2f
    val driftX = time * 0.22f
    val driftY = time * 0.13f
    val causticPhase = time * 1.6f
    for (gy in 0 until gridH) {
        val ny = gy.toFloat() / gridH * scaleY
        for (gx in 0 until gridW) {
            val nx = gx.toFloat() / gridW * scaleX
            val depth = warpedFbm(nx + driftX, ny + driftY, seed)
            val light = caustics(nx, ny, seed, causticPhase)

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

/** How often [oceanFoamBackground] regenerates its bitmap — a deliberate few-times-a-second tick, not a real-time 60fps redraw (see this file's header comment). */
private const val OceanFoamTickMs = 160L
private const val OceanFoamTimeStep = 0.05f

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
@Composable
fun Modifier.oceanFoamBackground(seed: Int = 1): Modifier {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(OceanFoamTickMs)
            time += OceanFoamTimeStep
        }
    }
    return this.drawWithCache {
        // Reading `time` here (not inside onDrawBehind) is what makes this
        // cache-invalidate — and so regenerate the bitmap — only on the
        // throttled ticks above, instead of on every actual display frame.
        val t = time
        // ~1 sample per 6dp — dense enough that the bilinear upscale below reads
        // as smooth, organic turbulence rather than visible grid cells, while
        // staying at most a few thousand samples even on a large tablet screen.
        val gridW = (size.width / 6f).toInt().coerceIn(24, 160)
        val gridH = (size.height / 6f).toInt().coerceIn(32, 220)
        val bitmap = renderOceanFoamBitmap(gridW, gridH, seed, t).asImageBitmap()
        onDrawBehind {
            drawImage(
                image = bitmap,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
