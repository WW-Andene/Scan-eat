package fr.scanneat.presentation.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.activity.components.ActivityDailyTotalsCard
import fr.scanneat.presentation.activity.components.ActivityEntryRow
import fr.scanneat.presentation.activity.components.ActivityStreakRow
import fr.scanneat.presentation.activity.components.ActivityWeeklyBurnChart
import fr.scanneat.presentation.activity.components.ActivityWeeklyMinutesCard
import fr.scanneat.presentation.activity.components.AddActivityDialog
import fr.scanneat.presentation.activity.components.AddActivityFormActions
import fr.scanneat.presentation.activity.components.AddActivityFormValues
import fr.scanneat.presentation.reminders.ActivityReminderCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// internal (not private) so CalendarScreen's day-detail panel can reuse the same
// localized labels instead of falling back to ActivityType.labelFr.
@Composable
internal fun typeLabels(): Map<ActivityType, String> = mapOf(
    ActivityType.WALKING_BRISK to stringResource(R.string.activity_type_walking),
    ActivityType.RUNNING to stringResource(R.string.activity_type_running),
    ActivityType.CYCLING to stringResource(R.string.activity_type_cycling),
    ActivityType.SWIMMING to stringResource(R.string.activity_type_swimming),
    ActivityType.STRENGTH to stringResource(R.string.activity_type_strength),
    ActivityType.YOGA to stringResource(R.string.activity_type_yoga),
    ActivityType.HIIT to stringResource(R.string.activity_type_hiit),
    ActivityType.OTHER to stringResource(R.string.activity_type_other),
)

@Composable
private fun subTypeLabels(): Map<String, String> = mapOf(
    "bench_press" to stringResource(R.string.activity_subtype_bench_press),
    "squat" to stringResource(R.string.activity_subtype_squat),
    "deadlift" to stringResource(R.string.activity_subtype_deadlift),
    "biceps_curl" to stringResource(R.string.activity_subtype_biceps_curl),
    "freestyle" to stringResource(R.string.activity_subtype_freestyle),
    "breaststroke" to stringResource(R.string.activity_subtype_breaststroke),
    "butterfly" to stringResource(R.string.activity_subtype_butterfly),
    "trail" to stringResource(R.string.activity_subtype_trail),
    "sprint" to stringResource(R.string.activity_subtype_sprint),
    "interval" to stringResource(R.string.activity_subtype_interval),
    "road" to stringResource(R.string.activity_subtype_road),
    "mountain" to stringResource(R.string.activity_subtype_mountain),
    "indoor" to stringResource(R.string.activity_subtype_indoor),
)

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar — used when
 * hosted as a Journal sub-tab, where the tab row itself is the header and a
 * second nested app bar (with a dead-end back arrow) would be redundant
 * chrome. Standalone push-navigation callers leave it false.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    // Only meaningful when [embedded] — the host (DiaryScreen) supplies this so
    // this screen's own LazyColumn reserves the same floating-bottom-nav
    // clearance the host itself is already reserving.
    embeddedBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries          = viewModel.entries.collectAsStateWithLifecycle()
    val pastSubTypes     = viewModel.pastSubTypes.collectAsStateWithLifecycle()
    val weeklyBurn       = viewModel.weeklyBurn.collectAsStateWithLifecycle()
    val weeklyMinutes    = viewModel.weeklyMinutes.collectAsStateWithLifecycle()
    val weekTrendPct     = viewModel.weekTrendPct.collectAsStateWithLifecycle()
    val sortedTypes      = viewModel.sortedActivityTypes.collectAsStateWithLifecycle()
    val streak           = viewModel.streak.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.WALKING_BRISK) }
    var minutesText by rememberSaveable { mutableStateOf("30") }
    var selectedSubType by rememberSaveable { mutableStateOf<String?>(null) }
    var customSubTypeText by rememberSaveable { mutableStateOf("") }
    var setsText by rememberSaveable { mutableStateOf("") }
    var repsText by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var weightUsedText by rememberSaveable { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    // Non-null while editing an existing entry (vs. creating a new one) — the same
    // AddActivityDialog is reused for both, matching Diary's edit-via-reopened-dialog
    // pattern, since Weight/Diary/Templates already support editing a logged entry
    // and Activity previously only supported delete-and-recreate.
    var editTargetId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    // Every "open Add" entry point (FAB, top-bar action, empty-state CTA) must reset every
    // dialog field, not just editTargetId - otherwise cancelling an Edit and then tapping
    // Add reopens the dialog still prefilled with that entry's minutes/sets/reps/distance/
    // weight/sub-type, and saving would create a new entry with those stale leftover values.
    fun openAddDialog() {
        editTargetId = null
        selectedType = ActivityType.WALKING_BRISK
        selectedSubType = null; customSubTypeText = ""
        setsText = ""; repsText = ""; distanceText = ""; weightUsedText = ""
        minutesText = "30"
        showAdd = true
    }
    val typeLabels = typeLabels()
    val subTypeLabels = subTypeLabels()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.activity_deleted_message)
    val undoLabel = stringResource(R.string.activity_undo)
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = Warm, secondary = AccentCoral).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // Previously an inline single-domain MonthCalendar toggled here;
            // now routes to the unified Calendar (Dashboard), which shows
            // activity alongside every other tracker.
            item { ActivityStreakRow(streakDays = streak.value, onOpenCalendar = onOpenCalendar) }

            // Daily burned summary
            val totalKcal = entries.value.sumOf { it.kcalBurned }
            val totalMin  = entries.value.sumOf { it.minutes }
            if (totalKcal > 0) {
                item { ActivityDailyTotalsCard(totalKcal = totalKcal, totalMin = totalMin) }
            }

            // Improvement: 7-day kcal burn bar chart
            if (weeklyBurn.value.any { it.second > 0 }) {
                item { ActivityWeeklyBurnChart(weeklyBurn.value) }
            }

            // New: weekly active minutes vs WHO 150 min/week goal + week-over-week trend
            item { ActivityWeeklyMinutesCard(weeklyMinutes = weeklyMinutes.value, weekTrendPct = weekTrendPct.value) }

            items(entries.value, key = { it.id }) { e ->
                ActivityEntryRow(
                    entry = e, typeLabels = typeLabels, subTypeLabels = subTypeLabels,
                    onEdit = {
                        editTargetId = e.id
                        selectedType = e.type
                        selectedSubType = e.subType
                        customSubTypeText = e.subType.orEmpty()
                        setsText = e.sets?.toString().orEmpty()
                        repsText = e.reps?.toString().orEmpty()
                        distanceText = e.distanceKm?.toString().orEmpty()
                        weightUsedText = e.weightUsedKg?.toString().orEmpty()
                        minutesText = e.minutes.toString()
                        showAdd = true
                    },
                    onDelete = { deleteTarget = e.id },
                )
            }

            if (entries.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.DirectionsRun, stringResource(R.string.activity_empty),
                        ctaLabel = stringResource(R.string.activity_add_cta), onCta = { openAddDialog() },
                    )
                }
            }

            item { ActivityReminderCard() }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(bottom = embeddedBottomPadding))
            FloatingActionButton(
                onClick = { openAddDialog() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = embeddedBottomPadding + Spacing.L, end = Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.activity_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            actions = { IconButton(onClick = { openAddDialog() }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral) } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AddActivityDialog(
            sortedTypes = sortedTypes.value,
            typeLabels = typeLabels,
            subTypeLabels = subTypeLabels,
            pastSubTypes = pastSubTypes.value,
            values = AddActivityFormValues(
                selectedType = selectedType,
                selectedSubType = selectedSubType,
                customSubTypeText = customSubTypeText,
                setsText = setsText,
                repsText = repsText,
                distanceText = distanceText,
                weightUsedText = weightUsedText,
                minutesText = minutesText,
            ),
            actions = AddActivityFormActions(
                onSelectedTypeChange = { selectedType = it; selectedSubType = null; customSubTypeText = "" },
                onSelectedSubTypeChange = { selectedSubType = it },
                onCustomSubTypeTextChange = { customSubTypeText = it; selectedSubType = it.ifBlank { null } },
                onClearCustomSubTypeText = { customSubTypeText = "" },
                onSetsTextChange = { setsText = it },
                onRepsTextChange = { repsText = it },
                onDistanceTextChange = { distanceText = it },
                onWeightUsedTextChange = { weightUsedText = it },
                onMinutesTextChange = { minutesText = it },
            ),
            onDismiss = { openAddDialog(); showAdd = false },
            onAdd = {
                minutesText.toIntOrNull()?.let { min ->
                    // Clamped to sane ranges, same rationale as Profile/Weight/CustomFood's
                    // own coerceIn calls - previously unbounded, so a pasted or IME-entered
                    // value like "999999" reps or a negative distance silently landed in
                    // activity_log and skewed the weekly burn/minutes charts.
                    val sets = setsText.toIntOrNull()?.coerceIn(0, 999)
                    val reps = repsText.toIntOrNull()?.coerceIn(0, 999)
                    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 500.0)
                    val weightUsedKg = weightUsedText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 500.0)
                    val editId = editTargetId
                    if (editId != null) {
                        viewModel.update(editId, selectedType, min, subType = selectedSubType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg)
                    } else {
                        viewModel.log(selectedType, min, subType = selectedSubType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg)
                    }
                    showAdd = false
                    editTargetId = null
                    selectedSubType = null; customSubTypeText = ""; setsText = ""; repsText = ""; distanceText = ""; weightUsedText = ""
                }
            },
        )
    }

    deleteTarget?.let { id ->
        val target = entries.value.find { it.id == id }
        val name = target?.let { typeLabels[it.type] ?: it.type.name }
        DeleteConfirmDialog(
            itemName = name,
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
                if (target != null) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(target)
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

}
