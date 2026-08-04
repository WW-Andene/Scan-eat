package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.WeeklyScoreSummary
import fr.scanneat.presentation.ui.theme.*

/**
 * Compact weekly nutrition-quality rollup - placed right under the calorie-
 * balance hero card so the app's score/grade system (its main differentiator
 * vs. calorie-only trackers) is visible without scrolling, instead of only
 * appearing per-item at the bottom of "Recent scans". See
 * [WeeklyScoreSummary]'s own doc comment for the full rationale.
 */
@Composable
internal fun WeeklyScoreCard(summary: WeeklyScoreSummary) {
    val gradeColor = gradeColor(summary.grade)
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.L)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = gradeColor.copy(0.2f)) {
                Text(
                    summary.grade.label,
                    modifier = Modifier.padding(horizontal = Spacing.SM, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = gradeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.dashboard_weekly_score_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    pluralStringResource(R.plurals.dashboard_weekly_score_scan_count, summary.scanCount, summary.scanCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface.copy(0.6f),
                )
            }
            Text("${summary.avgScore}/100", style = MaterialTheme.typography.titleMedium, color = gradeColor, fontWeight = FontWeight.Bold)
        }
    }
}
