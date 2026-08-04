package fr.scanneat.presentation.biolism.data

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
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.data.cards.*
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.ScanEatSnackbarHost
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.ambientGloom

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
    val useImperial = viewModel.useImperial.collectAsStateWithLifecycle()
    val advanced    = viewModel.advancedView.collectAsStateWithLifecycle()
    val crossInsight = viewModel.crossInsight.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle()  // force recomposition every second

    // Same pattern as TrackerScreen/WeightScreen - saveManualHR()/deleteSession()
    // previously called repo's DataStore writes completely unguarded; a failed
    // write now surfaces here as a one-shot snackbar instead of going back to
    // silent. No Scaffold on this screen (embedded as a BiolismScreen tab), so
    // the host is overlaid directly like TrackerScreen's own embedded path.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    val met = m.value
    val s   = timer.value

    if (met == null) {
        Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Icon(Icons.Outlined.MonitorHeart, null, tint = Gold, modifier = Modifier.size(IconSize.EmptyState))
                Text(stringResource(R.string.biolism_tracker_empty_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.biolism_datascreen_empty_tab_hint), style = MaterialTheme.typography.bodySmall, color = Gold)
            }
            ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item { MetabolicHealthScoreCard(met, profile.value) }
        item { BodyCompositionCard(met, profile.value, useImperial.value) }
        item { DailyEnergyCard(met, profile.value, s, sessions.value, todayIntake.value, language.value, useImperial.value) }
        // Diary logging previously never visibly affected this screen at all -
        // this is the one card that cross-references Diary's actual intake
        // against the real weight-trend direction, same insight already shown
        // on Dashboard (WeeklyInsightCard), reused here rather than duplicated.
        (crossInsight.value as? fr.scanneat.domain.engine.dashboard.CrossTrackerInsight.WeightVsIntake)?.let { insight ->
            if (insight.agreement != fr.scanneat.domain.engine.dashboard.InsightAgreement.INCONCLUSIVE) {
                item { fr.scanneat.presentation.dashboard.cards.WeeklyInsightCard(insight, useImperial = useImperial.value) }
            }
        }
        item { BurnRateCard(met, s, cum.value) }
        // R&D §X.0: everything below this point is the research-grade half of
        // this screen (substrate-flux biochemistry, Fanger thermoregulation,
        // raw ventilation/respiratory physiology, hormone estimates, formula
        // sheets) - gated behind the Advanced Biolism view toggle (Settings)
        // so a user who only wants BMR/body composition/energy isn't handed
        // 10+ dense scientific cards by default. Defaults to on (unchanged
        // behavior) until a user opts into the simpler view.
        if (advanced.value) {
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
        }
        item { MacroTargetsCard(met, profile.value) }
        if (sessions.value.isNotEmpty()) {
            item { GlobalSummaryCard(sessions.value) }
            item { DailyGoalsCard(met, profile.value, sessions.value, language.value) }
        }
        if (advanced.value) {
            item { EquationsCard(met, profile.value) }
        }
        if (sessions.value.isNotEmpty()) {
            item { SessionAnalyticsCard(sessions.value, profile.value.weightKg, useImperial.value) }
        }
        if (sessions.value.isNotEmpty()) {
            item { SessionHistoryCard(sessions.value, viewModel::deleteSession, useImperial.value) }
        }
        item { Spacer(Modifier.height(Spacing.L)) }
    }
    ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
