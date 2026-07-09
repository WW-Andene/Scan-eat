package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun BodyCompositionCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard("Composition corporelle", defaultOpen = true, badge = { BmiChip(met) }) {
        MetCellGrid(listOf(
            Triple("IMC", "%.1f".format(met.bmi), "kg/m² · WHO 2000"),
            Triple("Graisse corporelle", "%.1f%%".format(met.bfPct), "Deurenberg 1991"),
            Triple("Masse maigre", "%.1f kg".format(met.ffm), "sans graisse"),
            Triple("Masse grasse", "%.1f kg".format(met.fm), "graisse adipocytaire"),
        ))
        // Navy tape (when available)
        met.navyBfPct?.let { navy ->
            Spacer(Modifier.height(8.dp))
            TintedPanel(Teal) {
                Label("Méthode Navy Tape — Hodgdon & Beckett 1984", Teal)
                MetCellGrid(listOf(
                    Triple("Navy BF%", "%.1f%%".format(navy), "mesuré au ruban"),
                    Triple("Navy masse maigre", "%.1f kg".format(met.navyFfm ?: 0.0), "masse maigre"),
                    Triple("Navy masse grasse", "%.1f kg".format(met.navyFm ?: 0.0), "masse grasse"),
                    Triple("Δ vs Deurenberg", "%+.1f%%".format(navy - met.bfPct), ""),
                ))
            }
        }
        // IBW
        Spacer(Modifier.height(8.dp))
        Label("Poids idéal", OnBackground.copy(0.4f))
        val ibwDelta = profile.weightKg - met.ibwMean
        MetCellGrid(listOf(
            Triple("Devine", "%.1f kg".format(met.ibwDevine), "1974"),
            Triple("Robinson", "%.1f kg".format(met.ibwRobinson), "1983"),
            Triple("Miller", "%.1f kg".format(met.ibwMiller), "1983"),
            Triple("Moyenne", "%.1f kg".format(met.ibwMean), "%s%.1f kg vs actuel".format(if (ibwDelta > 0) "+" else "", ibwDelta)),
        ))
        // Visceral
        Spacer(Modifier.height(8.dp))
        TintedPanel(Gold) {
            Label("Indicateurs graisse viscérale", Gold)
            met.whtr?.let { v ->
                InfoRow("Tour taille/Taille (WHtR)", "%.3f".format(v),
                    if (v < 0.40) "Mince" else if (v < 0.50) "Sain" else if (v < 0.60) "Risque central" else "Risque élevé",
                    if (v < 0.50) Teal else if (v < 0.60) Gold else Danger)
            } ?: Text("Ajouter Tour de taille dans Profil pour déverrouiller WHtR",
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
            met.whr?.let { v ->
                val thresh = if (profile.sex == BiolismSex.MALE) 0.90 else 0.85
                InfoRow("Tour taille/Hanches (WHR)", "%.3f".format(v),
                    if (v < thresh) "Faible risque" else "Risque élevé",
                    if (v < thresh) Teal else Danger)
            }
            met.bai?.let { v ->
                InfoRow("Indice Adiposité Corporelle", "%.1f%%".format(v), "Bergman 2011", Gold)
            }
        }
    }
}

@Composable
private fun BmiChip(m: MetabolicResult) {
    val color = when (m.bmiClass) {
        BiolismBmiCategory.NORMAL      -> Teal
        BiolismBmiCategory.UNDERWEIGHT -> Violet
        BiolismBmiCategory.OVERWEIGHT  -> Gold
        BiolismBmiCategory.OBESE       -> Danger
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(0.15f), border = BorderStroke(1.dp, color.copy(0.3f))) {
        Text(m.bmiClass.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}
