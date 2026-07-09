package fr.scanneat.presentation.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
) {
    val summary      = viewModel.summary.collectAsStateWithLifecycle()
    val selectedDate = viewModel.selectedDate.collectAsStateWithLifecycle()
    val isToday      = viewModel.isToday.collectAsStateWithLifecycle(initialValue = true)
    val dayNote      = viewModel.dayNote.collectAsStateWithLifecycle(initialValue = "")
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    // Fix 9: initialise to empty on date change; LaunchedEffect collects the stored
    // note once per date and sets it — won't fire again while the user is typing because
    // selectedDate.value doesn't change until they navigate to a different day.
    var noteText by remember(selectedDate.value) { mutableStateOf("") }
    LaunchedEffect(selectedDate.value) {
        // Collect the first stored note for this date then stop — avoids overwriting typing
        viewModel.dayNote.collect { stored -> noteText = stored }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.diary_header), color = OnBackground, fontWeight = FontWeight.Bold)
                        Text(selectedDate.value.format(DATE_FMT), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                    }
                },
                navigationIcon = {
                    if (!isTabRoot) {
                        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToPreviousDay() }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.diary_cd_prev_day), tint = OnBackground) }
                    if (!isToday.value) {
                        TextButton(onClick = { viewModel.goToToday() }) { Text(stringResource(R.string.diary_today_button), color = AccentGreen, style = MaterialTheme.typography.labelMedium) }
                    }
                    IconButton(onClick = { viewModel.goToNextDay() }, enabled = !isToday.value) {
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.diary_cd_next_day), tint = if (!isToday.value) OnBackground else OnBackground.copy(0.3f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        val s = summary.value
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { MacroSummaryCard(totals = s.totals) }
                item {
                    // Day note field
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.diary_note_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        if (noteText != dayNote.value) {
                            IconButton(onClick = { viewModel.saveNote(noteText) }) {
                                Icon(Icons.Default.Check, stringResource(R.string.diary_cd_save_note), tint = AccentGreen)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )

                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                }
                if (s.entries.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.diary_empty_body),
                                color = OnBackground.copy(0.5f))
                        }
                    }
                } else {
                    // Group by meal slot
                    MealSlot.values().forEach { slot ->
                        val slotEntries = s.entries.filter { it.mealSlot == slot }
                        if (slotEntries.isNotEmpty()) {
                            item {
                                Text(
                                    slot.diaryLabel(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentGreen.copy(0.8f),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items(slotEntries) { entry ->
                                DiaryEntryCard(entry = entry, onDelete = { deleteTarget = entry.id })
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    

        // Delete confirmation
        deleteTarget?.let { id ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                containerColor   = SurfaceVariant,
                title   = { Text(stringResource(R.string.diary_delete_confirm_title), color = OnBackground) },
                text    = { Text(stringResource(R.string.diary_delete_confirm_body), color = OnBackground.copy(0.7f)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.deleteEntry(id); deleteTarget = null }) {
                        Text(stringResource(R.string.common_delete), color = FlagRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
                },
            )
        }
    }

@Composable
private fun MacroSummaryCard(totals: ConsumedNutrition) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.diary_totals_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                MacroItem(stringResource(R.string.diary_macro_calories), "${totals.energyKcal.toInt()}", "kcal")
                MacroItem(stringResource(R.string.diary_macro_protein), "${totals.proteinG.toInt()}", "g")
                MacroItem(stringResource(R.string.diary_macro_carbs), "${totals.carbsG.toInt()}", "g")
                MacroItem(stringResource(R.string.diary_macro_fat), "${totals.fatG.toInt()}", "g")
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
    }
}

@Composable
private fun DiaryEntryCard(entry: DiaryEntry, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(stringResource(R.string.diary_entry_summary, entry.portionG.toInt(), entry.consumed.energyKcal.toInt()),
                style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

/** Diary keeps its own (fuller) wording for breakfast — distinct from the abbreviated shared label. */
@Composable
private fun MealSlot.diaryLabel(): String = when (this) {
    MealSlot.BREAKFAST -> stringResource(R.string.diary_meal_breakfast)
    MealSlot.LUNCH     -> stringResource(R.string.meal_lunch)
    MealSlot.SNACK     -> stringResource(R.string.meal_snack)
    MealSlot.DINNER    -> stringResource(R.string.meal_dinner)
}
