package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.presentation.ui.theme.semanticAmber
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
        // Same unmissable-disclaimer treatment as the Data tab's HormonesCard -
        // these numbers are formula-estimated, never measured, and a muted
        // one-line caption was easy to scroll past before registering that.
        val warnColor = semanticAmber()
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
            modifier = Modifier.background(warnColor.copy(0.08f), RoundedCornerShape(6.dp)).padding(Spacing.S)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = warnColor, modifier = Modifier.size(IconSize.Inline))
            Text(
                stringResource(R.string.biolism_evo_hormones_caption),
                style = MaterialTheme.typography.labelSmall,
                color = OnBackground.copy(0.85f),
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(Spacing.S))
        if (today == null) {
            NotEnoughDataNote()
            return@BioCard
        }
        // Bound to a fresh local instead of relying on the null-check above to smart-cast
        // `today` across the nested valueLabel lambda below.
        val current = today
        Label(stringResource(R.string.biolism_evo_hormones_today), Gold)
        HormoneRow(stringResource(R.string.biolism_hormones_cortisol), current.cortisol, "Bjorntorp 2000")
        when (trends.sexPrimaryKind) {
            SexPrimaryHormone.TESTOSTERONE -> HormoneRow(stringResource(R.string.biolism_hormones_testosterone), current.testosterone, "Harman 2001")
            SexPrimaryHormone.ESTRADIOL    -> HormoneRow(stringResource(R.string.biolism_hormones_estradiol), current.estradiol, "Santoro 2008")
            null -> {}
        }
        Spacer(Modifier.height(Spacing.S))
        if (trends.cortisol.size >= 2) {
            Label(stringResource(R.string.biolism_hormones_cortisol), Teal)
            LineTrendChart(
                points = trends.cortisol,
                color = Teal,
                dateFmt = fmt,
                // Whole numbers, not one decimal - see HormoneRow's own comment on
                // why fabricated precision is worse than none for an estimate.
                valueLabel = { v -> "${v.formatDecimal(0)} ${current.cortisol.unit}" },
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
                valueLabel = { v -> v.formatDecimal(0) },
            )
        }
    }
}
