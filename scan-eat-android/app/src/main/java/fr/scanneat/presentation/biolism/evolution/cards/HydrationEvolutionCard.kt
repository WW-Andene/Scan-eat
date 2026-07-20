package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.InfoRow
import fr.scanneat.presentation.biolism.evolution.LineTrendChart
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.Teal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HydrationEvolutionCard(history: List<Pair<LocalDate, Int>>, goalMl: Int, language: String) {
    val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale(language))
    BioCard(stringResource(R.string.biolism_evo_hydration_title), defaultOpen = true) {
        if (history.size < 2) {
            NotEnoughDataNote()
            return@BioCard
        }
        val sorted = history.sortedBy { it.first }
        val avgMl = sorted.map { it.second }.average()
        val daysOnGoal = sorted.count { it.second >= goalMl }
        InfoRow(
            stringResource(R.string.biolism_evo_hydration_avg),
            stringResource(R.string.biolism_evo_hydration_ml, avgMl.toInt()),
            stringResource(R.string.biolism_evo_hydration_goal_hit, daysOnGoal, sorted.size),
            Teal,
        )
        LineTrendChart(
            points = sorted.map { it.first to it.second.toDouble() },
            color = Teal,
            dateFmt = fmt,
            valueLabel = { v -> stringResource(R.string.biolism_evo_hydration_ml, v.toInt()) },
            targetValue = goalMl.toDouble(),
            targetColor = Teal,
        )
    }
}
