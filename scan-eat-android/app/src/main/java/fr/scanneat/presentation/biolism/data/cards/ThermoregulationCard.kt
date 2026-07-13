package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun ThermoregulationCard(met: MetabolicResult) {
    BioCard(stringResource(R.string.biolism_thermo_title), defaultOpen = false,
        badge = { GoldBadge(stringResource(R.string.biolism_thermo_badge)) }) {
        Label(stringResource(R.string.biolism_thermo_dissipation_label, met.watts), OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple(stringResource(R.string.biolism_thermo_radiation), "%.4f W".format(Locale.US, met.heatRadW), stringResource(R.string.biolism_thermo_radiation_sub)),
                Triple(stringResource(R.string.biolism_thermo_convection), "%.4f W".format(Locale.US, met.heatConvW), stringResource(R.string.biolism_thermo_convection_sub)),
                Triple(stringResource(R.string.biolism_thermo_evaporation), "%.4f W".format(Locale.US, met.heatEvapW), stringResource(R.string.biolism_thermo_evaporation_sub)),
                Triple(stringResource(R.string.biolism_thermo_conduction), "%.4f W".format(Locale.US, met.heatCondW), stringResource(R.string.biolism_thermo_conduction_sub)),
            ),
            accents = listOf(Warm, Teal, Violet, TextMuted)
        )
        InfoRow(stringResource(R.string.biolism_thermo_transepidermal), "%.4f W".format(Locale.US, met.heatEvapSkinW), stringResource(R.string.biolism_thermo_transepidermal_sub), Violet)
        InfoRow(stringResource(R.string.biolism_thermo_respiratory), "%.4f W".format(Locale.US, met.heatEvapRespW), stringResource(R.string.biolism_thermo_respiratory_sub), Violet)
        Spacer(Modifier.height(8.dp))
        Label(stringResource(R.string.biolism_thermo_iwl_title), OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple(stringResource(R.string.biolism_thermo_rwl_label), "%.2f mL/h".format(Locale.US, met.rwlMlPerHr), stringResource(R.string.biolism_thermo_ml_per_day, met.rwlMlPerHr * 24)),
                Triple(stringResource(R.string.biolism_thermo_tewl_label), "%.2f mL/h".format(Locale.US, met.tewlMlPerHr), stringResource(R.string.biolism_thermo_ml_per_day, met.tewlMlPerHr * 24)),
                Triple(stringResource(R.string.biolism_thermo_iwl_total_label), "%.2f mL/h".format(Locale.US, met.iwlMlPerHr), stringResource(R.string.biolism_thermo_ml_per_day, met.iwlMlPerHr * 24)),
                Triple(stringResource(R.string.biolism_thermo_metwater_label), "%.2f mL/h".format(Locale.US, met.metWaterMlPerHr), "Hill 2004"),
            ),
            accents = listOf(Teal, Violet, Gold, Teal)
        )
        Spacer(Modifier.height(8.dp))
        TintedPanel(Teal) {
            Label(stringResource(R.string.biolism_thermo_balance_title), Teal)
            InfoRow(stringResource(R.string.biolism_thermo_metwater_full), stringResource(R.string.biolism_thermo_ml_h_and_day, met.metWaterMlPerHr, met.metWaterMlPerHr * 24), "", Teal)
            InfoRow(stringResource(R.string.biolism_thermo_insensible_losses), stringResource(R.string.biolism_thermo_ml_h_and_day, met.iwlMlPerHr, met.iwlMlPerHr * 24), stringResource(R.string.biolism_thermo_excl_urine_sweat), Warm)
            InfoRow(stringResource(R.string.biolism_thermo_net_balance), stringResource(R.string.biolism_thermo_ml_h_and_day_signed, met.netHydroBalMlPerHr, met.netHydroBalMlPerHr * 24),
                stringResource(R.string.biolism_thermo_net_balance_sub), if (met.netHydroBalMlPerHr >= 0) Teal else Warm)
        }
    }
}
