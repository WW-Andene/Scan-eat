package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.repository.health.WeightEntry
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.GoldBadge
import fr.scanneat.presentation.biolism.data.InfoRow
import fr.scanneat.presentation.biolism.evolution.LineTrendChart
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.dispWeight
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun WeightEvolutionCard(entries: List<WeightEntry>, useImperial: Boolean, language: String) {
    val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale(language))
    BioCard(stringResource(R.string.biolism_evo_weight_title), defaultOpen = true, badge = { GoldBadge(stringResource(R.string.biolism_evo_window_badge)) }) {
        if (entries.size < 2) {
            NotEnoughDataNote()
            return@BioCard
        }
        val sorted = entries.sortedBy { it.date }
        val first = sorted.first().weightKg
        val last  = sorted.last().weightKg
        val delta = last - first
        InfoRow(
            stringResource(R.string.biolism_evo_weight_current),
            dispWeight(last, useImperial),
            stringResource(R.string.biolism_evo_weight_delta, if (delta >= 0) "+" else "", dispWeight(abs(delta), useImperial)),
            AccentCoral,
        )
        InfoRow(
            stringResource(R.string.biolism_evo_min_max),
            "${dispWeight(sorted.minOf { it.weightKg }, useImperial)} – ${dispWeight(sorted.maxOf { it.weightKg }, useImperial)}",
            "",
            OnBackground,
        )
        LineTrendChart(
            points = sorted.map { it.date to it.weightKg },
            color = AccentCoral,
            dateFmt = fmt,
            valueLabel = { v -> dispWeight(v, useImperial) },
        )
    }
}
