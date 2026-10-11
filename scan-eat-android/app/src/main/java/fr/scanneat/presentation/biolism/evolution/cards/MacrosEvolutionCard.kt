package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.DayBucket
import fr.scanneat.domain.engine.dashboard.RollupResult
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.evolution.BarSparkline
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.presentation.ui.theme.Warm
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.util.formatDecimal

/**
 * Sugar/salt/protein/carbs/fat intake, sourced from the same Diary history
 * the Dashboard's weekly/monthly rollups already use — real per-day history,
 * no derivation needed. Rendered as compact rows (a full-size line chart per
 * macro would be 5 stacked charts) matching the bar-sparkline the Data tab's
 * own GlobalSummaryCard already uses.
 */
@Composable
fun MacrosEvolutionCard(rollup: RollupResult?) {
    BioCard(stringResource(R.string.biolism_evo_macros_title), defaultOpen = false) {
        val days = rollup?.days ?: emptyList()
        if (days.count { it.count > 0 } < 2) {
            NotEnoughDataNote()
            return@BioCard
        }
        MacroRow(stringResource(R.string.biolism_evo_macro_sugar), days, Warm) { it.sugarsG }
        MacroRow(stringResource(R.string.result_nutri_salt), days, Violet) { it.saltG }
        // app-audit §E3: this card used its own protein=Teal/carbs=Gold/fat=AccentCoral
        // mapping, contradicting the semantic macro-color convention consistent across
        // every other macro display in the app (MacroSummaryCard, RecipeCard,
        // TemplateCard, ProfileMetricsPreviewCard, DiaryKcalBreakdownCard):
        // protein=green, carbs=coral, fat=gold. Same nutrients, different colors
        // depending on screen - realigned to the established mapping.
        MacroRow(stringResource(R.string.result_nutri_protein), days, semanticGreen()) { it.proteinG }
        MacroRow(stringResource(R.string.result_nutri_carbs), days, AccentCoral) { it.carbsG }
        MacroRow(stringResource(R.string.result_nutri_fat), days, Gold) { it.fatG }
    }
}

@Composable
private fun MacroRow(label: String, days: List<DayBucket>, color: Color, value: (DayBucket) -> Double) {
    val logged = days.filter { it.count > 0 }
    val avg = if (logged.isEmpty()) 0.0 else logged.map(value).average()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
            Text(
                stringResource(R.string.biolism_evo_macro_avg, avg.formatDecimal()),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Spacing.XS))
        BarSparkline(values = days.map(value), color = color)
        Spacer(Modifier.height(Spacing.S))
    }
}
