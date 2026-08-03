package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * F9/F23 (docs/design-audit-step3-brand-identity.md, docs/design-audit-
 * step8-components-shape.md): the app had zero non-rectangular elements
 * anywhere — every shape in circulation was a rounded rectangle at one of
 * the three [CardRadius] tiers or a perfect circle. Per docs/design-audit-
 * art-direction-brief.md's "second skin" direction, one deliberately
 * asymmetric, organic (cell-like) shape is introduced as the app's one
 * signature non-rectangular gesture — reserved for the score ring's ambient
 * glow ([fr.scanneat.presentation.result.cards.ScoreRing]) only, never the
 * circular progress arc itself (which must stay a true circle — it's the
 * one place shape communicates a literal 0–100% quantity, so distorting it
 * would misrepresent the score, not just look different). Deliberately
 * subtle (46/54/48/52, not an exaggerated blob) per the user's explicit
 * "not cyberpunk/sci-fi, this app is for everyone" constraint — reads as
 * "softly alive," not as a shape a casual glance would even register as
 * asymmetric.
 */
val OrganicBlobShape = RoundedCornerShape(
    topStartPercent = 46, topEndPercent = 54,
    bottomEndPercent = 48, bottomStartPercent = 52,
)
