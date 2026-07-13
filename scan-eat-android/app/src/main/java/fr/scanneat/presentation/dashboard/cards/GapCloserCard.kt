package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.GapEntry
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.AmberWarning
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GapCloserCard(gaps: List<GapEntry>) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.dashboard_gap_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            gaps.take(3).forEach { gap ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text(
                        stringResource(R.string.dashboard_gap_entry, gap.nutrient.replaceFirstChar { it.uppercase() }, "%.1f".format(gap.deficit)),
                        style = MaterialTheme.typography.labelMedium, color = AmberWarning,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        gap.suggestions.take(3).forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
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
  }
}
