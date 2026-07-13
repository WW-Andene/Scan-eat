package fr.scanneat.presentation.biolism.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.biolism.tracker.cards.*
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.presentation.ui.theme.Warm
import kotlin.math.roundToInt

// Orchestrator only — each screen section lives in cards/*.kt, shared
// helpers (StepperChip, formatElapsed, formatFastingTime) in
// TrackerScreenComponents.kt. Was previously a single 516-line file with
// all sections + formatters inline.
@Composable
fun TrackerScreen(viewModel: TrackerViewModel = hiltViewModel()) {
    val profile   = viewModel.profile.collectAsStateWithLifecycle()
    val timer     = viewModel.timerState.collectAsStateWithLifecycle()
    val elapsedMs = viewModel.elapsedMs.collectAsStateWithLifecycle()
    val ketoMs    = viewModel.ketoElapsedMs.collectAsStateWithLifecycle()
    val precision    = viewModel.heroPrecision.collectAsStateWithLifecycle()
    val showPerSec   = viewModel.showKcalPerSec.collectAsStateWithLifecycle()
    val saved        = viewModel.saved.collectAsStateWithLifecycle()
    val healthConditions = viewModel.healthConditions.collectAsStateWithLifecycle()
    val language     = viewModel.language.collectAsStateWithLifecycle()
    val realFastHours = viewModel.realFastHours.collectAsStateWithLifecycle()

    val s    = timer.value
    val p    = profile.value
    val live = viewModel.liveMetabolic.collectAsStateWithLifecycle()
    val lm   = live.value

    val elapsedSec   = elapsedMs.value / 1000.0
    val ketoHours    = ketoMs.value / 3_600_000.0
    val fastingHours = s.fastingHours

    // All metabolic values come from the ViewModel StateFlow (Fix 8)
    val kcalTotal    = lm.kcalTotal
    val fatPct       = (lm.fatFrac  * 100).roundToInt()
    val protPct      = (lm.protFrac * 100).roundToInt()
    val carbPct      = 100 - fatPct - protPct
    val fatLostKg    = lm.fatLostKg
    val glycoLostKg  = lm.glycoLostKg
    val liveWeight   = lm.liveWeightKg
    val phase        = lm.phase
    val phaseColor   = when (phase?.colorToken) {
        "Gold"   -> Gold;    "Teal"  -> Teal;    "Violet" -> Violet
        "Warm"   -> Warm;    "Severe"-> semanticRed();  else -> Teal
    }

    val bgColor = MaterialTheme.colorScheme.background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.L)
            .padding(bottom = Spacing.XXL),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Spacer(Modifier.height(Spacing.S))

        if (!p.isValid) {
            EmptyProfilePrompt()
        } else {
            // ── Ketosis toggle ─────────────────────────────────────────────────
            KetosisToggleRow(
                active        = s.ketosisOn,
                ketoAdapted   = s.ketoAdapted,
                fatPct        = fatPct,
                npRq          = lm.npRq,
                ketoHours     = ketoHours,
                onToggle      = viewModel::toggleKetosis,
                onAddHours    = viewModel::addKetoHours,
            )

            if (s.ketosisOn) {
                AdaptedToggleRow(active = s.ketoAdapted, ketoHours = ketoHours, onToggle = viewModel::toggleKetoAdapted)
            }
            if (s.ketosisOn) {
                KetosisHealthCaution(healthConditions.value, language.value)
            }

            // ── Fasting row ────────────────────────────────────────────────────
            FastingRow(
                active        = s.fastingActive,
                fastingHours  = fastingHours,
                onToggle      = viewModel::toggleFastingActive,
                onLogMeal     = viewModel::logMealNow,
                onAddHours    = viewModel::addFastingHours,
                realFastHours = realFastHours.value,
                onImportRealFast = viewModel::importRealFast,
            )
            if (s.fastingActive) {
                FastingHealthCaution(healthConditions.value, language.value)
            }

            // ── Phase strip ────────────────────────────────────────────────────
            if (s.ketosisOn && phase != null) {
                PhaseStrip(phase = phase, ketoHours = ketoHours, color = phaseColor)
            }

            // ── Hero kcal display ──────────────────────────────────────────────
            HeroCard(
                kcalTotal    = kcalTotal,
                kcalSec      = lm.kcalSec,
                precision    = precision.value,
                showPerSec   = showPerSec.value,
                running      = s.running,
                ketosisOn    = s.ketosisOn,
                elapsedSec   = elapsedSec,
                fatPct       = fatPct,
                carbPct      = carbPct,
                protPct      = protPct,
                fatFrac      = lm.fatFrac,
                carbFrac     = lm.carbFrac,
                protFrac     = lm.protFrac,
                npRq         = lm.npRq,
                onPrecision  = viewModel::togglePrecision,
                onToggleRate = viewModel::toggleRateMode,
            )

            // ── Live weight ────────────────────────────────────────────────────
            if (elapsedSec > 0 && p.weightKg > 0) {
                LiveWeightCard(
                    liveWeight   = liveWeight,
                    baseWeight   = p.weightKg,
                    fatLostKg    = fatLostKg,
                    glycoLostKg  = glycoLostKg,
                    ketosisOn    = s.ketosisOn,
                )
            }

            // ── Session controls ───────────────────────────────────────────────
            SessionControls(
                running  = s.running,
                elapsed  = elapsedSec,
                saved    = saved.value,
                onStartPause = viewModel::startOrPause,
                onSave   = viewModel::saveSession,
                onReset  = viewModel::reset,
            )
        }
    }
}

@Composable
private fun EmptyProfilePrompt() {
    ScanEatCard(contentPadding = PaddingValues(32.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Icon(Icons.Default.MonitorHeart, null, tint = Gold, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.biolism_tracker_empty_title), style = MaterialTheme.typography.titleSmall,
                color = OnBackground, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(stringResource(R.string.biolism_tracker_empty_desc),
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                textAlign = TextAlign.Center)
        }
    }
}
