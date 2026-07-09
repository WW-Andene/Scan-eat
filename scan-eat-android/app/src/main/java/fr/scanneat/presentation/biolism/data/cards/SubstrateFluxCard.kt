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
fun SubstrateFluxCard(met: MetabolicResult, s: TimerState) {
    BioCard(stringResource(R.string.biolism_flux_title), badge = {
        if (s.ketosisOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).padding(top = 5.dp)); TealBadge(stringResource(R.string.biolism_flux_badge))
            }
        }
    }) {
        Label(stringResource(R.string.biolism_flux_ox_rate_title), OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple(stringResource(R.string.biolism_flux_fat_ox), "%.4f g/min".format(met.fatOxGPerMin), stringResource(R.string.biolism_flux_fat_kcal_g)),
                Triple(stringResource(R.string.biolism_flux_carb_ox), "%.4f g/min".format(met.carbOxGPerMin), stringResource(R.string.biolism_flux_carb_kcal_g)),
                Triple(stringResource(R.string.biolism_flux_protein_ox), "%.4f g/min".format(met.protOxGPerMin), stringResource(R.string.biolism_flux_protein_kcal_g)),
                Triple(stringResource(R.string.biolism_flux_ffa), "%.4f g/min".format(met.ffaFluxGPerMin), stringResource(R.string.biolism_flux_ffa_sub)),
            ),
            accents = listOf(Warm, OnBackground, Violet, Warm)
        )
        Spacer(Modifier.height(6.dp))
        InfoRow(stringResource(R.string.biolism_flux_acetyl_coa_total), "%.3f mmol/min".format(met.acCoaTotalMmolMin), "", Gold)
        InfoRow(stringResource(R.string.biolism_flux_beta_ox), "%.3f mmol/min".format(met.acCoaFatMmolMin), "", Warm)
        InfoRow(stringResource(R.string.biolism_flux_glycolysis), "%.3f mmol/min".format(met.acCoaCarbMmolMin), "", TextSecondary)
        InfoRow(stringResource(R.string.biolism_flux_protein_aa), "%.3f mmol/min".format(met.acCoaProtMmolMin), "", Violet)
        Spacer(Modifier.height(4.dp))
        InfoRow(stringResource(R.string.biolism_flux_bhb), if (met.bhbMmolPerMin > 0) "%.4f mmol/min".format(met.bhbMmolPerMin) else stringResource(R.string.biolism_flux_bhb_none),
            if (met.ketoActivation > 0) stringResource(R.string.biolism_flux_bhb_activation, met.ketoActivation * 100) else "", Teal)
        InfoRow(stringResource(R.string.biolism_flux_gluconeogenesis), "%.3f g glucose/h".format(met.gngGPerHr),
            stringResource(R.string.biolism_flux_gng_sub, met.gngProtFrac * 100), Gold)
    }
}
