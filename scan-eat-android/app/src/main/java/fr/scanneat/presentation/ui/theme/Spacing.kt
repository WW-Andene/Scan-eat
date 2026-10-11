package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ## Base-2 spacing/dimension scale (official)
 *
 * All dp-based spacing, icon, and radius tokens across the app must draw
 * from this single scale: {2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 196,
 * 256, 384, 512} dp. Each doubling (or clean intermediate step) keeps the
 * visual rhythm predictable and makes every dimension traceable back to one
 * system. The 196/256/384/512 tier extends the scale above 128dp to cover
 * large component widths/heights (dialog max sizes, ring diameters, widget
 * dimensions) that a previous pass had left as ad hoc literals; those were
 * folded onto the nearest scale value (ties rounded up) in this pass.
 *
 * Rules:
 * - Every ad hoc `.dp` literal in the codebase must be either a single
 *   value from this scale, or a reference to a named token (Spacing.*,
 *   IconSize.*, CardRadius.*) that itself resolves to one.
 * - A sum of tokens is allowed only when the code is already composing an
 *   offset from parts (e.g. `16.dp + 8.dp`) — and only when every token in
 *   the sum is a *distinct* value from the scale, used once. Repeating the
 *   same token (`16.dp + 16.dp`) or multiplying to reach a value is not
 *   allowed — pick the single closest scale value instead.
 * - Exceptions: `0.dp` (the null/no-op value, outside the scale by
 *   definition) and hairline borders (`1.dp` / `0.5.dp`) used for 1px
 *   dividers/strokes, which are legitimate and documented exceptions to
 *   keep line weight crisp rather than bumping it to 2.dp.
 *
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
    // Category E padding/gap audit: 1/2/3dp were all in live use for the same
    // "tight inline row" context (score deltas, legend dots, stat columns) —
    // three near-identical but distinct values for one visual purpose. Named
    // here as the floor of the scale rather than snapped up to XS (4dp), which
    // would visibly loosen those already-cramped layouts.
    val T2: Dp = 2.dp
    val XS: Dp = 4.dp
    // Base-2 scale migration: standardized from 6dp to 8dp.
    val S: Dp = 8.dp
    // Category E audit: 10dp was already the de facto standard for the
    // inner-item gap inside a card's own content column (Arrangement.spacedBy)
    // at ~35 call sites app-wide, just never named — closer to S than M and
    // used too consistently to be drift. Named here instead of snapped to S/M
    // so those call sites can move onto the token scale with zero visual change.
    val SM: Dp = 8.dp
    // Base-2 scale migration: standardized from 10dp to 12dp.
    val M: Dp = 12.dp
    val L: Dp = 16.dp
    // User-requested: standardized from 23dp to 24dp (even).
    val XL: Dp = 24.dp
    // Covers the bottom-of-list spacer value shared identically across
    // GroceryScreen/ScanHistoryScreen/WeightScreen - without this tier the
    // scale topped out below the one value every list screen already agrees on.
    val XXL: Dp = 32.dp
}

/**
 * User-reported: every DropdownMenu's popup opened flush against its
 * trigger (0dp gap, Compose's own default) - only 2 of the app's 10
 * DropdownMenu call sites had ever added a gap (CollapsibleFilterBar,
 * ExpensesDialogs' category picker), and even those used the tightest
 * token (Spacing.T2, 2dp - imperceptible at typical density). Named here
 * so every DropdownMenu's `offset = DpOffset(x = 0.dp, y = DROPDOWN_MENU_GAP)`
 * stays in sync instead of drifting per call site again.
 *
 * User-reported (round 2): Spacing.XS (4dp) still read as visually flush
 * against the trigger (e.g. Recipes' filter pill) - bumped to Spacing.S (6dp),
 * then user-requested (round 3): standardized to 10dp for every popup.
 *
 * User-reported (round 4): all 11 raw Material3 DropdownMenu call sites
 * (including the two mentioned above) were replaced with the shared
 * ScanEatDropdownMenu composable, which always anchors below its trigger -
 * see that file's own doc comment for why. This constant is now only ever
 * read from inside that one composable, not duplicated per call site.
 */
val DROPDOWN_MENU_GAP: Dp = Spacing.M
