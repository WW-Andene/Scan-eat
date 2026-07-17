package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.data.repository.biolism.BiolismRepository.TimerState
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun DailyEnergyCard(met: MetabolicResult, profile: BiolismProfile, s: TimerState, sessions: List<BiolismSession>, todayIntakeKcal: Double) {
    BioCard(stringResource(R.string.biolism_energy_title),
        badge = { if (s.ketosisOn) TealBadge(stringResource(R.string.biolism_energy_keto_badge, ((1.0 - met.ketoSupprFactor) * 100).toInt())) }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
            Text(stringResource(R.string.biolism_energy_tdee_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f), letterSpacing = 1.sp)
            Text("%.1f".format(Locale.US, met.tdeeDay), style = HeroNumberStyle.copy(fontSize = 34.sp), color = Gold)
            Text(stringResource(R.string.biolism_energy_tdee_sub, met.tdeeDay / met.bmrDay.coerceAtLeast(1.0)), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        }
        InfoRow(stringResource(R.string.biolism_energy_activity_level), profile.activityMeta.label, profile.activityMeta.note, Gold)
        val kcalJ = stringResource(R.string.biolism_energy_kcal_j)
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_energy_bmr_avg), "%.1f".format(Locale.US, met.bmrDay), stringResource(R.string.biolism_energy_bmr_avg_sub)),
            Triple("Mifflin-St Jeor", "%.1f".format(Locale.US, met.bmrMsj), kcalJ),
            Triple("Katch-McArdle", "%.1f".format(Locale.US, met.bmrKm), stringResource(R.string.biolism_energy_km_sub)),
        ) + if (s.ketosisOn) listOf(Triple(stringResource(R.string.biolism_energy_bmr_suppressed), "%.1f".format(Locale.US, met.bmrDay * met.ketoSupprFactor), stringResource(R.string.biolism_energy_bmr_suppressed_sub))) else emptyList())
        Spacer(Modifier.height(6.dp))
        InfoRow(stringResource(R.string.biolism_energy_deficit), stringResource(R.string.biolism_energy_kcal_per_day_value, met.tdeeDay - 500), stringResource(R.string.biolism_energy_deficit_sub), Teal)
        InfoRow(stringResource(R.string.biolism_energy_surplus), stringResource(R.string.biolism_energy_kcal_per_day_value, met.tdeeDay + 300), stringResource(R.string.biolism_energy_surplus_sub), Violet)

        // Real energy balance — sourced from the Diary's actual logged intake, so this
        // reflects real consumption rather than a separate manual entry system.
        // sessions only changes when a session is saved, but this scope recomposes
        // every second (live tick) — remember so it's not re-scanned 60x/minute
        // against the whole session history.
        val todaySessKcal = remember(sessions) { sessions.filter { isToday(it.timestamp) }.sumOf { it.kcalBurned } }
        // totalOut (TDEE + today's tracked session) is shown for context in the
        // Expenditure row below, but netBal deliberately excludes todaySessKcal -
        // met.tdeeDay is itself PAL-derived from profile.activityMeta (shown just
        // above), so folding a specific logged session on top risks double-
        // counting the same activity twice, exactly the reasoning Dashboard's own
        // CalorieBalanceCard already applies to ActivityRepository's exercise kcal
        // (see DashboardViewModel's CalorieBalance.exerciseKcal doc comment) -
        // this card previously used a different rule for what's meant to be the
        // same "today's net balance" concept as that one.
        val totalOut = met.tdeeDay + todaySessKcal
        val netBal = todayIntakeKcal - met.tdeeDay
        Spacer(Modifier.height(Spacing.S))
        // Aligned to Dashboard's CalorieBalanceCard color convention (its own
        // "Part B6 atmosphere fix") - this card previously mapped deficit to
        // semanticGreen() while Dashboard maps deficit to AccentCoral, so the
        // same status read as a different color depending which screen showed it.
        val balanceColor = if (netBal > 200) semanticRed() else if (netBal < -50) AccentCoral else semanticAmber()
        TintedPanel(balanceColor) {
            Label(stringResource(R.string.biolism_energy_balance_title), balanceColor)
            InfoRow(stringResource(R.string.biolism_energy_intake), "%.0f kcal".format(Locale.US, todayIntakeKcal), stringResource(R.string.biolism_energy_intake_sub), Teal)
            InfoRow(stringResource(R.string.biolism_energy_expenditure), "%.0f kcal".format(Locale.US, totalOut), stringResource(R.string.biolism_energy_expenditure_sub, met.tdeeDay, todaySessKcal), Gold)
            InfoRow(stringResource(R.string.biolism_energy_net_balance), "%+.0f kcal".format(Locale.US, netBal),
                if (netBal > 200) stringResource(R.string.biolism_energy_status_surplus) else if (netBal < -50) stringResource(R.string.biolism_energy_status_deficit) else stringResource(R.string.biolism_energy_status_balanced),
                balanceColor)
            // 1 decimal, matching WeightSummaryCard/BodyCompositionCard's weight
            // precision convention app-wide - the previous 3 decimals (e.g.
            // "+0.026 kg") implied a precision this rough net-kcal/7700 estimate
            // doesn't actually have, and read as disagreeing with every other
            // weight figure in the app rather than just being a coarser estimate.
            InfoRow(stringResource(R.string.biolism_energy_weight_impact), "%+.1f kg".format(Locale.US, netBal / 7700.0), stringResource(R.string.biolism_energy_weight_impact_sub), TextSecondary)
        }
    }
}
