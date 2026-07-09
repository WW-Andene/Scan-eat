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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.BiolismSex
import fr.scanneat.domain.engine.biolism.MetabolicResult
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

// Formula/substituted-value strings intentionally stay as French literals —
// see the strings.xml comment above the biolism_eq_* block for why.
@Composable
fun EquationsCard(met: MetabolicResult, profile: BiolismProfile) {
    BioCard(stringResource(R.string.biolism_eq_title), defaultOpen = false,
        badge = { Badge(stringResource(R.string.biolism_eq_badge), TextSecondary) }) {
        Text(
            stringResource(R.string.biolism_eq_disclaimer),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
        )
        Spacer(Modifier.height(10.dp))
        EqBlock(stringResource(R.string.biolism_eq_bmr_msj), "10×poids + 6,25×taille − 5×âge + s",
            "10×%.1f + 6,25×%.1f − 5×%d %s = %.1f kcal/j".format(
                profile.weightKg, profile.heightCm, profile.ageYears,
                if (profile.sex == BiolismSex.MALE) "+ 5" else "− 161", met.bmrMsj))
        EqBlock(stringResource(R.string.biolism_eq_bmr_km), "370 + 21,6×masse maigre",
            "370 + 21,6×%.1f kg = %.1f kcal/j".format(met.ffm, met.bmrKm))
        EqBlock(stringResource(R.string.biolism_eq_bodyfat), "1,20×IMC + 0,23×âge − 10,8×sexe − 5,4 (IMC ajusté ethnie)",
            "= %.1f%% (offset %s : %+.1f)".format(met.bfPct, profile.ethnicMeta.label, profile.ethnicMeta.bmiOffset))
        EqBlock(stringResource(R.string.biolism_eq_bsa), "0,007184 × poids^0,425 × taille^0,725",
            "= %.4f m²".format(met.bsa))
        EqBlock(stringResource(R.string.biolism_eq_gas_exchange), "V̇O₂ = BMR / (équiv. oxycal. × 1440)",
            "%.4f L/min · RQ %.3f".format(met.vo2PerMin, met.sub.rq))
        EqBlock(stringResource(R.string.biolism_eq_substrate_ox), "kcal/min × fraction / densité énergétique (9,0 / 4,0 / 4,1 kcal/g)",
            "Graisses %.3f · Glucides %.3f · Prot. %.3f g/min".format(met.fatOxGPerMin, met.carbOxGPerMin, met.protOxGPerMin))
        EqBlock(stringResource(R.string.biolism_eq_ffa_flux), "oxyd. graisses / (1 − fraction réestérification)",
            "= %.4f g/min".format(met.ffaFluxGPerMin))
        EqBlock(stringResource(R.string.biolism_eq_bhb), "1 palmitate → 4 BHB (flux hépatique AGL / 0,256 × 4 × activation)",
            "= %.4f mmol/min".format(met.bhbMmolPerMin))
        EqBlock(stringResource(R.string.biolism_eq_acetyl_coa), "β-ox 8/palmitate + PDH 2/glucose + AA ~0,6",
            "= %.3f mmol/min total".format(met.acCoaTotalMmolMin))
        EqBlock(stringResource(R.string.biolism_eq_gluconeogenesis), "oxyd. protéines × fraction GNG × 0,58 g glucose/g protéine",
            "= %.3f g glucose/h".format(met.gngGPerHr))
        EqBlock(stringResource(R.string.biolism_eq_heat_dissipation), "rayonnement 45% + convection 30% + évaporation 23% + conduction 2%",
            "= %.3f W total".format(met.watts))
        EqBlock(stringResource(R.string.biolism_eq_water_loss), "resp. = V̇E×60×(44−10)/760×(18/22,4)×1000 · transépid. = 0,45×SC",
            "RWL %.2f + TEWL %.2f = %.2f mL/h".format(met.rwlMlPerHr, met.tewlMlPerHr, met.iwlMlPerHr))
        EqBlock(stringResource(R.string.biolism_eq_ibw), "moyenne des 3 formules linéaires (taille au-dessus de 152,4 cm)",
            "= %.1f kg (moyenne)".format(met.ibwMean))
        EqBlock(stringResource(R.string.biolism_eq_macro_floors), "protéines g/kg MMC selon activité · glucides 120g→30g en cétose >24h",
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
