package fr.scanneat.presentation.biolism

/**
 * Shared duration-decomposition helper.
 *
 * Both `tracker/TrackerScreenComponents.kt` (formatElapsed) and
 * `data/DataScreenComponents.kt` (formatDuration) independently re-implemented
 * the same "total seconds -> hours/minutes/seconds" breakdown. This extracts
 * that math into one place so the two screens only differ in how they render
 * the parts, not in how they compute them.
 */
internal data class HmsParts(val hours: Long, val minutes: Long, val seconds: Long)

internal fun hmsFromSeconds(totalSeconds: Long): HmsParts {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return HmsParts(h, m, s)
}
