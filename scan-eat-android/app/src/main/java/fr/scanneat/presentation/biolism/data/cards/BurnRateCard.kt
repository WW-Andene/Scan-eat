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

@Composable
fun BurnRateCard(met: MetabolicResult, s: TimerState, cum: SessionCumulative?) {
    BioCard(stringResource(R.string.biolism_burn_title),
        badge = { TealBadge(stringResource(R.string.biolism_burn_badge, met.sub.rq)) }) {
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_burn_per_second), "%.6f".format(met.kcalSec), "kcal/s"),
            Triple(stringResource(R.string.biolism_burn_per_minute), "%.4f".format(met.kcalSec * 60), "kcal/min"),
            Triple(stringResource(R.string.biolism_burn_per_hour), "%.2f".format(met.kcalSec * 3600), "kcal/h"),
            Triple(stringResource(R.string.biolism_burn_per_day), "%.1f".format(met.bmrDay), stringResource(R.string.biolism_burn_per_day_unit)),
        ))
        Spacer(Modifier.height(6.dp))
        InfoRow(stringResource(R.string.biolism_burn_substrate_split), "G %d%% · Gluc %d%% · P %d%%".format(
            (met.sub.fatFrac * 100).toInt(), (met.sub.carbFrac * 100).toInt(), (met.sub.protFrac * 100).toInt()),
            stringResource(R.string.biolism_burn_substrate_split_note), TextSecondary)
        InfoRow(stringResource(R.string.biolism_burn_thermo_output), "%.4f W".format(met.watts), "", Gold)
        InfoRow(stringResource(R.string.biolism_burn_vo2), "%.4f L/min".format(met.vo2PerMin), "", Violet)
        InfoRow(stringResource(R.string.biolism_burn_co2), "%.4f L/min".format(met.vco2PerMin), "", TextSecondary)
        InfoRow(stringResource(R.string.biolism_burn_oxycal), "%.4f kcal/L O₂".format(met.sub.oxycaloric), "", if (s.ketosisOn) Teal else TextSecondary)
        cum?.let { c ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(if (s.ketosisOn) Teal else Gold) {
                Label(stringResource(R.string.biolism_burn_session_cum), if (s.ketosisOn) Teal else Gold)
                InfoRow(stringResource(R.string.biolism_burn_kcal_burned), "%.4f kcal".format(c.kcalTotal), "", if (s.ketosisOn) Teal else Gold)
                InfoRow(stringResource(R.string.biolism_burn_o2_consumed), "%.4f L".format(c.o2LitersTotal), "", Violet)
                InfoRow(stringResource(R.string.biolism_burn_co2), "%.4f L".format(c.co2LitersTotal), "", TextSecondary)
                InfoRow(stringResource(R.string.biolism_burn_fat_oxidised), "%.2f mg".format(c.fatOxidisedMg), "", if (s.ketosisOn) Teal else Warm)
                if (s.ketosisOn) InfoRow(stringResource(R.string.biolism_burn_glycogen_depleted), "%.2f g".format(c.glycogenDepletedG), "+ %.2f g H₂O".format(c.glycogenWaterG), Gold)
                InfoRow(stringResource(R.string.biolism_burn_protein_catabolised), "%.2f mg".format(c.proteinCatabolisedMg), "%.2f mg N₂".format(c.n2ExcretedMg), Violet)
            }
        }
    }
}
