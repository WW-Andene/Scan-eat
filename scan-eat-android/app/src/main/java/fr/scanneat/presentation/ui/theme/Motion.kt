package fr.scanneat.presentation.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The one signature easing curve, reserved for the score-reveal moment
 * (Result screen) — everything else keeps Compose's default easing.
 * A soft overshoot-free ease-out: quick to start, settles gently, matching
 * the "quiet, confident" character rather than a bouncy/playful spring.
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
    return Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
