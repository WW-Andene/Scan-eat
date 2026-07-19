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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.FastCompletion
import fr.scanneat.presentation.biolism.hmsFromSeconds
import fr.scanneat.presentation.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
private fun Fasting7DayChart(history: List<FastCompletion>) {
    val today = LocalDate.now()
    // Map date-string → FastCompletion for quick lookup (one entry per day)
    val byDate = history.associateBy { it.date }
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.fasting_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
                (6 downTo 0).forEach { daysBack ->
                    val date = today.minusDays(daysBack.toLong())
                    val dateKey = date.toString()
                    val entry = byDate[dateKey]
                    val frac = if (entry != null && entry.targetHours > 0)
                        (entry.achievedHours.toFloat() / entry.targetHours).coerceIn(0f, 1f)
                    else 0f
                    val color = when {
                        entry == null -> OnSurface.copy(0.08f)
                        entry.reached -> semanticGreen().copy(0.8f)
                        frac > 0.5f   -> semanticAmber().copy(0.7f)
                        else          -> semanticRed().copy(0.5f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(if (frac == 0f) 0.06f else frac.coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                (6 downTo 0).forEach { daysBack ->
                    val date = today.minusDays(daysBack.toLong())
                    Text(
                        date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysBack == 0) AccentCoral else OnSurface.copy(0.35f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

private data class MetabolicPhase(val label: String, val description: String, val minHours: Int, val labelEn: String = label, val descriptionEn: String = description) {
    fun label(lang: String) = if (lang == "en") labelEn else label
    fun description(lang: String) = if (lang == "en") descriptionEn else description
}
private val METABOLIC_PHASES = listOf(
    MetabolicPhase("Glycogène",    "L'organisme consomme ses réserves de glucose",      0,
        "Glycogen",    "Body is burning stored glucose reserves"),
    MetabolicPhase("Transition",   "Début de la gluconéogenèse, glycogène en baisse",   8,
        "Transition",  "Gluconeogenesis begins, glycogen falling"),
    MetabolicPhase("Combustion",   "Les graisses deviennent la source d'énergie principale", 12,
        "Fat Burn",    "Fat becomes the primary energy source"),
    MetabolicPhase("Cétose",       "Production de corps cétoniques, clarté mentale",    16,
        "Ketosis",     "Ketone bodies produced, mental clarity"),
    MetabolicPhase("Cétose prof.", "Cétose profonde, énergie stable et soutenue",       20,
        "Deep Ketosis","Deep ketosis, stable sustained energy"),
    MetabolicPhase("Autophagie",   "Recyclage cellulaire (autophagie) activé",          24,
        "Autophagy",   "Cellular recycling (autophagy) activated"),
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
    val state          = viewModel.fastingState.collectAsStateWithLifecycle()
    val history        = viewModel.history.collectAsStateWithLifecycle()
    val streak         = viewModel.streak.collectAsStateWithLifecycle()
    val language       = viewModel.language.collectAsStateWithLifecycle()
    val personalRecord = viewModel.personalRecord.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle() // force recomposition every second

    // start()/stop()/cancel() previously failed completely silently - see
    // FastingViewModel.actionFailed's own comment.
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

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
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = Teal, secondary = AccentCoral)
                .padding(horizontal = Spacing.L),
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
                            val (h, m, _) = hmsFromSeconds(fs.elapsedMs / 1000)
                            Text("${h}h ${m.toString().padStart(2, '0')}m", style = MaterialTheme.typography.headlineMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.fasting_target_progress, fs.targetHours), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))

                            // Metabolic phase indicator (now bilingual)
                            val phase = metabolicPhase(h.toInt())
                            val nextPhase = METABOLIC_PHASES.firstOrNull { it.minHours > h }
                            val lang = language.value
                            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.1f)) {
                                Column(modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.fasting_phase_label, phase.label(lang)), style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
                                    Text(phase.description(lang), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                                    if (nextPhase != null) {
                                        Text(stringResource(R.string.fasting_phase_next, nextPhase.label(lang), nextPhase.minHours), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                                    }
                                }
                            }
                            // Personal-record chip — shown when the current fast already
                            // beats the user's longest historically completed fast.
                            val pr = personalRecord.value
                            if (pr > 0 && h >= pr) {
                                Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = Gold.copy(0.15f), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(0.4f))) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        // Icon, not the 🏆 emoji baked into the string before.
                                        Icon(Icons.Default.EmojiEvents, null, tint = Gold, modifier = Modifier.size(16.dp))
                                        Text(stringResource(R.string.fasting_new_record), style = MaterialTheme.typography.labelMedium, color = Gold, fontWeight = FontWeight.Bold)
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

            // History stats summary card
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
                            stringResource(R.string.fasting_stat_total)   to "${completed.size}",
                            stringResource(R.string.fasting_stat_success) to "$successCount/${completed.size}",
                            stringResource(R.string.fasting_stat_avg)     to "${String.format(Locale.US, "%.1f", avgHours)}h",
                            stringResource(R.string.fasting_stat_record)  to "${longestH}h",
                        ).forEach { (label, value) ->
                            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant) {
                                Column(modifier = Modifier.padding(Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(value, style = MaterialTheme.typography.titleSmall, color = AccentCoral, fontWeight = FontWeight.Bold)
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.M))
                    // 7-day consistency mini-chart: one bar per day, height = achieved/target
                    Fasting7DayChart(completed)
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
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            topBar = {
                FloatingTopBar(
                    title = { Text(stringResource(R.string.fasting_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Background,
        ) { padding -> content(padding) }
    }
}
