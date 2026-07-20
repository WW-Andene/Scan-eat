package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.HormoneResult
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.HormoneRow
import fr.scanneat.presentation.biolism.data.Label
import fr.scanneat.presentation.biolism.evolution.HormoneTrends
import fr.scanneat.presentation.biolism.evolution.LineTrendChart
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.biolism.evolution.SexPrimaryHormone
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.util.formatDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hormones are formula-estimated (see biolism_hormones_disclaimer on the Data
 * tab), never measured — and unlike weight/macros/hydration, nothing about a
 * PAST day's actual fasting/ketosis state is recorded, so a genuine day-to-day
 * hormone history isn't reconstructable. What's shown instead: today's
 * baseline estimate (ketoHours=0, fastingHours=0 — the Data tab's live value
 * factors in today's actual timer state, this one deliberately doesn't), plus
 * a trend recomputed the same way from real historical weight entries against
 * today's other measurements — isolating how the baseline shifts with body
 * composition alone, clearly captioned rather than implied to be a real
 * per-day hormone record.
 */
@Composable
fun HormonesEvolutionCard(today: HormoneResult?, trends: HormoneTrends, language: String) {
    val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale(language))
    BioCard(stringResource(R.string.biolism_hormones_title), defaultOpen = false) {
        Text(
            stringResource(R.string.biolism_evo_hormones_caption),
            style = MaterialTheme.typography.labelSmall,
            color = OnBackground.copy(0.5f),
            modifier = Modifier.background(OnBackground.copy(0.03f), RoundedCornerShape(6.dp)).padding(Spacing.S),
        )
        Spacer(Modifier.height(Spacing.S))
        if (today == null) {
            NotEnoughDataNote()
            return@BioCard
        }
        Label(stringResource(R.string.biolism_evo_hormones_today), Gold)
        HormoneRow(stringResource(R.string.biolism_hormones_cortisol), today.cortisol, "Bjorntorp 2000")
        when (trends.sexPrimaryKind) {
            SexPrimaryHormone.TESTOSTERONE -> HormoneRow(stringResource(R.string.biolism_hormones_testosterone), today.testosterone, "Harman 2001")
            SexPrimaryHormone.ESTRADIOL    -> HormoneRow(stringResource(R.string.biolism_hormones_estradiol), today.estradiol, "Santoro 2008")
            null -> {}
        }
        Spacer(Modifier.height(Spacing.S))
        if (trends.cortisol.size >= 2) {
            Label(stringResource(R.string.biolism_hormones_cortisol), Teal)
            LineTrendChart(
                points = trends.cortisol,
                color = Teal,
                dateFmt = fmt,
                valueLabel = { v -> "${v.formatDecimal()} ${today.cortisol.unit}" },
            )
            Spacer(Modifier.height(Spacing.M))
        }
        if (trends.sexPrimary.size >= 2) {
            val label = when (trends.sexPrimaryKind) {
                SexPrimaryHormone.TESTOSTERONE -> stringResource(R.string.biolism_hormones_testosterone)
                SexPrimaryHormone.ESTRADIOL    -> stringResource(R.string.biolism_hormones_estradiol)
                null -> ""
            }
            Label(label, Violet)
            LineTrendChart(
                points = trends.sexPrimary,
                color = Violet,
                dateFmt = fmt,
                valueLabel = { v -> v.formatDecimal() },
            )
        }
    }
}
