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
    /**
     * §E1/genre audit (icon sizes): 18.dp turned out to be the de facto
     * standard for a small row-action icon (delete/edit/favorite glyphs in
     * dense list rows) at ~19 call sites, distinct from [Inline]'s role and
     * more common than it - never named, so those call sites couldn't move
     * onto the scale. Named at the exact existing value (zero visual change,
     * unlike a full-scale renumbering) so they can reference it directly.
     */
    val Compact: Dp = 18.dp

    /** Inline / label-adjacent icons (chip icons, list-row leading icons). */
    val Inline: Dp = 20.dp

    /** Navigation-bar icons — matches Material's native 24dp grid. */
    val Nav: Dp = 24.dp

    /** Empty-state icons — matches EmptyListState.kt's existing value. */
    val EmptyState: Dp = 40.dp
}
