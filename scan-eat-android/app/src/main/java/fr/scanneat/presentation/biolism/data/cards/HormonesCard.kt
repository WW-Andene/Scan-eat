package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun HormonesCard(h: HormoneResult, s: TimerState, met: MetabolicResult, profile: BiolismProfile) {
    BioCard("Hormones", defaultOpen = false, badge = { GoldBadge("ESTIMATION") }) {
        Text("Estimations basées sur âge, sexe, composition corporelle et contexte métabolique. Pas un substitut à une analyse sanguine.",
            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
            modifier = Modifier.background(OnBackground.copy(0.03f), RoundedCornerShape(6.dp)).padding(8.dp))
        Spacer(Modifier.height(8.dp))

        Label("Hormones sexuelles", Gold)
        listOf(
            Triple("Testostérone", h.testosterone, "Harman 2001"),
            Triple("Estradiol (E2)", h.estradiol, "Santoro 2008"),
            h.progesterone?.let { Triple("Progestérone", it, "Speroff 2011") },
            Triple("DHEA-S", h.dheaS, "Orentreich 1984"),
        ).filterNotNull().forEach { (name, reading, note) ->
            HormoneRow(name, reading, note)
        }
        Spacer(Modifier.height(8.dp))
        Label("Hormones métaboliques", Teal)
        listOf(
            Triple("Insuline", h.insulin, "Phinney 2012"),
            Triple("Glucagon", h.glucagon, "Unger 1978"),
            Triple("Cortisol", h.cortisol, "Bjorntorp 2000"),
            Triple("T3 libre", h.fT3, "Phinney 1983"),
        ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }
        Spacer(Modifier.height(8.dp))
        Label("Appétit & croissance", Violet)
        listOf(
            Triple("Leptine", h.leptin, "Considine 1996"),
            Triple("Ghréline", h.ghrelin, "Tschop 2000"),
            Triple("GH (moyen/j)", h.gh, "Hartman 1992"),
            Triple("IGF-1", h.igf1, "Giustina 2008"),
        ).forEach { (name, reading, note) -> HormoneRow(name, reading, note) }

        val kh = s.ketoHours; val fh = s.fastingHours
        val modifiers = buildList {
            if (kh > 24) add(Triple("Cétose (${kh.toInt()}h) →", "T↑ · Insuline↓ · Glucagon↑ · T3↓ · Leptine↓", Teal))
            if (fh > 12) add(Triple("Jeûne (${fh.toInt()}h) →", "GH↑ · Glucagon↑ · Cortisol↑ · Ghréline↑", Violet))
            if (s.ketoAdapted) add(Triple("Céto-adapté →", "Ghréline↓↓ · Leptine adaptée · T3 nadir", Gold))
            if (met.bfPct > 25) add(Triple("Masse grasse élevée (${met.bfPct.toInt()}%) →",
                "${if (profile.sex == BiolismSex.MALE) "T↓ · E2↑" else "E2↑"} · Résistance insuline ↑", Warm))
        }
        if (modifiers.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            TintedPanel(OnBackground) {
                Label("Modificateurs actifs", OnBackground.copy(0.4f))
                modifiers.forEach { (label, tags, color) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                        Text(tags, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
