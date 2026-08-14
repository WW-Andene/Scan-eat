package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared corner-radius scale — same idea as Spacing.kt/IconSize.kt, applied to
 * RoundedCornerShape() literals.
 *
 * User-requested (app-wide, every tab, not theme-specific): CONTROL/CARD/
 * PROMINENT previously carried three different values (12/16/24dp) across
 * three role tiers (control vs. card vs. hero/header/footer). That produced
 * exactly the inconsistency reported — the floating header/bottom-nav chrome
 * (PROMINENT) read visibly rounder than an ordinary content card (CARD),
 * which in turn read rounder than a button or text field (CONTROL), so
 * "some cards look round, others look rectangular" depending only on which
 * role happened to render them, not on any deliberate per-surface choice.
 * All three now share one value so every rounded-rectangle surface in the
 * app — buttons, fields, chips, ordinary cards, bottom sheets, and the
 * floating header/footer chrome alike — reads as the same one shape. Kept
 * as three separate names (rather than collapsing to one constant) purely
 * so the ~600 existing call sites keep their own semantic role in the code
 * without a mechanical rename; only the value converged.
 */
object CardRadius {
    val CONTROL: Dp = 16.dp
    val CARD: Dp = 16.dp
    val PROMINENT: Dp = 16.dp

    // genre audit (shape/corners): 4.dp turned out to be the single most
    // common tinted-badge Surface radius in the app (13 exact-match sites:
    // Biolism tracker/data cards' colored status pills, WeightHistorySection/
    // FoodEntryRow/FoodSearchScreen/ExpensesScreen's small inline badges) -
    // distinct role from the small-decorative-radius exclusion above (those
    // are progress-bar ticks/dots with no text content), so this earns its
    // own named tier rather than folding into CONTROL/CARD/PROMINENT.
    val BADGE: Dp = 4.dp
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
 *
 * User-requested (padding/radius standardization pass): retuned to
 * 48/56/48/56 - keeps the asymmetric organic silhouette above while
 * landing every corner on an even percentage.
 */
val OrganicBlobShape = RoundedCornerShape(
    topStartPercent = 48, topEndPercent = 56,
    bottomEndPercent = 48, bottomStartPercent = 56,
)
