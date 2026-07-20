package fr.scanneat.presentation.biolism.evolution

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
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.evolution.cards.*
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.ambientGloom

// Orchestrator only, mirrors DataScreen.kt — each section lives in cards/*.kt,
// shared chart primitives in EvolutionComponents.kt, reusing BioCard/Label/
// InfoRow/HormoneRow etc. from the Data tab's DataScreenComponents.kt so both
// tabs share one visual language instead of a second parallel component set.
@Composable
fun EvolutionScreen(viewModel: EvolutionViewModel = hiltViewModel()) {
    val profile          = viewModel.profile.collectAsStateWithLifecycle()
    val language          = viewModel.language.collectAsStateWithLifecycle()
    val useImperial       = viewModel.useImperial.collectAsStateWithLifecycle()
    val weightEntries     = viewModel.weightEntries.collectAsStateWithLifecycle()
    val bodyComposition   = viewModel.bodyCompositionSeries.collectAsStateWithLifecycle()
    val hydrationHistory  = viewModel.hydrationHistory.collectAsStateWithLifecycle()
    val hydrationGoalMl   = viewModel.hydrationGoalMl.collectAsStateWithLifecycle()
    val macroRollup       = viewModel.macroRollup.collectAsStateWithLifecycle()
    val sessions          = viewModel.sessions.collectAsStateWithLifecycle()
    val hormonesToday     = viewModel.hormonesToday.collectAsStateWithLifecycle()
    val hormoneTrends     = viewModel.hormoneTrends.collectAsStateWithLifecycle()

    if (!profile.value.isValid) {
        Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = Gold, secondary = Teal), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Icon(Icons.Default.ShowChart, null, tint = Gold, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.biolism_tracker_empty_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.biolism_datascreen_empty_tab_hint), style = MaterialTheme.typography.bodySmall, color = Gold)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = Gold, secondary = Teal),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item { WeightEvolutionCard(weightEntries.value, useImperial.value, language.value) }
        item { BodyCompositionEvolutionCard(bodyComposition.value, language.value) }
        item { HydrationEvolutionCard(hydrationHistory.value, hydrationGoalMl.value, language.value) }
        item { MacrosEvolutionCard(macroRollup.value) }
        item { KetosisEvolutionCard(sessions.value) }
        item { HormonesEvolutionCard(hormonesToday.value, hormoneTrends.value, language.value) }
        item { Spacer(Modifier.height(Spacing.L)) }
    }
}
