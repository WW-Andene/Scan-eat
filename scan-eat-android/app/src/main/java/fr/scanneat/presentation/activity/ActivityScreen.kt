package fr.scanneat.presentation.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    // Refresh date when screen becomes active (handles midnight crossing)
    LaunchedEffect(Unit) { viewModel.refreshDate() }

    val entries = viewModel.entries.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(ActivityType.WALKING_BRISK) }
    var minutesText by remember { mutableStateOf("30") }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val typeLabels = typeLabels()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentGreen) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
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
                                Text("$totalMin", style = MaterialTheme.typography.titleLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.activity_minutes_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                            }
                        }
                    }
                }
            }

            items(entries.value) { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(typeLabels[e.type] ?: e.type.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.activity_entry_summary, e.minutes, e.kcalBurned), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                    }
                    IconButton(onClick = { deleteTarget = e.id }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (entries.value.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.activity_empty), color = OnBackground.copy(0.4f))
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
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
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                                    labelColor = OnBackground.copy(0.7f),
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = minutesText, onValueChange = { minutesText = it },
                        label = { Text(stringResource(R.string.activity_duration_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    minutesText.toIntOrNull()?.let { min ->
                        viewModel.log(selectedType, min); showAdd = false
                    }
                }) { Text(stringResource(R.string.common_add), color = AccentGreen) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = SurfaceVariant,
            title   = { Text(stringResource(R.string.common_delete_confirm_title), color = OnBackground) },
            text    = { Text(stringResource(R.string.common_delete_confirm_body), color = OnBackground.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); deleteTarget = null }) {
                    Text(stringResource(R.string.common_delete), color = FlagRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
            },
        )
    }

}
