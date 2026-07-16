package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Deduction
import fr.scanneat.domain.model.PillarScore
import fr.scanneat.domain.model.ScoreAudit
import fr.scanneat.domain.model.Severity
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.Spacing
import kotlin.math.abs

@Composable
internal fun PillarsSection(pillars: ScoreAudit.Pillars) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.result_pillars_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        listOf(pillars.processing, pillars.nutritionalDensity, pillars.negativeNutrients,
               pillars.additiveRisk, pillars.ingredientIntegrity).forEach { PillarRow(it) }
    }
}

/** CRITICAL/MAJOR deductions promote to the top-level red-flag list, bonuses to green flags (see buildFlags) - everything else, MINOR/MODERATE deductions included, was previously invisible: a user staring at "Nutritional density: 8/20" had no way to see why. */
@Composable
private fun PillarRow(pillar: PillarScore) {
    val ratio = (pillar.score.toFloat() / pillar.max.toFloat()).coerceIn(0f, 1f)
    val color = when { ratio >= 0.7f -> semanticGreen(); ratio >= 0.4f -> semanticAmber(); else -> semanticRed() }
    val reasons = (pillar.deductions + pillar.bonuses).sortedByDescending { abs(it.points) }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .let { if (reasons.isNotEmpty()) it.clickable { expanded = !expanded } else it },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(pillar.name, style = MaterialTheme.typography.labelMedium, color = OnBackground)
                if (reasons.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                        tint = OnBackground.copy(0.4f), modifier = Modifier.size(IconSize.Inline),
                    )
                }
            }
            Text(stringResource(R.string.result_pillar_score, pillar.score.toInt(), pillar.max), style = MaterialTheme.typography.labelMedium,
                color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(Spacing.XS))
        LinearProgressIndicator(
            progress   = { ratio },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = SurfaceVariant,
        )
        if (expanded) {
            Column(modifier = Modifier.padding(top = Spacing.XS), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                reasons.forEach { d -> ReasonRow(d) }
            }
        }
    }
}

@Composable
private fun ReasonRow(d: Deduction) {
    val color = reasonColor(d)
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            if (d.points > 0) "+${"%.0f".format(d.points)}" else "%.0f".format(d.points),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp),
        )
        Text(d.reason, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.7f), modifier = Modifier.weight(1f))
    }
}

/** Bonuses are always Severity.INFO by construction (see ScoringEngine.computeGlobalBonuses/pillar sources) - only sign matters there. Deductions use severity to grade how bad the concern is. */
@Composable
private fun reasonColor(d: Deduction): Color = when {
    d.points > 0 -> semanticGreen()
    d.severity == Severity.CRITICAL || d.severity == Severity.MAJOR -> semanticRed()
    else -> semanticAmber()
}
