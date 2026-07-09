package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs

@Composable
fun MacroTargetsCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard("Objectifs macros & nutrition", defaultOpen = false, badge = { GoldBadge("MINIMUMS JOURNALIERS") }) {
        InfoRow("TDEE", "%.0f kcal · BMR %.0f × %.3f · MMC %.1f kg".format(met.tdeeDay, met.bmrDay, profile.activityMeta.mult, met.ffm), "", Gold)
        Spacer(Modifier.height(4.dp))
        MacroTargetRow("Protéines", met.macroProtMinG, "g", "%.1f g/kg MMC × %.1f kg".format(met.protGPerKgFfm, met.ffm), Violet)
        MacroTargetRow("Glucides", met.macroCarbMinG, "g", if (met.macroCarbMinG < 50) "Cétose >24h · Cahill 2006" else "Besoin cérébral · IOM 2005", Teal)
        MacroTargetRow("Lipides", met.macroFatMinG, "g", "Résiduel TDEE · min. AGE = %.0f g".format(met.essentialFatMinG), Warm)

        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(10.dp), color = GoldHaze, border = BorderStroke(1.dp, GoldBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total minimum", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Text("prot + glucides + lipides", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.0f kcal".format(met.macroFloorKcal), style = MaterialTheme.typography.titleMedium, color = Gold, fontWeight = FontWeight.Bold)
                    val delta = met.macroFloorKcal - met.tdeeDay
                    Text("TDEE %.0f · %+.0f kcal".format(met.tdeeDay, delta), style = MaterialTheme.typography.labelSmall,
                        color = if (abs(delta) < 5) Teal else OnBackground.copy(0.5f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Label("Objectifs complémentaires", OnBackground.copy(0.4f))
        InfoRow("Eau", "≥ %.1f L".format(met.waterNeedL),
            "EFSA 2010 · ${if (profile.sex == BiolismSex.MALE) "2,5" else "2,0"} L${if (profile.activityMeta.mult >= 1.55) " + 0,5 L activité" else ""}", Teal)
        InfoRow("Fibres alimentaires", "≥ 25 g", "EFSA 2017 · apport suffisant pour transit & microbiote", Gold)
        InfoRow("Sodium", "1500–2300 mg", "WHO 2012 · cible AHA 1500 mg · limite NHANES 2300 mg", Warm)
        InfoRow("Potassium", "≥ ${if (profile.sex == BiolismSex.MALE) "3400" else "2600"} mg", "NASEM DRI 2019 · contre l'excrétion sodique", Violet)
    }
}
