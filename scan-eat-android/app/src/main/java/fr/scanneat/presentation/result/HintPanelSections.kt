package fr.scanneat.presentation.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.ImprovementTip
import fr.scanneat.domain.engine.nutrition.PillarSummary
import fr.scanneat.domain.engine.nutrition.ScoreSummary
import fr.scanneat.domain.model.Severity
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.gradeColor
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed

/** Grade badge + numeric score + verdict, at the top of the hint panel. */
@Composable
internal fun ScoreHeader(summary: ScoreSummary) {
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
internal fun PillarBar(pillar: PillarSummary) {
    val ratio = if (pillar.max > 0) (pillar.score / pillar.max).toFloat().coerceIn(0f, 1f) else 0f
    val color = when {
        ratio >= 0.75f -> semanticGreen()
        ratio >= 0.4f -> semanticAmber()
        else -> semanticRed()
    }
    // Label stacked above its bar rather than in a fixed-width side column -
    // the pillar names are full French phrases ("Niveau de transformation",
    // "Intégrité des ingrédients") that wrap past ~120dp, and a wrapped label
    // sharing a CenterVertically row with the bar threw the bar/score out of
    // alignment with the (now two-line) text next to it.
    Column(modifier = Modifier.padding(vertical = Spacing.XS)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(pillar.name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
            Text(
                stringResource(R.string.hint_pillar_score, pillar.score.toInt(), pillar.max),
                style = MaterialTheme.typography.bodySmall,
                color = OnBackground.copy(0.6f),
            )
        }
        LinearProgressIndicator(
            progress = { ratio },
            color = color,
            trackColor = OnBackground.copy(0.1f),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.T2).height(6.dp).clip(RoundedCornerShape(50)),
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
internal fun ImprovementTipsSection(title: String, tips: List<ImprovementTip>) {
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
