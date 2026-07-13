package fr.scanneat.presentation.activity

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.ACTIVITY_SUB_TYPES
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Composable
private fun typeLabels(): Map<ActivityType, String> = mapOf(
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
    onOpenCalendar: () -> Unit = {},
) {
    // Refresh date when screen becomes active (handles midnight crossing)
    LaunchedEffect(Unit) { viewModel.refreshDate() }

    val entries      = viewModel.entries.collectAsStateWithLifecycle()
    val pastSubTypes = viewModel.pastSubTypes.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.WALKING_BRISK) }
    var minutesText by remember { mutableStateOf("30") }
    var selectedSubType by remember { mutableStateOf<String?>(null) }
    var customSubTypeText by remember { mutableStateOf("") }
    var setsText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("") }
    var distanceText by remember { mutableStateOf("") }
    var weightUsedText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val typeLabels = typeLabels()
    val subTypeLabels = subTypeLabels()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.activity_deleted_message)
    val undoLabel = stringResource(R.string.activity_undo)

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Previously an inline single-domain MonthCalendar toggled here;
            // now routes to the unified Calendar (Dashboard), which shows
            // activity alongside every other tracker.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
                    ScanEatCard(shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
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

            items(entries.value, key = { it.id }) { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant).padding(Spacing.M),
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

            if (entries.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.DirectionsRun, stringResource(R.string.activity_empty),
                        ctaLabel = stringResource(R.string.activity_add_cta), onCta = { showAdd = true },
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.activity_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                    actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
            containerColor = Background,
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
                    // Type picker
                    Text(stringResource(R.string.activity_type_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeLabels.forEach { (type, label) ->
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
                    val minutesValid = minutes != null && minutes > 0
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
                val minutesValid = (minutesText.toIntOrNull() ?: 0) > 0
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
