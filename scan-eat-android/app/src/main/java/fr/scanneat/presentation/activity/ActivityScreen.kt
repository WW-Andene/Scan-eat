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
) {
    // Refresh date when screen becomes active (handles midnight crossing)
    LaunchedEffect(Unit) { viewModel.refreshDate() }

    val entries = viewModel.entries.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.WALKING_BRISK) }
    var minutesText by remember { mutableStateOf("30") }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val typeLabels = typeLabels()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.activity_deleted_message)
    val undoLabel = stringResource(R.string.activity_undo)

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Daily burned summary
            val totalKcal = entries.value.sumOf { it.kcalBurned }
            val totalMin  = entries.value.sumOf { it.minutes }
            if (totalKcal > 0) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalKcal", style = MaterialTheme.typography.titleLarge, color = FlagRed, fontWeight = FontWeight.Bold)
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
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(typeLabels[e.type] ?: e.type.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.activity_entry_summary, e.minutes, e.kcalBurned), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Type picker
                    Text(stringResource(R.string.activity_type_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        typeLabels.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedType == type, onClick = { selectedType = type },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                    labelColor = OnBackground.copy(0.7f),
                                ),
                            )
                        }
                    }
                    val minutes = minutesText.toIntOrNull()
                    val minutesValid = minutes != null && minutes > 0
                    OutlinedTextField(
                        value = minutesText, onValueChange = { minutesText = it },
                        label = { Text(stringResource(R.string.activity_duration_label)) }, singleLine = true,
                        isError = minutesText.isNotBlank() && !minutesValid,
                        supportingText = {
                            if (minutesText.isNotBlank() && !minutesValid) {
                                Text(stringResource(R.string.activity_duration_invalid), color = FlagRed)
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
                            viewModel.log(selectedType, min); showAdd = false
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
