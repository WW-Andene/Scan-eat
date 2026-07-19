package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.GapEntry
import fr.scanneat.domain.engine.dashboard.GapSuggestion
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.CardRadius
import java.util.Locale

/** [onSuggestionClick] logs the suggested food (see DashboardViewModel.logGapSuggestion) — previously these chips had no action at all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GapCloserCard(gaps: List<GapEntry>, onSuggestionClick: (GapSuggestion) -> Unit) {
  ScanEatCard(
    color = SurfaceVariant,
    contentPadding = PaddingValues(Spacing.L),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
        Text(stringResource(R.string.dashboard_gap_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        gaps.take(3).forEach { gap ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(
                    stringResource(R.string.dashboard_gap_entry, gap.nutrient.replaceFirstChar { it.uppercase() }, "%.1f".format(Locale.US, gap.deficit)),
                    style = MaterialTheme.typography.labelMedium, color = semanticAmber(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    gap.suggestions.take(3).forEach { s ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(CardRadius.CARD)).clickable { onSuggestionClick(s) },
                            shape = RoundedCornerShape(CardRadius.CARD),
                            color = AccentCoral.copy(0.15f),
                        ) {
                            Text(
                                stringResource(R.string.dashboard_gap_suggestion, s.name, s.grams),
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = AccentCoral,
                            )
                        }
                    }
                }
            }
        }
  }
}
