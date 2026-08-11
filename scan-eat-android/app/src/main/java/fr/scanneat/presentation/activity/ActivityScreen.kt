package fr.scanneat.presentation.activity

import compose.icons.tablericons.Activity
import compose.icons.tablericons.Plus
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.domain.engine.health.OvertrainingSeverity
import fr.scanneat.presentation.activity.components.ActivityDailyTotalsCard
import fr.scanneat.presentation.activity.components.ActivityEntryRow
import fr.scanneat.presentation.activity.components.ActivityQuickLogRow
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
    // Same reasoning as embeddedBottomPadding, for the host's floating header -
    // fed into this screen's own LazyColumn contentPadding so scrolled items can
    // still scroll up into the region behind the header instead of that region
    // staying permanently empty.
    embeddedTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries          = viewModel.entries.collectAsStateWithLifecycle()
    val pastSubTypes     = viewModel.pastSubTypes.collectAsStateWithLifecycle()
    val weeklyBurn       = viewModel.weeklyBurn.collectAsStateWithLifecycle()
    val weeklyMinutes    = viewModel.weeklyMinutes.collectAsStateWithLifecycle()
    val weekTrendPct     = viewModel.weekTrendPct.collectAsStateWithLifecycle()
    val customWeeklyGoalMinutes = viewModel.customWeeklyGoalMinutes.collectAsStateWithLifecycle()
    val sortedTypes      = viewModel.sortedActivityTypes.collectAsStateWithLifecycle()
    val streak           = viewModel.streak.collectAsStateWithLifecycle()
    val quickLogSuggestions = viewModel.quickLogSuggestions.collectAsStateWithLifecycle()
    val language         = viewModel.language.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.WALKING_BRISK) }
    var minutesText by rememberSaveable { mutableStateOf("30") }
    var selectedSubType by rememberSaveable { mutableStateOf<String?>(null) }
    var customSubTypeText by rememberSaveable { mutableStateOf("") }
    var setsText by rememberSaveable { mutableStateOf("") }
    var repsText by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var weightUsedText by rememberSaveable { mutableStateOf("") }
    // User-requested: "was it outdoors" so an outdoor session can credit a
    // rough vitamin D estimate on the dashboard (see DashboardAggregator).
    var wasOutdoors by rememberSaveable { mutableStateOf(false) }
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
        wasOutdoors = false
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

    // One-time celebration snackbar the moment the streak sets a new all-time
    // record - Fasting already has this exact acknowledgment for personalRecord;
    // ActivityStreakRow's badge is persistent, not a distinct celebrated moment.
    // context.getString (not stringResource) since the message needs the day
    // count from a value only known inside this suspend collector, not at
    // composition time.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.newStreakRecord.collect { days ->
            // Was a plain string with %1$d always followed by plural "jours"/"days" -
            // a first-ever streak (days == 1, reachable since best starts at 0) read
            // "New record: 1 days in a row!". Same <plurals> pattern this app already
            // uses for the equivalent widget/fasting streak counts.
            val message = context.resources.getQuantityString(R.plurals.activity_new_streak_record, days, days)
            snackbarHostState.showSnackbar(CelebrationSnackbarVisuals(message))
        }
    }

    // User-requested: same excessive-volume awareness as AddActivityDialog's
    // own inline text (see ActivityViewModel.overtrainingWarning's own doc
    // comment), surfaced as a snackbar too so quickLog() - which skips the
    // dialog entirely - still warns the user.
    LaunchedEffect(Unit) {
        viewModel.overtrainingWarning.collect { warning ->
            val message = context.getString(
                if (warning.severity == OvertrainingSeverity.HIGH) R.string.activity_overtraining_high
                else R.string.activity_overtraining_moderate,
                warning.totalMinutes,
            )
            snackbarHostState.showSnackbar(message)
        }
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // Previously an inline single-domain MonthCalendar toggled here;
            // now routes to the unified Calendar (Dashboard), which shows
            // activity alongside every other tracker.
            item { ActivityStreakRow(streakDays = streak.value, onOpenCalendar = onOpenCalendar) }

            // User-requested: one-tap re-log of a frequently repeated workout -
            // see ActivityViewModel.quickLogSuggestions' own doc comment.
            if (quickLogSuggestions.value.isNotEmpty()) {
                item {
                    ActivityQuickLogRow(
                        suggestions = quickLogSuggestions.value,
                        typeLabels = typeLabels, subTypeLabels = subTypeLabels,
                        onQuickLog = { viewModel.quickLog(it) },
                    )
                }
            }

            // Daily burned summary
            val totalKcal = entries.value.sumOf { it.kcalBurned }
            val totalMin  = entries.value.sumOf { it.minutes }
            if (totalKcal > 0) {
                item { ActivityDailyTotalsCard(totalKcal = totalKcal, totalMin = totalMin) }
            }

            // Improvement: 7-day kcal burn bar chart
            if (weeklyBurn.value.any { it.second > 0 }) {
                item { ActivityWeeklyBurnChart(weeklyBurn.value, language.value) }
            }

            // New: weekly active minutes vs WHO 150 min/week goal + week-over-week trend
            item {
                ActivityWeeklyMinutesCard(
                    weeklyMinutes = weeklyMinutes.value, weekTrendPct = weekTrendPct.value,
                    goalMinutes = customWeeklyGoalMinutes.value ?: 150,
                    hasCustomGoal = customWeeklyGoalMinutes.value != null,
                    onSetGoal = { viewModel.setWeeklyGoalMinutes(it) },
                )
            }

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
                        wasOutdoors = e.wasOutdoors
                        showAdd = true
                    },
                    onDelete = { deleteTarget = e.id },
                )
            }

            if (entries.value.isEmpty()) {
                item {
                    EmptyListState(
                        TablerIcons.Activity, stringResource(R.string.activity_empty),
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
            content(PaddingValues(top = embeddedTopPadding, bottom = embeddedBottomPadding))
            FloatingActionButton(
                onClick = { openAddDialog() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = embeddedBottomPadding + Spacing.L, end = Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = Color.Black) }
            ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.activity_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
            actions = { IconButton(onClick = { openAddDialog() }) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = AccentCoral) } },
            snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
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
                wasOutdoors = wasOutdoors,
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
                onWasOutdoorsChange = { wasOutdoors = it },
            ),
            // openAddDialog() already resets every field whenever the Add dialog is
            // reopened (FAB/CTA) - calling it again here on dismiss was redundant and
            // composed an extra recomposition of a now-empty dialog right before it
            // closed, unlike Weight/Medication which only reset fields at the open call site.
            // Excludes the entry being edited (if any) from its own type's
            // already-logged total, so re-saving an unchanged edit doesn't
            // double-count that same session against itself.
            todayMinutesForType = entries.value
                .filter { it.type == selectedType && it.id != editTargetId }
                .sumOf { it.minutes },
            onDismiss = { showAdd = false },
            onAdd = {
                // Clamped to sane ranges, same rationale as Profile/Weight/CustomFood's
                // own coerceIn calls - previously unbounded (sets/reps/distance/weight
                // were already fixed, but minutes itself was missed), so a pasted or
                // IME-entered value like "999999" minutes silently landed in
                // activity_log, producing a proportionally huge kcalBurned that skewed
                // the weekly burn/minutes charts and streak. 1440 = one full day.
                minutesText.toIntOrNull()?.coerceIn(1, 1440)?.let { min ->
                    val sets = setsText.toIntOrNull()?.coerceIn(0, 999)
                    val reps = repsText.toIntOrNull()?.coerceIn(0, 999)
                    val distanceKm = distanceText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 500.0)
                    val weightUsedKg = weightUsedText.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 500.0)
                    val editId = editTargetId
                    if (editId != null) {
                        viewModel.update(editId, selectedType, min, subType = selectedSubType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg, wasOutdoors = wasOutdoors)
                    } else {
                        viewModel.log(selectedType, min, subType = selectedSubType, sets = sets, reps = reps, distanceKm = distanceKm, weightUsedKg = weightUsedKg, wasOutdoors = wasOutdoors)
                    }
                    showAdd = false
                    editTargetId = null
                    selectedSubType = null; customSubTypeText = ""; setsText = ""; repsText = ""; distanceText = ""; weightUsedText = ""; wasOutdoors = false
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
