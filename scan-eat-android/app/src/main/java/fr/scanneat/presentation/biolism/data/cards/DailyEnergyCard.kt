package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun DailyEnergyCard(met: MetabolicResult, profile: BiolismProfile, s: TimerState, sessions: List<BiolismSession>, todayIntakeKcal: Double) {
    BioCard(stringResource(R.string.biolism_energy_title),
        badge = { if (s.ketosisOn) TealBadge(stringResource(R.string.biolism_energy_keto_badge, ((1.0 - met.ketoSupprFactor) * 100).toInt())) }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(R.string.biolism_energy_tdee_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), letterSpacing = 1.sp)
            Text("%.1f".format(met.tdeeDay), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.W500), color = Gold)
            Text(stringResource(R.string.biolism_energy_tdee_sub, met.tdeeDay / met.bmrDay.coerceAtLeast(1.0)), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        }
        InfoRow(stringResource(R.string.biolism_energy_activity_level), profile.activityMeta.label, profile.activityMeta.note, Gold)
        val kcalJ = stringResource(R.string.biolism_energy_kcal_j)
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_energy_bmr_avg), "%.1f".format(met.bmrDay), stringResource(R.string.biolism_energy_bmr_avg_sub)),
            Triple("Mifflin-St Jeor", "%.1f".format(met.bmrMsj), kcalJ),
            Triple("Katch-McArdle", "%.1f".format(met.bmrKm), stringResource(R.string.biolism_energy_km_sub)),
        ) + if (s.ketosisOn) listOf(Triple(stringResource(R.string.biolism_energy_bmr_suppressed), "%.1f".format(met.bmrDay * met.ketoSupprFactor), stringResource(R.string.biolism_energy_bmr_suppressed_sub))) else emptyList())
        Spacer(Modifier.height(6.dp))
        InfoRow(stringResource(R.string.biolism_energy_deficit), stringResource(R.string.biolism_energy_kcal_per_day_value, met.tdeeDay - 500), stringResource(R.string.biolism_energy_deficit_sub), Teal)
        InfoRow(stringResource(R.string.biolism_energy_surplus), stringResource(R.string.biolism_energy_kcal_per_day_value, met.tdeeDay + 300), stringResource(R.string.biolism_energy_surplus_sub), Violet)

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
            Label(stringResource(R.string.biolism_energy_balance_title), if (netBal > 200) Danger else if (netBal < -50) Teal else Gold)
            InfoRow(stringResource(R.string.biolism_energy_intake), "%.0f kcal".format(todayIntakeKcal), stringResource(R.string.biolism_energy_intake_sub), Teal)
            InfoRow(stringResource(R.string.biolism_energy_expenditure), "%.0f kcal".format(totalOut), stringResource(R.string.biolism_energy_expenditure_sub, met.tdeeDay, todaySessKcal), Gold)
            InfoRow(stringResource(R.string.biolism_energy_net_balance), "%+.0f kcal".format(netBal),
                if (netBal > 200) stringResource(R.string.biolism_energy_status_surplus) else if (netBal < -50) stringResource(R.string.biolism_energy_status_deficit) else stringResource(R.string.biolism_energy_status_balanced),
                if (netBal > 200) Danger else if (netBal < -50) Teal else Gold)
            InfoRow(stringResource(R.string.biolism_energy_weight_impact), "%+.3f kg".format(netBal / 7700.0), stringResource(R.string.biolism_energy_weight_impact_sub), TextSecondary)
        }
    }
}
