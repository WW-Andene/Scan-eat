package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale — same idea as IconSize.kt, applied to padding/gaps
 * instead of icon sizes. New call sites should reach for one of these
 * instead of another ad hoc *.dp literal; existing call sites are migrated
 * incrementally rather than in one sweep, same rollout IconSize.kt used.
 *
 * F28 (docs/design-audit-step10-hierarchy-responsive.md): this scale used to
 * be 4/8/10/12/16/24/32 — a set of individually-reasonable but geometrically
 * irregular jumps (×2, ×1.25, ×1.2, ×1.33, ×1.5, ×1.33), which limited how
 * much "section vs. component" rhythmic contrast the scale could carry (see
 * docs/design-audit-art-direction-brief.md §COMPOSITION). Retuned to a true
 * geometric progression, ratio √2 ≈ 1.414, anchored at the same two endpoints
 * (4dp, 32dp) so the scale's overall range is unchanged: 4 → 6 → 8 → 11 → 16
 * → 23 → 32. Four of the seven values move by only 1-2dp from their prior
 * literal (S 8→6, SM 10→8, M 12→11, XL 24→23); XS/L/XXL are untouched. Visual
 * impact should be minimal and is worth confirming visually before relying
 * on this note alone.
 */
object Spacing {
    val XS: Dp = 4.dp
    val S: Dp = 6.dp
    // Category E audit: 10dp was already the de facto standard for the
    // inner-item gap inside a card's own content column (Arrangement.spacedBy)
    // at ~35 call sites app-wide, just never named — closer to S than M and
    // used too consistently to be drift. Named here instead of snapped to S/M
    // so those call sites can move onto the token scale with zero visual change.
    val SM: Dp = 8.dp
    val M: Dp = 11.dp
    val L: Dp = 16.dp
    val XL: Dp = 23.dp
    // Covers the bottom-of-list spacer value shared identically across
    // GroceryScreen/ScanHistoryScreen/WeightScreen - without this tier the
    // scale topped out below the one value every list screen already agrees on.
    val XXL: Dp = 32.dp
}
