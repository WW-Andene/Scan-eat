package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun SubstrateFluxCard(met: MetabolicResult, s: TimerState) {
    BioCard("Flux de substrats", badge = {
        if (s.ketosisOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(6.dp).padding(top = 5.dp)); TealBadge("LIVE")
            }
        }
    }) {
        Label("Taux d'oxydation", OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple("Oxyd. graisses", "%.4f g/min".format(met.fatOxGPerMin), "9,0 kcal/g"),
                Triple("Oxyd. glucides", "%.4f g/min".format(met.carbOxGPerMin), "4,0 kcal/g"),
                Triple("Oxyd. protéines", "%.4f g/min".format(met.protOxGPerMin), "4,1 kcal/g"),
                Triple("Flux AGL lipolyse", "%.4f g/min".format(met.ffaFluxGPerMin), "adipose→plasma"),
            ),
            accents = listOf(Warm, OnBackground, Violet, Warm)
        )
        Spacer(Modifier.height(6.dp))
        InfoRow("Acétyl-CoA (total)", "%.3f mmol/min".format(met.acCoaTotalMmolMin), "", Gold)
        InfoRow("  ↳ β-oxydation", "%.3f mmol/min".format(met.acCoaFatMmolMin), "", Warm)
        InfoRow("  ↳ glycolyse (PDH)", "%.3f mmol/min".format(met.acCoaCarbMmolMin), "", TextSecondary)
        InfoRow("  ↳ protéines (AA)", "%.3f mmol/min".format(met.acCoaProtMmolMin), "", Violet)
        Spacer(Modifier.height(4.dp))
        InfoRow("β-Hydroxybutyrate", if (met.bhbMmolPerMin > 0) "%.4f mmol/min".format(met.bhbMmolPerMin) else "— (pas de cétose)",
            if (met.ketoActivation > 0) "activation CPT-I %.0f%% · McGarry 1980".format(met.ketoActivation * 100) else "", Teal)
        InfoRow("Gluconéogenèse", "%.3f g glucose/h".format(met.gngGPerHr),
            "%.0f%% du catabolisme protéique → GNG · Cahill 1966".format(met.gngProtFrac * 100), Gold)
    }
}
