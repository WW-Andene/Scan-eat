package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.GapEntry
import fr.scanneat.domain.engine.dashboard.GapSuggestion
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.ChipBackgroundAccent
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.util.formatDecimal

/**
 * [onSuggestionClick] logs the suggested food (see DashboardViewModel.logGapSuggestion) — previously these chips had no action at all.
 *
 * User-requested: a shuffle button + more visible chips. GapEntry.suggestions now carries up
 * to 6 candidates (widened from 3 in DashboardGapAnalysis.closeTheGap - see its own comment),
 * of which 5 are shown at once here instead of the previous fixed 3 - both the larger pool and
 * the wider display window give the reshuffle below something real to vary, rather than just
 * re-ordering the same 3 chips every tap. [shuffleTick] is bumped by the refresh button and fed
 * into `remember(gap, shuffleTick)` below so each tap draws a fresh random 5-of-6 subset,
 * independent of the day-seeded (deterministic) shuffle closeTheGap() already does once per day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GapCloserCard(gaps: List<GapEntry>, onSuggestionClick: (GapSuggestion) -> Unit) {
  var shuffleTick by remember { mutableIntStateOf(0) }
  ScanEatCard(
    contentPadding = PaddingValues(Spacing.L),
    verticalArrangement = Arrangement.spacedBy(Spacing.SM),
  ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.dashboard_gap_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { shuffleTick++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.dashboard_gap_shuffle), tint = OnSurface.copy(0.6f), modifier = Modifier.size(IconSize.Small))
            }
        }
        gaps.take(3).forEach { gap ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                // design-aesthetic-audit §DC: every other amber-warning text in the app
                // (TemplateCard, HormonesEvolutionCard, HealthConditionCaution) pairs a
                // WarningAmber icon with the text - this was bare amber text alone.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(IconSize.Small))
                    Text(
                        stringResource(R.string.dashboard_gap_entry, nutrientLabel(gap.nutrient), gap.deficit.formatDecimal()),
                        style = MaterialTheme.typography.labelMedium, color = semanticAmber(),
                    )
                }
                val displayed = remember(gap, shuffleTick) { gap.suggestions.shuffled().take(5) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    displayed.forEach { s ->
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(CardRadius.CARD)).clickable { onSuggestionClick(s) },
                            shape = RoundedCornerShape(CardRadius.CARD),
                            color = ChipBackgroundAccent,
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

/**
 * Maps a GapEntry/ChronicGap.nutrient raw key (e.g. "vit_d", from DashboardAggregator's
 * GAP_NUTRIENTS/defs lists) to its localized display label. Previously interpolated directly
 * as gap.nutrient.replaceFirstChar { it.uppercase() }, which showed the raw internal key
 * itself regardless of app language — "Vit_d" (not even a real word) for a French user, or
 * "Protein" for either language since the key was never actually translated. Reuses the
 * same labels MicronutrientCard/MacroSummaryCard already have for these six nutrients.
 */
@Composable
internal fun nutrientLabel(key: String): String = when (key) {
    "protein" -> stringResource(R.string.diary_macro_protein)
    "fiber"   -> stringResource(R.string.dashboard_micro_fiber)
    "iron"    -> stringResource(R.string.dashboard_micro_iron)
    "calcium" -> stringResource(R.string.dashboard_micro_calcium)
    "vit_d"   -> stringResource(R.string.dashboard_micro_vitd)
    "b12"     -> stringResource(R.string.dashboard_micro_b12)
    else      -> key.replaceFirstChar { it.uppercase() }
}
