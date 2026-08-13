package fr.scanneat.presentation.biolism.data

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.isLightBackground
import fr.scanneat.presentation.ui.theme.NotebookPaper
import fr.scanneat.presentation.ui.theme.notebookPenBorder
import fr.scanneat.presentation.ui.theme.rememberNotebookPostItStyle
import kotlin.random.Random
import fr.scanneat.presentation.ui.theme.rememberReducedMotion

/** Shared expand/collapse card shell for the Biolism Data screen's ~15 cards. */
@Composable
internal fun BioCard(
    title: String,
    defaultOpen: Boolean = true,
    // MetabolicHealthScoreCard is the Data tab's stated aggregate/entry-point
    // card (per its own doc comment) but rendered at the exact same visual
    // weight as its 15 siblings - no card on this screen ever used HERO-tier
    // emphasis the way Dashboard's CalorieBalanceCard does for its one focal
    // card. Mirrors ScanEatCard's own HeroGlassSpec (edgeAlpha 0.34, a
    // visible Gold border) rather than introducing a second set of numbers.
    emphasized: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(defaultOpen) }
    // TalkBack previously heard only "button" for this row - the ExpandLess/ExpandMore
    // icon swap was purely visual (contentDescription = null) with no semantics on the
    // Row itself, so neither the toggle affordance nor the current open/closed state
    // was announced. mergeDescendants keeps the title/badge text audible while adding
    // the expanded/collapsed state and a Button role, mirroring HydrationScreen's
    // goal-editor row (see HydrationScreen.kt ~line 171).
    val openStateDescription = stringResource(if (open) R.string.common_expanded else R.string.common_collapsed)
    // User-reported: "toutes les cartes n'ont pas été remplacées" - this card
    // is the shared shell for all ~15 Biolism Data cards, hand-rolled as its
    // own Surface() rather than ScanEatCard() (see this function's own doc
    // comment for why), so it was one of the biggest gaps left when only
    // ScanEatCard itself got the post-it treatment. rememberNotebookPostItStyle
    // returns null for every other theme, so this changes nothing outside
    // Notebook.
    // No rotation (see ScanEatCard.kt's own doc comment on why that caused
    // "certaine carte ce touche et ce superpose") and a sketched pen border
    // instead of a solid post-it fill, matching ScanEatCard's own current
    // Notebook treatment.
    val postIt = rememberNotebookPostItStyle(RoundedCornerShape(CardRadius.CARD))
    val cardShape = postIt?.shape ?: RoundedCornerShape(CardRadius.CARD)
    val sketchSeed = if (postIt != null) remember { Random.nextInt() } else 0
    Box(
        Modifier.fillMaxWidth()
            .glassSheen(
                edgeAlpha = if (postIt != null) 0f else if (emphasized) 0.34f else 0.16f,
                shape = cardShape,
                glowTint = if (emphasized) Gold else Color.White,
                glowAlpha = if (postIt != null) 0f else if (emphasized) 0.12f else 0.06f,
            ),
    ) {
        Surface(
            shape = cardShape,
            // design-aesthetic-audit: same fix as ScanEatCard.kt - SurfaceVariant sits
            // only ~1-3 RGB units from Background in Light theme, so this fill was
            // imperceptible there, leaving only the shadow visible as a disconnected
            // rectangle instead of a filled card.
            color = if (postIt != null) Color.Transparent else SurfaceVariant.copy(alpha = if (isLightBackground()) 0.85f else 0.42f),
            border = if (postIt == null && emphasized) BorderStroke(1.dp, Gold.copy(alpha = 0.22f)) else null,
            // same fix as ScanEatCard.kt: force the fill to hard-clip to its own shape
            // instead of relying on Surface's implicit clip, which doesn't reliably
            // match the shadow's rounded outline on every rendering path. Shadow also
            // now tinted (Modifier.shadow) instead of Surface's untinted shadowElevation.
            // Notebook (postIt != null): no shadow/clip silhouette at all - see
            // ScanEatCard.kt's own doc comment on why that plain box and the
            // hand-drawn ink box never lined up (offset "les card et les box
            // existe en même temps" bug); the sketch is the only visible shape.
            modifier = Modifier.fillMaxWidth()
                .then(if (postIt != null) Modifier else Modifier.shadow(elevation = if (emphasized) 10.dp else 6.dp, shape = cardShape).clip(cardShape))
                .then(if (postIt != null) Modifier.notebookPenBorder(postIt.color, sketchSeed) else Modifier),
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(Spacing.L)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { open = !open }
                        .semantics(mergeDescendants = true) {
                            stateDescription = openStateDescription
                            role = Role.Button
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Box(Modifier.width(2.dp).height(16.dp).background(Gold, RoundedCornerShape(1.dp)))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    badge?.invoke()
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Inline))
                }
                // Gated on rememberReducedMotion(), like every other prominent
                // animation in the app (see Motion.kt's own doc comment) -
                // previously left at Compose's default expand/fade regardless
                // of the system setting.
                val reduceMotion = rememberReducedMotion()
                AnimatedVisibility(
                    visible = open,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(),
                    exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
                ) {
                    Column(Modifier.padding(top = Spacing.S), content = content)
                }
            }
        }
    }
}
