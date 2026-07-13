package fr.scanneat.presentation.biolism.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.data.cards.*
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

// Orchestrator only — each card section lives in cards/*.kt (one file per
// independent card), shared helpers (BioCard, MetCellGrid, InfoRow, ...) in
// DataScreenComponents.kt. Was previously a single 892-line file with all
// 14 card sections inline.
@Composable
fun DataScreen(viewModel: DataViewModel = hiltViewModel()) {
    val profile     = viewModel.profile.collectAsStateWithLifecycle()
    val timer       = viewModel.timer.collectAsStateWithLifecycle()
    val m           = viewModel.metabolics.collectAsStateWithLifecycle()
    val hormones    = viewModel.hormones.collectAsStateWithLifecycle()
    val sessions    = viewModel.sessions.collectAsStateWithLifecycle()
    val manualHR    = viewModel.manualHR.collectAsStateWithLifecycle()
    val cum         = viewModel.sessionCumulative.collectAsStateWithLifecycle()
    val todayIntake = viewModel.todayIntakeKcal.collectAsStateWithLifecycle()
    val language    = viewModel.language.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle()  // force recomposition every second

    val met = m.value
    val s   = timer.value

    if (met == null) {
        val bgColor = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Icon(Icons.Default.MonitorHeart, null, tint = Gold, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.biolism_tracker_empty_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.biolism_datascreen_empty_tab_hint), style = MaterialTheme.typography.bodySmall, color = Gold)
            }
        }
        return
    }

    val bgColor2 = MaterialTheme.colorScheme.background
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(bgColor2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { BodyCompositionCard(met, profile.value) }
        item { DailyEnergyCard(met, profile.value, s, sessions.value, todayIntake.value) }
        item { BurnRateCard(met, s, cum.value) }
        item { SubstrateFluxCard(met, s) }
        if (s.ketosisOn) {
            item { KetosisProcessCard(s, met, language.value) }
        }
        item { OrganHeatCard(met, s) }
        item { ThermoregulationCard(met) }
        item { PhysiologicalMetricsCard(met, profile.value, s, cum.value, manualHR.value, viewModel::saveManualHR) }
        hormones.value?.let { h ->
            item { HormonesCard(h, s, met, profile.value) }
        }
        item { MacroTargetsCard(met, profile.value) }
        if (sessions.value.isNotEmpty()) {
            item { GlobalSummaryCard(sessions.value) }
            item { DailyGoalsCard(met, profile.value, sessions.value) }
        }
        item { EquationsCard(met, profile.value) }
        if (sessions.value.isNotEmpty()) {
            item { SessionAnalyticsCard(sessions.value, profile.value.weightKg) }
        }
        if (sessions.value.isNotEmpty()) {
            item { SessionHistoryCard(sessions.value, viewModel::deleteSession) }
        }
        item { Spacer(Modifier.height(Spacing.L)) }
    }
}
