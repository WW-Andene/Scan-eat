package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.Label
import fr.scanneat.presentation.biolism.evolution.BodyCompPoint
import fr.scanneat.presentation.biolism.evolution.LineTrendChart
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Warm
import fr.scanneat.util.formatDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fat% and lean mass (a proxy for "muscle") have no dated history of their own —
 * BiolismProfile overwrites in place on every save, and MetabolicResult is
 * recomputed live, never stored per-date. This recomputes computeMetabolics()
 * for every real historical weight entry against the CURRENT waist/hip/neck/
 * height, so the chart is an estimate ("body-fat% if you'd weighed this much
 * with today's measurements"), not a per-day measurement — captioned as such.
 */
@Composable
fun BodyCompositionEvolutionCard(points: List<BodyCompPoint>, language: String) {
    val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale(language))
    BioCard(stringResource(R.string.biolism_evo_bodycomp_title), defaultOpen = true) {
        Text(
            stringResource(R.string.biolism_evo_bodycomp_caption),
            style = MaterialTheme.typography.labelSmall,
            color = OnBackground.copy(0.5f),
        )
        Spacer(Modifier.height(Spacing.S))
        if (points.size < 2) {
            NotEnoughDataNote()
            return@BioCard
        }
        Label(stringResource(R.string.biolism_evo_bodycomp_fat_label), Warm)
        LineTrendChart(
            points = points.map { it.date to it.bfPct },
            color = Warm,
            dateFmt = fmt,
            valueLabel = { v -> "${v.formatDecimal()}%" },
        )
        Spacer(Modifier.height(Spacing.M))
        Label(stringResource(R.string.biolism_evo_bodycomp_lean_label), Teal)
        LineTrendChart(
            points = points.map { it.date to it.ffmKg },
            color = Teal,
            dateFmt = fmt,
            valueLabel = { v -> "${v.formatDecimal()} kg" },
        )
    }
}
