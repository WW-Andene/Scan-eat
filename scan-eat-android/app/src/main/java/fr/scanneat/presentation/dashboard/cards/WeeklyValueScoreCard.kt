package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.presentation.ui.theme.*

/**
 * R&D audit finding: value-score data (ValueScoreEstimator) lived in complete
 * isolation in the Expenses tab, never surfaced on Dashboard or anywhere else
 * - see DashboardViewModel.weeklyValueScoreCounts' own doc comment on why
 * this stays a separate rollup rather than folding into the food score
 * itself (price has nothing to do with nutritional quality).
 */
@Composable
internal fun WeeklyValueScoreCard(counts: Map<ValueScore, Int>) {
    if (counts.isEmpty()) return
    val goodCount = (counts[ValueScore.GREAT] ?: 0) + (counts[ValueScore.GOOD] ?: 0)
    val poorCount = counts[ValueScore.POOR] ?: 0
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), emphasis = CardEmphasis.SECONDARY) {
        Text(
            stringResource(R.string.dashboard_value_score_title),
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f), fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.XS))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            if (goodCount > 0) {
                Text(
                    stringResource(R.string.dashboard_value_score_good, goodCount),
                    style = MaterialTheme.typography.bodySmall, color = semanticGreen(), fontWeight = FontWeight.Medium,
                )
            }
            if (poorCount > 0) {
                Text(
                    stringResource(R.string.dashboard_value_score_poor, poorCount),
                    style = MaterialTheme.typography.bodySmall, color = AccentCoral, fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
