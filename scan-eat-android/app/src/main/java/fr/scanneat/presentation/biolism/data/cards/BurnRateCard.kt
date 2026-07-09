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
fun BurnRateCard(met: MetabolicResult, s: TimerState, cum: SessionCumulative?) {
    BioCard("Taux de combustion", badge = { TealBadge("RQ %.3f".format(met.sub.rq)) }) {
        MetCellGrid(listOf(
            Triple("Par seconde", "%.6f".format(met.kcalSec), "kcal/s"),
            Triple("Par minute", "%.4f".format(met.kcalSec * 60), "kcal/min"),
            Triple("Par heure", "%.2f".format(met.kcalSec * 3600), "kcal/h"),
            Triple("Par jour", "%.1f".format(met.bmrDay), "kcal/j"),
        ))
        Spacer(Modifier.height(6.dp))
        InfoRow("Répartition substrats", "G %d%% · Gluc %d%% · P %d%%".format(
            (met.sub.fatFrac * 100).toInt(), (met.sub.carbFrac * 100).toInt(), (met.sub.protFrac * 100).toInt()),
            "fraction de l'énergie totale", TextSecondary)
        InfoRow("Sortie thermodynamique", "%.4f W".format(met.watts), "", Gold)
        InfoRow("VO₂ consommé", "%.4f L/min".format(met.vo2PerMin), "", Violet)
        InfoRow("CO₂ produit", "%.4f L/min".format(met.vco2PerMin), "", TextSecondary)
        InfoRow("Équiv. oxycalorique", "%.4f kcal/L O₂".format(met.sub.oxycaloric), "", if (s.ketosisOn) Teal else TextSecondary)
        cum?.let { c ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(if (s.ketosisOn) Teal else Gold) {
                Label("Cumul de session", if (s.ketosisOn) Teal else Gold)
                InfoRow("kcal brûlées", "%.4f kcal".format(c.kcalTotal), "", if (s.ketosisOn) Teal else Gold)
                InfoRow("O₂ consommé", "%.4f L".format(c.o2LitersTotal), "", Violet)
                InfoRow("CO₂ produit", "%.4f L".format(c.co2LitersTotal), "", TextSecondary)
                InfoRow("Graisse oxydée", "%.2f mg".format(c.fatOxidisedMg), "", if (s.ketosisOn) Teal else Warm)
                if (s.ketosisOn) InfoRow("Glycogène déplété", "%.2f g".format(c.glycogenDepletedG), "+ %.2f g H₂O".format(c.glycogenWaterG), Gold)
                InfoRow("Protéines catabolisées", "%.2f mg".format(c.proteinCatabolisedMg), "%.2f mg N₂".format(c.n2ExcretedMg), Violet)
            }
        }
    }
}
