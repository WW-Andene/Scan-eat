package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun DailyEnergyCard(met: MetabolicResult, profile: BiolismProfile, s: TimerState, sessions: List<BiolismSession>, todayIntakeKcal: Double) {
    BioCard("Énergie quotidienne (TDEE)",
        badge = { if (s.ketosisOn) TealBadge("KÉTO −${((1.0 - met.ketoSupprFactor) * 100).toInt()}%") }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Dépense Totale Journalière", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), letterSpacing = 1.sp)
            Text("%.1f".format(met.tdeeDay), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.W500), color = Gold)
            Text("kcal/jour · BMR × %.3f".format(met.tdeeDay / met.bmrDay.coerceAtLeast(1.0)), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        }
        InfoRow("Niveau d'activité", profile.activityMeta.label, profile.activityMeta.note, Gold)
        MetCellGrid(listOf(
            Triple("BMR moyen", "%.1f".format(met.bmrDay), "kcal/j (consensus)"),
            Triple("Mifflin-St Jeor", "%.1f".format(met.bmrMsj), "kcal/j"),
            Triple("Katch-McArdle", "%.1f".format(met.bmrKm), "kcal/j · masse maigre"),
        ) + if (s.ketosisOn) listOf(Triple("BMR supprimé", "%.1f".format(met.bmrDay * met.ketoSupprFactor), "kcal/j · T3 adj.")) else emptyList())
        Spacer(Modifier.height(6.dp))
        InfoRow("Déficit (−500 kcal)", "%.1f kcal/j".format(met.tdeeDay - 500), "~0,45 kg/semaine perdu", Teal)
        InfoRow("Surplus (+300 kcal)", "%.1f kcal/j".format(met.tdeeDay + 300), "objectif prise de masse maigre", Violet)

        // Real energy balance — sourced from the Diary's actual logged intake, so this
        // reflects real consumption rather than a separate manual entry system.
        // sessions only changes when a session is saved, but this scope recomposes
        // every second (live tick) — remember so it's not re-scanned 60x/minute
        // against the whole session history.
        val todaySessKcal = remember(sessions) { sessions.filter { isToday(it.timestamp) }.sumOf { it.kcalBurned } }
        val totalOut = met.tdeeDay + todaySessKcal
        val netBal = todayIntakeKcal - totalOut
        Spacer(Modifier.height(8.dp))
        TintedPanel(if (netBal > 200) Danger else if (netBal < -50) Teal else Gold) {
            Label("Solde énergétique réel — Aujourd'hui", if (netBal > 200) Danger else if (netBal < -50) Teal else Gold)
            InfoRow("Apport (Journal)", "%.0f kcal".format(todayIntakeKcal), "aliments scannés/consignés", Teal)
            InfoRow("Dépense (TDEE + sport)", "%.0f kcal".format(totalOut), "TDEE %.0f + sport %.0f".format(met.tdeeDay, todaySessKcal), Gold)
            InfoRow("Bilan net", "%+.0f kcal".format(netBal),
                if (netBal > 200) "EXCÉDENT" else if (netBal < -50) "DÉFICIT" else "ÉQUILIBRÉ",
                if (netBal > 200) Danger else if (netBal < -50) Teal else Gold)
            InfoRow("Impact estimé sur le poids", "%+.3f kg".format(netBal / 7700.0), "règle des 7700 kcal/kg · estimation informative", TextSecondary)
        }
    }
}
