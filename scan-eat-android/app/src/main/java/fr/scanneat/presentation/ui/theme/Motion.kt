package fr.scanneat.presentation.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * The one signature easing curve, reserved for the score-reveal moment
 * (Result screen) — everything else keeps Compose's default easing.
 * A soft overshoot-free ease-out: quick to start, settles gently, matching
 * the "quiet, confident" character rather than a bouncy/playful spring.
 *
 * Also now the shared easing for the entrance/press-feedback helpers below —
 * "reserved for the score-reveal moment" meant *reserved from Material's
 * bouncier defaults*, not "used exactly once": every other prominent
 * animation this app adds should read as the same "quiet, confident"
 * character, not a different curve per screen.
 */
val ScoreRevealEasing: Easing = CubicBezierEasing(0.16f, 0.84f, 0.28f, 1f)

/**
 * True when the system's "Remove animations" accessibility setting is on
 * (Settings.Global.ANIMATOR_DURATION_SCALE == 0). Every new prominent
 * animation — starting with the score reveal — must gate on this before
 * shipping, per the audit's Chain 2: adding motion without checking this
 * first makes the existing zero-reduced-motion gap worse, not better.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    // Was missing remember{} despite the name - every call (pressScale is applied to
    // every clickable ScanEatCard app-wide) performed a fresh synchronous
    // ContentResolver/Binder round trip on every single recomposition.
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Gentle fade + scale-up entrance for a screen's one "hero" element (the
 * CalorieBalanceCard number, a MacroSummaryCard row, a ScoreRing...) — the
 * "add proper animation/transition" ask from the app-wide polish pass,
 * applied to the single most important element per screen rather than
 * everywhere at once (a screen where every card animates in independently
 * reads as busy, not polished). [visible] flips to true once the element's
 * real data has actually loaded — pass a ViewModel's own loaded/non-null
 * check, not a hardcoded true, so this plays once per real entrance instead
 * of on every recomposition. Reduced-motion snaps straight to the end state
 * (same final alpha/scale either way) rather than skipping the element
 * entirely, so screen-reader users still see identical content, just
 * without the animated build-up.
 */
@Composable
fun rememberHeroEntrance(visible: Boolean): HeroEntranceState {
    val reduced = rememberReducedMotion()
    val spec = if (reduced) snap() else tween<Float>(durationMillis = 420, easing = ScoreRevealEasing)
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = spec, label = "heroEntranceAlpha")
    val scale by animateFloatAsState(targetValue = if (visible) 1f else 0.94f, animationSpec = spec, label = "heroEntranceScale")
    return HeroEntranceState(alpha, scale)
}

data class HeroEntranceState(val alpha: Float, val scale: Float)

/** Applies a [HeroEntranceState] to this Modifier's graphicsLayer in one call. */
fun Modifier.heroEntrance(state: HeroEntranceState): Modifier = this.graphicsLayer {
    alpha = state.alpha
    scaleX = state.scale
    scaleY = state.scale
}

/**
 * Subtle press-down scale feedback for a tappable card/surface — most
 * tappable surfaces in the app rely solely on Material's default ripple,
 * which reads as flat on a translucent glass surface (see ScanEatCard's own
 * doc comment); a small scale-down on press reinforces "this responds to
 * touch" the way a ripple alone doesn't on a low-contrast dark background.
 * Gated on [rememberReducedMotion] like every other new animation here.
 */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val reduced = rememberReducedMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}
