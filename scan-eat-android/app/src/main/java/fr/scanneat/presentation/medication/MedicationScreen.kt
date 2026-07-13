package fr.scanneat.presentation.medication

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.presentation.ui.theme.*

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar - used when
 * hosted as a Journal sub-tab, same convention as Weight/Hydration/Activity/
 * Fasting. Manual entry only for now - barcode-scan medication lookup and a
 * curated medication database are a larger, separate effort.
 */
@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    onOpenCalendar: () -> Unit = {},
) {
    val medications = viewModel.medications.collectAsStateWithLifecycle()
    val todayTaken = viewModel.todayTaken.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Medication?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var reminderTarget by remember { mutableStateOf<Medication?>(null) }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }
            // Previously no calendar entry point at all for Traitement (unlike
            // Weight/Activity/Hydration, which each had their own local one) -
            // routes straight to the unified Calendar rather than embedding
            // another single-domain grid here.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.medication_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }
            if (medications.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.Medication, stringResource(R.string.medication_empty_body),
                        ctaLabel = stringResource(R.string.medication_cd_new), onCta = { showAdd = true },
                    )
                }
            }
            items(medications.value, key = { it.id }) { m ->
                val takenToday = todayTaken.value.find { it.medicationId == m.id }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).background(SurfaceVariant).padding(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        val details = listOfNotNull(
                            m.dosage.takeIf { it.isNotBlank() },
                            m.scheduleNote.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (details.isNotBlank()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                        }
                    }
                    // "Taken today" - previously there was no way to log a dose at all,
                    // only to keep/remove a medication from the active list.
                    IconButton(
                        onClick = { if (takenToday != null) viewModel.undoTaken(takenToday) else viewModel.markTaken(m) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (takenToday != null) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                            stringResource(R.string.medication_cd_taken_today),
                            tint = if (takenToday != null) Teal else OnSurface.copy(0.4f),
                        )
                    }
                    Switch(
                        checked = m.active, onCheckedChange = { viewModel.setActive(m, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Teal),
                    )
                    // Previously "schedule" was display-only text — no way to actually
                    // be reminded to take a medication, unlike Fasting/Hydration/Weight
                    // which all fire a real notification.
                    IconButton(onClick = { reminderTarget = m }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Notifications,
                            stringResource(R.string.medication_reminder_cd),
                            tint = if (m.reminderOn) Teal else OnSurface.copy(0.4f),
                        )
                    }
                    IconButton(onClick = { renameTarget = m }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, stringResource(R.string.common_rename), tint = OnSurface.copy(0.5f))
                    }
                    IconButton(onClick = { deleteTarget = m.id }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f))
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.L),
                containerColor = Teal,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = androidx.compose.ui.graphics.Color.Black) }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.medication_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                    actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.medication_cd_new), tint = Teal) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
            containerColor = Background,
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var dosage by remember { mutableStateOf("") }
        var scheduleNote by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.medication_add_dialog_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.medication_field_name)) }, singleLine = true, colors = scanEatTextFieldColors())
                    OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text(stringResource(R.string.medication_field_dosage)) }, singleLine = true, colors = scanEatTextFieldColors())
                    OutlinedTextField(value = scheduleNote, onValueChange = { scheduleNote = it }, label = { Text(stringResource(R.string.medication_field_schedule)) }, singleLine = true, colors = scanEatTextFieldColors())
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.save(name, dosage, scheduleNote); showAdd = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.common_create), color = Teal)
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

    renameTarget?.let { m ->
        RenameDialog(currentName = m.name, onDismiss = { renameTarget = null }, onConfirm = { newName -> viewModel.rename(m, newName); renameTarget = null })
    }

    deleteTarget?.let { id ->
        val name = medications.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

    reminderTarget?.let { m ->
        var on by remember(m.id) { mutableStateOf(m.reminderOn) }
        var time by remember(m.id) { mutableStateOf(m.reminderTime) }
        val isValidTime = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
        AlertDialog(
            onDismissRequest = { reminderTarget = null },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.medication_reminder_dialog_title, m.name), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.medication_reminder_toggle), color = OnBackground.copy(0.8f))
                        Switch(checked = on, onCheckedChange = { on = it }, colors = SwitchDefaults.colors(checkedTrackColor = Teal))
                    }
                    OutlinedTextField(
                        value = time, onValueChange = { time = it },
                        label = { Text(stringResource(R.string.medication_reminder_time_label)) },
                        placeholder = { Text("08:00") }, singleLine = true,
                        isError = !isValidTime,
                        colors = scanEatTextFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.setReminder(m, on, time); reminderTarget = null },
                    enabled = isValidTime,
                ) { Text(stringResource(R.string.common_save), color = Teal) }
            },
            dismissButton = { TextButton(onClick = { reminderTarget = null }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }
}
