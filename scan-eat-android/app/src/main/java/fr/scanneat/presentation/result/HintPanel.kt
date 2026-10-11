package fr.scanneat.presentation.result

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Bulb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * Self-contained lightbulb entry point for the hint panel - owns its own
 * open/closed state and shows the panel on tap. Previously ResultScreen was
 * the only place the hint panel was reachable from, via a plain always-amber
 * icon with no signal of what was behind it; a user with a declared allergy
 * or diet had to tap in blind to discover an allergen/diet-violation risk was
 * even there. This turns red with a count badge whenever [hints] carries a
 * risk, and its contentDescription speaks the actual counts to TalkBack
 * instead of a generic "view information."
 */
@Composable
fun HintIconButton(hints: ProductHints, modifier: Modifier = Modifier, iconSize: androidx.compose.ui.unit.Dp = 24.dp) {
    var showHints by remember { mutableStateOf(false) }
    val riskCount = hints.risks.size + hints.conditionRisks.size + hints.medicationRisks.size
    val hasRisks = riskCount > 0
    val totalCount = hints.benefits.size + riskCount + hints.facts.size + hints.pairWell.size + hints.avoidPairing.size + hints.improvementTips.size
    val baseLabel = stringResource(R.string.hint_cd_open)
    val cd = when {
        hasRisks -> stringResource(R.string.hint_cd_open_with_risks, baseLabel, pluralStringResource(R.plurals.hint_cd_risks_count, riskCount, riskCount))
        totalCount > 0 -> stringResource(R.string.hint_cd_open_with_count, baseLabel, pluralStringResource(R.plurals.hint_cd_info_count, totalCount, totalCount))
        else -> baseLabel
    }
    BadgedBox(
        modifier = modifier,
        badge = {
            if (hasRisks) Badge(containerColor = semanticRed()) { Text("$riskCount") }
        },
    ) {
        IconButton(onClick = { showHints = true }) {
            Icon(TablerIcons.Bulb, cd, tint = if (hasRisks) semanticRed() else semanticAmber(), modifier = Modifier.size(iconSize))
        }
    }
    if (showHints) {
        HintPanel(hints = hints, onDismiss = { showHints = false })
    }
}

/** The "💡" hint panel — benefits / risks / pairings / facts, each traced to a concrete product field (see ProductHints.kt). */
@Composable
fun HintPanel(hints: ProductHints, onDismiss: () -> Unit) {
    val green = semanticGreen()
    val amber = semanticAmber()
    val red = semanticRed()
    val neutral = OnBackground.copy(0.7f)
    val isEmpty = hints.benefits.isEmpty() && hints.risks.isEmpty() && hints.conditionRisks.isEmpty() && hints.medicationRisks.isEmpty() && hints.facts.isEmpty() &&
        hints.keyInfo.isEmpty() && hints.pairWell.isEmpty() && hints.avoidPairing.isEmpty() && hints.improvementTips.isEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.hint_panel_title), color = OnBackground) },
        text = {
            // A long ingredient list can produce enough benefit/risk/fact lines to
            // overflow AlertDialog's unconstrained text slot on a small screen —
            // scroll within a capped height instead of letting the dialog grow
            // past the viewport with no way to reach the close button.
            Column(
                modifier = Modifier.widthIn(max = 384.dp).heightIn(max = 512.dp).verticalScroll(rememberScrollState()),
            ) {
                // Score header + pillar breakdown up top so a hint read in
                // isolation still carries the score context it explains -
                // previously the panel had zero visible link to the grade.
                hints.scoreSummary?.let { summary ->
                    ScoreHeader(summary)
                    if (hints.pillarSummary.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = Spacing.S)) {
                            hints.pillarSummary.forEach { PillarBar(it) }
                        }
                    }
                    HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.S))
                }
                // Was a fixed pairwise chain of "is the section before me AND am I
                // non-empty" checks - fine for 3 sections, unreadable once pairWell/
                // avoidPairing made it 5. A running "was anything shown yet" flag
                // scales to any section count without combinatorial conditions.
                var shownAny = hints.scoreSummary != null
                @Composable fun section(title: String, lines: List<String>, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
                    if (lines.isEmpty()) return
                    if (shownAny) HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
                    HintSection(title, lines, accent, icon)
                    shownAny = true
                }
                section(stringResource(R.string.hint_section_information), hints.keyInfo, neutral, Icons.Rounded.Info)
                section(stringResource(R.string.hint_section_risks), hints.risks, amber, TablerIcons.AlertTriangle)
                section(stringResource(R.string.hint_section_condition_risks), hints.conditionRisks, red, TablerIcons.AlertCircle)
                // User-requested: medication+ingredient cross-reference, distinct from
                // conditionRisks above since it's driven by the active Medication list,
                // not Profile.healthConditions - see ProductHints.medicationRisks' own
                // doc comment.
                section(stringResource(R.string.hint_section_medication_risks), hints.medicationRisks, red, TablerIcons.AlertCircle)
                if (hints.improvementTips.isNotEmpty()) {
                    if (shownAny) HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
                    ImprovementTipsSection(stringResource(R.string.hint_section_improve), hints.improvementTips)
                    shownAny = true
                }
                section(stringResource(R.string.hint_section_benefits), hints.benefits, green, Icons.Rounded.ThumbUp)
                section(stringResource(R.string.hint_section_pair_well), hints.pairWell, green, Icons.Rounded.Restaurant)
                section(stringResource(R.string.hint_section_avoid_pairing), hints.avoidPairing, amber, Icons.Rounded.Block)
                section(stringResource(R.string.hint_section_facts), hints.facts, neutral, TablerIcons.Bulb)
                if (isEmpty) {
                    Text(stringResource(R.string.hint_panel_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) }
        },
    )
}

