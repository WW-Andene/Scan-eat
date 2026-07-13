package fr.scanneat.presentation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared corner-radius scale — same idea as Spacing.kt/IconSize.kt, applied to
 * RoundedCornerShape() literals. An app-wide audit found 12 distinct radius
 * values in circulation with no documented scale; most of that spread was
 * accidental drift between near-identical values (10/12/14dp) rather than
 * a real design need. Three role-based tiers cover the actual usage:
 *   - CONTROL: buttons, text fields, chips, badges, list-row cards, banners —
 *     the app's overwhelming default (the vast majority of call sites already
 *     agree on this value).
 *   - CARD: ScanEatCard's default and the plain-Surface "dashboard widget"
 *     cards (GapCloserCard, WeeklyBarsCard, WeightSummaryCard, ...).
 *   - PROMINENT: bottom sheets/modals and hero/featured cards that
 *     deliberately want a larger, softer radius than an ordinary card.
 * Small decorative radii (progress-bar segments, tick marks, tiny dots) and
 * one-off shapes (pills, camera overlays) are intentionally not folded into
 * this scale — those are a different role, not drift.
 */
object CardRadius {
    val CONTROL: Dp = 12.dp
    val CARD: Dp = 16.dp
    val PROMINENT: Dp = 20.dp
}
