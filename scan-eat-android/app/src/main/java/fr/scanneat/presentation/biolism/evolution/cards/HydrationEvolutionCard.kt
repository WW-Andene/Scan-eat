package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.InfoRow
import fr.scanneat.presentation.biolism.evolution.LineTrendChart
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.semanticBlue
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
        // art-direction-engine §ATMOSPHERE: this card used Teal, but the live Hydration
        // tracker (HydrationRingAndControls, RemindersCard's HydrationReminderCard) is
        // consistently semanticBlue() - the same metric read as a different accent
        // between live tracking and its own history chart.
        InfoRow(
            stringResource(R.string.biolism_evo_hydration_avg),
            stringResource(R.string.biolism_evo_hydration_ml, avgMl.toInt()),
            stringResource(R.string.biolism_evo_hydration_goal_hit, daysOnGoal, sorted.size),
            semanticBlue(),
        )
        LineTrendChart(
            points = sorted.map { it.first to it.second.toDouble() },
            color = semanticBlue(),
            dateFmt = fmt,
            valueLabel = { v -> stringResource(R.string.biolism_evo_hydration_ml, v.toInt()) },
            targetValue = goalMl.toDouble(),
            targetColor = semanticBlue(),
        )
    }
}
