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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.LinearProgressIndicator
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
import fr.scanneat.domain.engine.nutrition.ImprovementTip
import fr.scanneat.domain.engine.nutrition.PillarSummary
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.domain.engine.nutrition.ScoreSummary
import fr.scanneat.domain.model.Severity
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.glassPopupSurface
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
import fr.scanneat.presentation.ui.theme.gradeColor
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
    val riskCount = hints.risks.size + hints.conditionRisks.size
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
    val isEmpty = hints.benefits.isEmpty() && hints.risks.isEmpty() && hints.conditionRisks.isEmpty() && hints.facts.isEmpty() &&
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
                modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
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

/** Grade badge + numeric score + verdict, at the top of the hint panel. */
@Composable
private fun ScoreHeader(summary: ScoreSummary) {
    val color = gradeColor(summary.grade)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(CardRadius.PROMINENT)).background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(summary.grade.label, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(
                stringResource(R.string.hint_score_value, summary.value),
                style = MaterialTheme.typography.labelLarge,
                color = OnBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(summary.verdict, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        }
    }
}

/** One pillar's score/max as a labeled mini progress bar - gives an at-a-glance
 *  sense of *where* the score comes from without opening the full Result screen. */
@Composable
private fun PillarBar(pillar: PillarSummary) {
    val ratio = if (pillar.max > 0) (pillar.score / pillar.max).toFloat().coerceIn(0f, 1f) else 0f
    val color = when {
        ratio >= 0.75f -> semanticGreen()
        ratio >= 0.4f -> semanticAmber()
        else -> semanticRed()
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.T2)) {
        Text(
            pillar.name,
            style = MaterialTheme.typography.bodySmall,
            color = OnBackground.copy(0.8f),
            modifier = Modifier.width(120.dp),
        )
        LinearProgressIndicator(
            progress = { ratio },
            color = color,
            trackColor = OnBackground.copy(0.1f),
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)),
        )
        Text(
            stringResource(R.string.hint_pillar_score, pillar.score.toInt(), pillar.max),
            style = MaterialTheme.typography.bodySmall,
            color = OnBackground.copy(0.6f),
            modifier = Modifier.padding(start = Spacing.S),
        )
    }
}

private fun severityColor(severity: Severity, red: Color, amber: Color, neutral: Color): Color = when (severity) {
    Severity.CRITICAL, Severity.MAJOR -> red
    Severity.MODERATE, Severity.MINOR -> amber
    Severity.INFO -> neutral
}

/**
 * Severity-colored rendering for [ImprovementTip]s - each line gets a colored
 * points badge instead of the generic bulleted plain-text [HintSection], so a
 * -12pt critical deduction visually stands out from a -1pt minor one instead
 * of both reading as an identical bullet.
 */
@Composable
private fun ImprovementTipsSection(title: String, tips: List<ImprovementTip>) {
    val red = semanticRed()
    val amber = semanticAmber()
    val neutral = OnBackground.copy(0.7f)
    Column(modifier = Modifier.padding(bottom = Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = amber, modifier = Modifier.padding(0.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = amber, fontWeight = FontWeight.Bold)
        }
        tips.forEach { tip ->
            val accent = severityColor(tip.severity, red, amber, neutral)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = Spacing.S)) {
                Box(
                    modifier = Modifier.padding(top = 2.dp).size(8.dp).clip(RoundedCornerShape(50)).background(accent),
                )
                Text(
                    tip.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBackground.copy(0.8f),
                    modifier = Modifier.weight(1f).padding(start = Spacing.S),
                )
                Text(
                    stringResource(R.string.hint_points_badge, tip.points.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = Spacing.S),
                )
            }
        }
    }
}

/**
 * Shared facts/cautions layout for scan-result dialogs that aren't full
 * food scoring — MedicationFound and NonConsumableFound in ScanScreen.kt
 * both use this instead of duplicating the section/scroll/divider
 * plumbing HintPanel already has above.
 */
@Composable
fun FactsCautionsColumn(facts: List<String>, cautions: List<String>) {
    val amber = semanticAmber()
    val neutral = OnBackground.copy(0.7f)
    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        if (cautions.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_risks), cautions, amber, TablerIcons.AlertTriangle)
        }
        if (cautions.isNotEmpty() && facts.isNotEmpty()) {
            HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
        }
        if (facts.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_facts), facts, neutral, TablerIcons.Bulb)
        }
    }
}

@Composable
internal fun HintSection(title: String, lines: List<String>, accent: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = Modifier.padding(bottom = Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(0.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
        }
        lines.forEach { line ->
            // Spacing.S (8dp), not XS (4dp) - each line here is often a full
            // wrapped sentence/paragraph rather than a short list item, and 4dp
            // read as a run-on wall of text with no visual break between one
            // bullet's wrapped second line and the next bullet's first line.
            Row(modifier = Modifier.padding(top = Spacing.S)) {
                Text("•  ", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                // weight(1f) so a wrapped second line stays within the dialog's
                // width instead of the un-weighted Text being measured at its
                // natural (unwrapped) width and overflowing the Row.
                Text(line, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f), modifier = Modifier.weight(1f))
            }
        }
    }
}
