package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal
import java.util.Locale

@Composable
fun BurnRateCard(met: MetabolicResult, s: TimerState, cum: SessionCumulative?) {
    BioCard(stringResource(R.string.biolism_burn_title),
        badge = { TealBadge(stringResource(R.string.biolism_burn_badge, met.sub.rq)) }) {
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_burn_per_second), met.kcalSec.formatDecimal(6), "kcal/s"),
            Triple(stringResource(R.string.biolism_burn_per_minute), (met.kcalSec * 60).formatDecimal(4), "kcal/min"),
            Triple(stringResource(R.string.biolism_burn_per_hour), (met.kcalSec * 3600).formatDecimal(2), "kcal/h"),
            Triple(stringResource(R.string.biolism_burn_per_day), met.bmrDay.formatDecimal(), stringResource(R.string.biolism_burn_per_day_unit)),
        ))
        Spacer(Modifier.height(6.dp))
        InfoRow(stringResource(R.string.biolism_burn_substrate_split), "G %d%% · Gluc %d%% · P %d%%".format(Locale.US, 
            (met.sub.fatFrac * 100).toInt(), (met.sub.carbFrac * 100).toInt(), (met.sub.protFrac * 100).toInt()),
            stringResource(R.string.biolism_burn_substrate_split_note), TextSecondary)
        InfoRow(stringResource(R.string.biolism_burn_thermo_output), "%.4f W".format(Locale.US, met.watts), "", Gold)
        InfoRow(stringResource(R.string.biolism_burn_vo2), "%.4f L/min".format(Locale.US, met.vo2PerMin), "", Violet)
        InfoRow(stringResource(R.string.biolism_burn_co2), "%.4f L/min".format(Locale.US, met.vco2PerMin), "", TextSecondary)
        InfoRow(stringResource(R.string.biolism_burn_oxycal), "%.4f kcal/L O₂".format(Locale.US, met.sub.oxycaloric), "", if (s.ketosisOn) Teal else TextSecondary)
        cum?.let { c ->
            Spacer(Modifier.height(Spacing.S))
            TintedPanel(if (s.ketosisOn) Teal else Gold) {
                Label(stringResource(R.string.biolism_burn_session_cum), if (s.ketosisOn) Teal else Gold)
                InfoRow(stringResource(R.string.biolism_burn_kcal_burned), "%.4f kcal".format(Locale.US, c.kcalTotal), "", if (s.ketosisOn) Teal else Gold)
                InfoRow(stringResource(R.string.biolism_burn_o2_consumed), "%.4f L".format(Locale.US, c.o2LitersTotal), "", Violet)
                InfoRow(stringResource(R.string.biolism_burn_co2), "%.4f L".format(Locale.US, c.co2LitersTotal), "", TextSecondary)
                InfoRow(stringResource(R.string.biolism_burn_fat_oxidised), "%.2f mg".format(Locale.US, c.fatOxidisedMg), "", if (s.ketosisOn) Teal else Warm)
                if (s.ketosisOn) InfoRow(stringResource(R.string.biolism_burn_glycogen_depleted), "%.2f g".format(Locale.US, c.glycogenDepletedG), "+ %.2f g H₂O".format(Locale.US, c.glycogenWaterG), Gold)
                InfoRow(stringResource(R.string.biolism_burn_protein_catabolised), "%.2f mg".format(Locale.US, c.proteinCatabolisedMg), "%.2f mg N₂".format(Locale.US, c.n2ExcretedMg), Violet)
            }
        }
    }
}
