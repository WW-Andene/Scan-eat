package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale — same idea as IconSize.kt, applied to padding/gaps
 * instead of icon sizes. New call sites should reach for one of these
 * instead of another ad hoc *.dp literal; existing call sites are migrated
 * incrementally rather than in one sweep, same rollout IconSize.kt used.
 */
object Spacing {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    // Category E audit: 10dp was already the de facto standard for the
    // inner-item gap inside a card's own content column (Arrangement.spacedBy)
    // at ~35 call sites app-wide, just never named — closer to S than M and
    // used too consistently to be drift. Named here instead of snapped to S/M
    // so those call sites can move onto the token scale with zero visual change.
    val SM: Dp = 10.dp
    val M: Dp = 12.dp
    val L: Dp = 16.dp
    val XL: Dp = 24.dp
    // Covers the bottom-of-list spacer value shared identically across
    // GroceryScreen/ScanHistoryScreen/WeightScreen - without this tier the
    // scale topped out below the one value every list screen already agrees on.
    val XXL: Dp = 32.dp
}
