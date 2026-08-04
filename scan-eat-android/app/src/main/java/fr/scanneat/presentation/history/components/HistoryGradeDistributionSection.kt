package fr.scanneat.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Grade
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HistoryGradeDistributionSection(gradeDistribution: List<Pair<String, Int>>) {
    val total = gradeDistribution.sumOf { it.second }.coerceAtLeast(1)
    Text(
        stringResource(R.string.history_grade_distribution_title),
        style = MaterialTheme.typography.labelMedium,
        color = OnBackground.copy(0.5f),
        modifier = Modifier.padding(top = Spacing.S, bottom = Spacing.XS),
    )
    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        gradeDistribution.forEach { (grade, count) ->
            // Was a second, independent grade->color mapping (green/amber/coral/red)
            // that could silently disagree with gradeColor()'s own colorblind-safe
            // palettes - this section's bands ("A"/"B"/"C"/"D") already line up
            // 1:1 with Grade's own labels, so route through the shared accessor.
            val color = gradeColor(Grade.fromLabel(grade))
            Box(Modifier.weight(count.toFloat() / total).fillMaxHeight().background(color))
        }
    }
    Spacer(Modifier.height(Spacing.XS))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        gradeDistribution.forEach { (grade, count) ->
            // Was a second, independent grade->color mapping (green/amber/coral/red)
            // that could silently disagree with gradeColor()'s own colorblind-safe
            // palettes - this section's bands ("A"/"B"/"C"/"D") already line up
            // 1:1 with Grade's own labels, so route through the shared accessor.
            val color = gradeColor(Grade.fromLabel(grade))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
                Text("$grade $count", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}
