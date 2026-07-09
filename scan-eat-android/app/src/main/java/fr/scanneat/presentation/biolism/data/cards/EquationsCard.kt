package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.BiolismSex
import fr.scanneat.domain.engine.biolism.MetabolicResult
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun EquationsCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard("Équations & sources", defaultOpen = false, badge = { Badge("RÉFÉRENCE", TextSecondary) }) {
        Text(
            "Chaque valeur affichée dans Biolism dérive d'une formule publiée. Ci-dessous : la formule, tes chiffres actuels substitués, et la source.",
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
        Spacer(Modifier.height(10.dp))
        EqBlock("BMR — Mifflin-St Jeor (1990)", "10×poids + 6,25×taille − 5×âge + s",
            "10×%.1f + 6,25×%.1f − 5×%d %s = %.1f kcal/j".format(
                profile.weightKg, profile.heightCm, profile.ageYears,
                if (profile.sex == BiolismSex.MALE) "+ 5" else "− 161", met.bmrMsj))
        EqBlock("BMR — Katch-McArdle (1996)", "370 + 21,6×masse maigre",
            "370 + 21,6×%.1f kg = %.1f kcal/j".format(met.ffm, met.bmrKm))
        EqBlock("Masse grasse — Deurenberg (1991)", "1,20×IMC + 0,23×âge − 10,8×sexe − 5,4 (IMC ajusté ethnie)",
            "= %.1f%% (offset %s : %+.1f)".format(met.bfPct, profile.ethnicMeta.label, profile.ethnicMeta.bmiOffset))
        EqBlock("Surface corporelle — DuBois (1916)", "0,007184 × poids^0,425 × taille^0,725",
            "= %.4f m²".format(met.bsa))
        EqBlock("Échanges gazeux — Weir (1949) / Frayn (1983)", "V̇O₂ = BMR / (équiv. oxycal. × 1440)",
            "%.4f L/min · RQ %.3f".format(met.vo2PerMin, met.sub.rq))
        EqBlock("Oxydation substrats — Atwater", "kcal/min × fraction / densité énergétique (9,0 / 4,0 / 4,1 kcal/g)",
            "Graisses %.3f · Glucides %.3f · Prot. %.3f g/min".format(met.fatOxGPerMin, met.carbOxGPerMin, met.protOxGPerMin))
        EqBlock("Flux lipolytique AGL — Wolfe (1990)", "oxyd. graisses / (1 − fraction réestérification)",
            "= %.4f g/min".format(met.ffaFluxGPerMin))
        EqBlock("β-hydroxybutyrate — McGarry & Foster (1980)", "1 palmitate → 4 BHB (flux hépatique AGL / 0,256 × 4 × activation)",
            "= %.4f mmol/min".format(met.bhbMmolPerMin))
        EqBlock("Acétyl-CoA — Berg, Tymoczko & Stryer (2015)", "β-ox 8/palmitate + PDH 2/glucose + AA ~0,6",
            "= %.3f mmol/min total".format(met.acCoaTotalMmolMin))
        EqBlock("Gluconéogenèse — Cahill (1966) / Jungas (1992)", "oxyd. protéines × fraction GNG × 0,58 g glucose/g protéine",
            "= %.3f g glucose/h".format(met.gngGPerHr))
        EqBlock("Dissipation thermique — Fanger (1970)", "rayonnement 45% + convection 30% + évaporation 23% + conduction 2%",
            "= %.3f W total".format(met.watts))
        EqBlock("Pertes hydriques — Moran (2007) / Pinnagoda (1990)", "resp. = V̇E×60×(44−10)/760×(18/22,4)×1000 · transépid. = 0,45×SC",
            "RWL %.2f + TEWL %.2f = %.2f mL/h".format(met.rwlMlPerHr, met.tewlMlPerHr, met.iwlMlPerHr))
        EqBlock("Poids idéal — Devine/Robinson/Miller", "moyenne des 3 formules linéaires (taille au-dessus de 152,4 cm)",
            "= %.1f kg (moyenne)".format(met.ibwMean))
        EqBlock("Planchers macros — Phillips & Van Loon (2011) / Cahill (2006)", "protéines g/kg MMC selon activité · glucides 120g→30g en cétose >24h",
            "P %.0fg · G %.0fg · L %.0fg".format(met.macroProtMinG, met.macroCarbMinG, met.macroFatMinG))
    }
}

@Composable
private fun EqBlock(title: String, formula: String, substituted: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Gold, fontWeight = FontWeight.SemiBold)
        Text(formula, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        Text(substituted, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f), fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(color = OnBackground.copy(0.06f))
}
