package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.NutriScoreGap
import fr.scanneat.presentation.ui.theme.*

/** Roadmap S11: why Scan'eat's grade differs from the Nutri-Score printed on the pack. */
@Composable
internal fun NutriScoreGapCard(gap: NutriScoreGap) {
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(if (gap.scanEatWorse) R.string.result_ns_gap_worse else R.string.result_ns_gap_better),
            style = MaterialTheme.typography.titleSmall, color = OnBackground)
        gap.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f)) }
        Text(stringResource(R.string.result_ns_gap_footer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
    }
}
