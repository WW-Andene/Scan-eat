package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 3-size icon scale the audit's Icon Character Brief calls for —
 * previously 13 distinct ad hoc values (12-64dp) with no shared token.
 * New icon call sites should pick one of these three; existing call sites
 * are migrated incrementally rather than all at once (a blanket resize
 * across ~85 sites risks visible layout regressions — cramped rows, clipped
 * icons — that can't be checked without a running device).
 */
object IconSize {
    /** Badge / trend-arrow glyphs inside chips and thumbnails. */
    val Tiny: Dp = 12.dp

    /** Row actions and warnings in dense lists and dialogs. */
    val Compact: Dp = 16.dp

    /** Inline / label-adjacent icons (chip icons, list-row leading icons). */
    val Inline: Dp = 24.dp

    /** Navigation-bar icons — matches Material's native 24dp grid. */
    val Nav: Dp = 24.dp

    /** Step / feature icons inside a hero card. */
    val Large: Dp = 32.dp

    /** Empty-state icons. */
    val EmptyState: Dp = 48.dp

    /** Full-screen illustrations (permission gate, welcome page). */
    val Hero: Dp = 64.dp
}
