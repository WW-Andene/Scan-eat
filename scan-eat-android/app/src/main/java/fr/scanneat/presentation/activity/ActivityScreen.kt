package fr.scanneat.presentation.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ACTIVITY_SUB_TYPES
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.reminders.ActivityReminderCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    // LaunchedEffect(Unit) only ever fires once for this composable's lifetime -
    // a user who backgrounds the app overnight with this screen still composed
    // (e.g. as the Diary/Journal Activity sub-tab) and reopens it the next
    // morning never got a second firing, so `date` stayed pinned at yesterday
    // until the composition was torn down and rebuilt some other way. An
    // ON_RESUME observer re-fires every time the screen is actually revisited,
    // which is when a midnight crossing needs to be caught.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    var deleteTarget by remember { mutableStateOf<String?>(null) }
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
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // New: consecutive-days activity streak badge
                    if (streak.value > 0) {
                        Surface(shape = RoundedCornerShape(50), color = semanticRed().copy(0.15f)) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = semanticRed(), modifier = Modifier.size(16.dp))
                                Text("${streak.value}j", style = MaterialTheme.typography.labelMedium, color = semanticRed(), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
                    }
                }
            }

            // Daily burned summary
            val totalKcal = entries.value.sumOf { it.kcalBurned }
            val totalMin  = entries.value.sumOf { it.minutes }
            if (totalKcal > 0) {
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalKcal", style = MaterialTheme.typography.titleLarge, color = semanticRed(), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.activity_kcal_burned_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalMin", style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.activity_minutes_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                            }
                        }
                    }
                }
            }

            // Improvement: 7-day kcal burn bar chart
            if (weeklyBurn.value.any { it.second > 0 }) {
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
                        val peak = weeklyBurn.value.maxOf { it.second }.coerceAtLeast(1)
                        val barColor = semanticRed()
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Text(stringResource(R.string.activity_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                weeklyBurn.value.forEach { (date, kcal) ->
                                    val frac = kcal.toFloat() / peak
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(0.02f)).background(barColor.copy(if (date == LocalDate.now()) 1f else 0.4f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                weeklyBurn.value.forEach { (date, _) ->
                                    Text(
                                        date.dayOfWeek.name.take(1),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = OnSurface.copy(if (date == LocalDate.now()) 0.8f else 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // New: weekly active minutes vs WHO 150 min/week goal + week-over-week trend
            item {
                val whoGoal = 150
                val pct = (weeklyMinutes.value.toFloat() / whoGoal).coerceIn(0f, 1f)
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.activity_weekly_minutes_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                                weekTrendPct.value?.let { trend ->
                                    val (trendColor, trendIcon) = when {
                                        trend > 0  -> semanticGreen() to "↑"
                                        trend < 0  -> semanticRed()   to "↓"
                                        else       -> OnSurface.copy(0.5f) to "→"
                                    }
                                    Text("$trendIcon${kotlin.math.abs(trend)}%", style = MaterialTheme.typography.labelSmall, color = trendColor)
                                }
                                Text("${weeklyMinutes.value}/$whoGoal min", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = if (pct >= 1f) semanticGreen() else AccentCoral, fontWeight = FontWeight.Bold)
                            }
                        }
                        LinearProgressIndicator(
                            progress    = { pct },
                            modifier    = Modifier.fillMaxWidth(),
                            color       = if (pct >= 1f) semanticGreen() else AccentCoral,
                            trackColor  = SurfaceVariant,
                        )
                        if (pct >= 1f) Text(stringResource(R.string.activity_who_goal_reached), style = MaterialTheme.typography.labelSmall, color = semanticGreen())
                        else Text(stringResource(R.string.activity_who_goal_hint), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                    }
                }
            }

            items(entries.value, key = { it.id }) { e ->
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            val subLabel = e.subType?.let { subTypeLabels[it] ?: it }
                            Text(
                                if (subLabel != null) "${typeLabels[e.type] ?: e.type.name} · $subLabel" else typeLabels[e.type] ?: e.type.name,
                                style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium,
                            )
                            Text(stringResource(R.string.activity_entry_summary, e.minutes, e.kcalBurned), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                            val metricsParts = buildList {
                                if (e.sets != null && e.reps != null) add(stringResource(R.string.activity_entry_sets_reps, e.sets, e.reps))
                                e.weightUsedKg?.let { add(stringResource(R.string.activity_entry_weight, it)) }
                                e.distanceKm?.let { add(stringResource(R.string.activity_entry_distance, it)) }
                            }
                            if (metricsParts.isNotEmpty()) {
                                Text(metricsParts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            }
                        }
                        IconButton(onClick = { deleteTarget = e.id }) {
                            Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (entries.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.DirectionsRun, stringResource(R.string.activity_empty),
                        ctaLabel = stringResource(R.string.activity_add_cta), onCta = { showAdd = true },
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
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = embeddedBottomPadding + Spacing.L, end = Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.activity_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral) } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.activity_add_dialog_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    // Type picker — Improvement: sorted by most recently used first
                    Text(stringResource(R.string.activity_type_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sortedTypes.value.forEach { type ->
                            val label = typeLabels[type] ?: type.name
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type; selectedSubType = null; customSubTypeText = "" },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                    labelColor = OnBackground.copy(0.7f),
                                ),
                            )
                        }
                    }
                    val availableSubTypes = ACTIVITY_SUB_TYPES[selectedType].orEmpty()
                    if (availableSubTypes.isNotEmpty()) {
                        Text(stringResource(R.string.activity_subtype_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableSubTypes.forEach { key ->
                                FilterChip(
                                    selected = selectedSubType == key,
                                    onClick = { selectedSubType = if (selectedSubType == key) null else key; customSubTypeText = "" },
                                    label = { Text(subTypeLabels[key] ?: key, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                                        labelColor = OnBackground.copy(0.7f),
                                    ),
                                )
                            }
                        }
                    }
                    // Free-text sub-type — the fixed chip lists above only cover a
                    // handful of common exercises per type; there was previously no
                    // way to log something like "rowing" or "pilates" at all.
                    // Suggestions drawn from the user's own past entries (no new
                    // data source), excluding names already offered as fixed chips.
                    val pastForType = remember(selectedType, pastSubTypes.value) {
                        pastSubTypes.value[selectedType].orEmpty().filter { it !in availableSubTypes }
                    }
                    if (pastForType.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            pastForType.forEach { suggestion ->
                                FilterChip(
                                    selected = selectedSubType == suggestion,
                                    onClick = { selectedSubType = suggestion; customSubTypeText = suggestion },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                                        labelColor = OnBackground.copy(0.7f),
                                    ),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customSubTypeText,
                        onValueChange = { customSubTypeText = it; selectedSubType = it.ifBlank { null } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.activity_subtype_custom_label)) },
                        singleLine = true,
                        colors = scanEatTextFieldColors(),
                    )
                    if (selectedType == ActivityType.STRENGTH) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            OutlinedTextField(
                                value = setsText, onValueChange = { setsText = it }, modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.activity_sets_label)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = scanEatTextFieldColors(),
                            )
                            OutlinedTextField(
                                value = repsText, onValueChange = { repsText = it }, modifier = Modifier.weight(1f),
                                label = { Text(stringResource(R.string.activity_reps_label)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = scanEatTextFieldColors(),
                            )
                        }
                        OutlinedTextField(
                            value = weightUsedText, onValueChange = { weightUsedText = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.activity_weight_used_label)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = scanEatTextFieldColors(),
                        )
                    }
                    if (selectedType == ActivityType.RUNNING || selectedType == ActivityType.CYCLING || selectedType == ActivityType.SWIMMING) {
                        OutlinedTextField(
                            value = distanceText, onValueChange = { distanceText = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.activity_distance_label)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = scanEatTextFieldColors(),
                        )
                    }
                    val minutes = minutesText.toIntOrNull()
                    val minutesValid = minutes != null && minutes in 1..1440
                    OutlinedTextField(
                        value = minutesText, onValueChange = { minutesText = it },
                        label = { Text(stringResource(R.string.activity_duration_label)) }, singleLine = true,
                        isError = minutesText.isNotBlank() && !minutesValid,
                        supportingText = {
                            if (minutesText.isNotBlank() && !minutesValid) {
                                Text(stringResource(R.string.activity_duration_invalid), color = semanticRed())
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = scanEatTextFieldColors(),
                    )
                }
            },
            confirmButton = {
                val minutesValid = (minutesText.toIntOrNull() ?: 0) in 1..1440
                TextButton(
                    onClick = {
                        minutesText.toIntOrNull()?.let { min ->
                            viewModel.log(
                                selectedType, min,
                                subType = selectedSubType,
                                sets = setsText.toIntOrNull(),
                                reps = repsText.toIntOrNull(),
                                distanceKm = distanceText.replace(',', '.').toDoubleOrNull(),
                                weightUsedKg = weightUsedText.replace(',', '.').toDoubleOrNull(),
                            )
                            showAdd = false
                            selectedSubType = null; customSubTypeText = ""; setsText = ""; repsText = ""; distanceText = ""; weightUsedText = ""
                        }
                    },
                    enabled = minutesValid,
                ) { Text(stringResource(R.string.common_add), color = if (minutesValid) AccentCoral else OnBackground.copy(0.3f)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
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
