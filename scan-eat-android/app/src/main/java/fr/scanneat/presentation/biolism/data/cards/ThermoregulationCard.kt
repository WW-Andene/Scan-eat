package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun ThermoregulationCard(met: MetabolicResult) {
    BioCard("Thermorégulation & chaleur", defaultOpen = false, badge = { GoldBadge("FANGER 1970") }) {
        Label("Dissipation thermique — %.3f W total".format(met.watts), OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple("Rayonnement", "%.4f W".format(met.heatRadW), "~45% · IR longwave"),
                Triple("Convection", "%.4f W".format(met.heatConvW), "~30% · naturelle"),
                Triple("Évaporation", "%.4f W".format(met.heatEvapW), "~23% · peau + resp."),
                Triple("Conduction", "%.4f W".format(met.heatCondW), "~2% · contact direct"),
            ),
            accents = listOf(Warm, Teal, Violet, TextMuted)
        )
        InfoRow("  ↳ transépidermique", "%.4f W".format(met.heatEvapSkinW), "~15% · diffusion cutanée", Violet)
        InfoRow("  ↳ respiratoire", "%.4f W".format(met.heatEvapRespW), "~8% · évaporation respiratoire", Violet)
        Spacer(Modifier.height(8.dp))
        Label("Pertes hydriques insensibles", OnBackground.copy(0.4f))
        MetCellGrid(
            listOf(
                Triple("Resp. (RWL)", "%.2f mL/h".format(met.rwlMlPerHr), "%.0f mL/j".format(met.rwlMlPerHr * 24)),
                Triple("Transépidermique", "%.2f mL/h".format(met.tewlMlPerHr), "%.0f mL/j".format(met.tewlMlPerHr * 24)),
                Triple("Total (IWL)", "%.2f mL/h".format(met.iwlMlPerHr), "%.0f mL/j".format(met.iwlMlPerHr * 24)),
                Triple("Eau métab. produite", "%.2f mL/h".format(met.metWaterMlPerHr), "Hill 2004"),
            ),
            accents = listOf(Teal, Violet, Gold, Teal)
        )
        Spacer(Modifier.height(8.dp))
        TintedPanel(Teal) {
            Label("Bilan hydrique — turnover", Teal)
            InfoRow("Eau métabolique produite", "%.2f mL/h · %.0f mL/j".format(met.metWaterMlPerHr, met.metWaterMlPerHr * 24), "", Teal)
            InfoRow("Pertes insensibles", "%.2f mL/h · %.0f mL/j".format(met.iwlMlPerHr, met.iwlMlPerHr * 24), "hors urine et sueur", Warm)
            InfoRow("Bilan net", "%+.2f mL/h · %+.0f mL/j".format(met.netHydroBalMlPerHr, met.netHydroBalMlPerHr * 24),
                "métab. − insensibles", if (met.netHydroBalMlPerHr >= 0) Teal else Warm)
        }
    }
}
