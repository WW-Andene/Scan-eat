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
    /** Inline / label-adjacent icons (chip icons, list-row leading icons). */
    val Inline: Dp = 20.dp

    /** Navigation-bar icons — matches Material's native 24dp grid. */
    val Nav: Dp = 24.dp

    /** Empty-state icons — matches EmptyListState.kt's existing value. */
    val EmptyState: Dp = 40.dp
}
