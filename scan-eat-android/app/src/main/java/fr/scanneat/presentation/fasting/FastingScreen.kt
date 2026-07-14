package fr.scanneat.presentation.fasting

import kotlinx.coroutines.flow.StateFlow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.domain.model.MS_PER_HOUR
import fr.scanneat.domain.model.MS_PER_MINUTE
import fr.scanneat.presentation.ui.theme.*

private data class MetabolicPhase(val label: String, val description: String, val minHours: Int)
private val METABOLIC_PHASES = listOf(
    MetabolicPhase("Glycogène",    "L'organisme consomme ses réserves de glucose",      0),
    MetabolicPhase("Transition",   "Début de la gluconéogenèse, glycogène en baisse",   8),
    MetabolicPhase("Combustion",   "Les graisses deviennent la source d'énergie principale", 12),
    MetabolicPhase("Cétose",       "Production de corps cétoniques, clarté mentale",    16),
    MetabolicPhase("Cétose prof.", "Cétose profonde, énergie stable et soutenue",       20),
    MetabolicPhase("Autophagie",   "Recyclage cellulaire (autophagie) activé",          24),
)
private fun metabolicPhase(elapsedHours: Int): MetabolicPhase =
    METABOLIC_PHASES.lastOrNull { elapsedHours >= it.minHours } ?: METABOLIC_PHASES.first()

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar — used when
 * hosted as a Journal sub-tab, where the tab row itself is the header and a
 * second nested app bar (with a dead-end back arrow) would be redundant
 * chrome. Standalone push-navigation callers leave it false.
 */
@Composable
fun FastingScreen(
    viewModel: FastingViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    onOpenCalendar: () -> Unit = {},
) {
    val state   = viewModel.fastingState.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsStateWithLifecycle()
    val streak  = viewModel.streak.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle() // force recomposition every second

    var targetHours by remember { mutableIntStateOf(16) }
    var customMode by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf("18:00") }
    var customEnd by remember { mutableStateOf("06:00") }
    val customHours = remember(customStart, customEnd) {
        runCatching {
            val start = java.time.LocalTime.parse(customStart)
            val end = java.time.LocalTime.parse(customEnd)
            var minutes = java.time.Duration.between(start, end).toMinutes()
            if (minutes <= 0) minutes += 24 * 60 // overnight window, e.g. 18:00 -> 06:00
            minutes / 60.0
        }.getOrNull()
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }

            // Previously no calendar entry point at all for Jeûne (unlike
            // Weight/Activity/Hydration, which each had their own local one) -
            // routes straight to the unified Calendar instead of adding yet
            // another single-domain grid.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.fasting_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }

            // Streak
            if (streak.value > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = CalorieOrange)
                        Text(pluralStringResource(R.plurals.fasting_streak, streak.value, streak.value), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Timer
            item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.PROMINENT), contentPadding = PaddingValues(24.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.L),
                    ) {
                        val fs = state.value
                        if (fs != null) {
                            // Active fast
                            val pct = fs.progressFraction.coerceIn(0f, 1f)
                            CircularProgressIndicator(
                                progress = { pct }, modifier = Modifier.size(140.dp),
                                color = if (pct >= 1f) AccentCoral else AccentCoral.copy(0.6f),
                                trackColor = SurfaceVariant, strokeWidth = 10.dp,
                            )
                            val h = (fs.elapsedMs / MS_PER_HOUR).toInt()
                            val m = ((fs.elapsedMs % MS_PER_HOUR) / MS_PER_MINUTE).toInt()
                            Text("${h}h ${m.toString().padStart(2, '0')}m", style = MaterialTheme.typography.headlineMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.fasting_target_progress, fs.targetHours), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))

                            // Improvement: metabolic phase indicator
                            val phase = metabolicPhase(h)
                            val nextPhase = METABOLIC_PHASES.firstOrNull { it.minHours > h }
                            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.1f)) {
                                Column(modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Phase · ${phase.label}", style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                                    Text(phase.description, style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                                    if (nextPhase != null) {
                                        Text("Prochaine : ${nextPhase.label} à ${nextPhase.minHours}h", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                                ScanEatOutlinedButton(onClick = { viewModel.cancel() }) {
                                    Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.7f))
                                }
                                ScanEatPrimaryButton(onClick = { viewModel.stop() }) {
                                    Text(stringResource(R.string.fasting_finish_button))
                                }
                            }
                        } else {
                            // Not active
                            Text(stringResource(R.string.fasting_start_title), style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.fasting_target_duration_label), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                listOf(12, 16, 18, 20, 24).forEach { h ->
                                    FilterChip(
                                        selected = !customMode && targetHours == h, onClick = { customMode = false; targetHours = h },
                                        label = { Text(stringResource(R.string.fasting_hours_chip, h), style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                            labelColor = OnBackground.copy(0.7f),
                                        ),
                                    )
                                }
                                FilterChip(
                                    selected = customMode, onClick = { customMode = true },
                                    label = { Text(stringResource(R.string.fasting_custom_chip), style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                        labelColor = OnBackground.copy(0.7f),
                                    ),
                                )
                            }
                            if (customMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = customStart, onValueChange = { customStart = it },
                                        label = { Text(stringResource(R.string.fasting_custom_start_label)) },
                                        singleLine = true, modifier = Modifier.width(110.dp),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                    )
                                    Text("→", style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
                                    OutlinedTextField(
                                        value = customEnd, onValueChange = { customEnd = it },
                                        label = { Text(stringResource(R.string.fasting_custom_end_label)) },
                                        singleLine = true, modifier = Modifier.width(110.dp),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (customHours != null) {
                                    Text(stringResource(R.string.fasting_target_progress, customHours.toInt()), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                            }
                            val effectiveHours = if (customMode) customHours?.toInt() else targetHours
                            ScanEatPrimaryButton(
                                onClick = { effectiveHours?.let { viewModel.start(it) } },
                                enabled = effectiveHours != null,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.fasting_start_button, effectiveHours ?: targetHours))
                            }
                        }
                    }
                }
            }

            // New: history stats summary card
            if (history.value.isNotEmpty()) {
                item {
                    val completed = history.value
                    val successCount = completed.count { it.reached }
                    val avgHours = completed.map { it.achievedHours }.average()
                    val longestH = completed.maxOf { it.achievedHours }
                    Text(stringResource(R.string.fasting_history_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.S))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        listOf(
                            "Total"     to "${completed.size}",
                            "Réussis"   to "$successCount/${completed.size}",
                            "Moy."      to "${String.format("%.1f", avgHours)}h",
                            "Record"    to "${longestH}h",
                        ).forEach { (label, value) ->
                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant) {
                                Column(modifier = Modifier.padding(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(value, style = MaterialTheme.typography.titleSmall, color = AccentCoral, fontWeight = FontWeight.Bold)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                }
                            }
                        }
                    }
                }
            }

            // History list
            if (history.value.isNotEmpty()) {
                items(history.value.take(20), key = { it.endMs }) { c ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(c.date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                        Text(stringResource(R.string.fasting_history_entry, c.achievedHours, c.targetHours), style = MaterialTheme.typography.bodySmall, color = if (c.reached) semanticGreen() else semanticAmber())
                        Icon(if (c.reached) Icons.Default.CheckCircle else Icons.Default.Close, null, tint = if (c.reached) semanticGreen() else OnSurface.copy(0.3f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        content(PaddingValues(0.dp))
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.fasting_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
            containerColor = Background,
        ) { padding -> content(padding) }
    }
}
