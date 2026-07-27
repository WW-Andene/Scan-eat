package fr.scanneat.presentation.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.presentation.medication.components.AddMedicationDialog
import fr.scanneat.presentation.medication.components.MedicationEntryRow
import fr.scanneat.presentation.medication.components.MedicationInteractionWarningBanner
import fr.scanneat.presentation.medication.components.MedicationReminderDialog
import fr.scanneat.presentation.medication.components.MedicationStreakRow
import fr.scanneat.presentation.medication.components.MedicationTodaySummaryCard
import fr.scanneat.presentation.medication.components.MedicationWeeklyAdherenceChart
import fr.scanneat.presentation.ui.theme.*

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar - used when
 * hosted as a Journal sub-tab, same convention as Weight/Hydration/Activity/
 * Fasting. Entry here is manual (name/dosage/schedule); scanning a box's
 * barcode from the Scan tab is the other entry point — see ScanViewModel's
 * MedicationFound state, which resolves against the real BDPM database and
 * saves into the same repository this screen reads from.
 */
@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    // Only meaningful when [embedded] — the host (DiaryScreen) supplies this so
    // this screen's own LazyColumn reserves the same floating-bottom-nav
    // clearance the host itself is already reserving.
    embeddedBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val medications          = viewModel.medications.collectAsStateWithLifecycle()
    val todayTaken           = viewModel.todayTaken.collectAsStateWithLifecycle()
    val interactionWarnings  = viewModel.interactionWarnings.collectAsStateWithLifecycle()
    val adherenceStreak      = viewModel.adherenceStreak.collectAsStateWithLifecycle()
    val weeklyAdherence      = viewModel.weeklyAdherence.collectAsStateWithLifecycle()

    // markTaken/save/rename/delete etc. previously failed completely silently -
    // see MedicationViewModel.actionFailed's own comment.
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Medication?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var reminderTarget by remember { mutableStateOf<Medication?>(null) }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = Teal, secondary = AccentCoral)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }
            // Previously no calendar entry point at all for Traitement (unlike
            // Weight/Activity/Hydration, which each had their own local one) -
            // routes straight to the unified Calendar rather than embedding
            // another single-domain grid here.
            item { MedicationStreakRow(streakDays = adherenceStreak.value, onOpenCalendar = onOpenCalendar) }

            // Improvement: drug interaction warning banners. Safety-relevant text, so it
            // must follow the app's own language setting rather than a hardcoded one —
            // the ViewModel returns typed InteractionWarning values (it has no
            // stringResource() access) and this is where they're localized.
            if (interactionWarnings.value.isNotEmpty()) {
                items(interactionWarnings.value, key = { warning ->
                    when (warning) {
                        is InteractionWarning.GroupDuplicate -> "dup-${warning.group.name}"
                        InteractionWarning.AnticoagNsaid -> "anticoag-nsaid"
                        InteractionWarning.SsriMaoi -> "ssri-maoi"
                    }
                }) { warning -> MedicationInteractionWarningBanner(warning) }
            }

            // Today's adherence summary: compact chip row showing taken/not-taken per medication.
            // Previously you had to scroll through the full list to see overall adherence — no aggregate view existed.
            if (medications.value.filter { it.active }.size >= 2) {
                item { MedicationTodaySummaryCard(medications = medications.value, todayTaken = todayTaken.value) }
            }

            // Weekly adherence chart - adherenceStreak above only reports the current
            // unbroken run, which resets to 0 on the very first miss. Weight/Activity/
            // Hydration all have a 7-day chart showing the actual week's shape; this was
            // the one tracker where a single bad day made the week look identical to
            // one with zero activity at all (both just "streak: 0").
            if (medications.value.any { it.active }) {
                item { MedicationWeeklyAdherenceChart(weeklyAdherence.value) }
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
                MedicationEntryRow(
                    medication = m,
                    takenToday = takenToday,
                    onToggleTaken = { if (takenToday != null) viewModel.undoTaken(takenToday) else viewModel.markTaken(m) },
                    onSetActive = { active -> viewModel.setActive(m, active) },
                    onOpenReminder = { reminderTarget = m },
                    onEdit = { editTarget = m },
                    onDelete = { deleteTarget = m.id },
                )
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(bottom = embeddedBottomPadding))
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = embeddedBottomPadding + Spacing.L, end = Spacing.L),
                containerColor = Teal,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = androidx.compose.ui.graphics.Color.Black) }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.medication_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.medication_cd_new), tint = Teal) } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AddMedicationDialog(onDismiss = { showAdd = false }, onSave = { name, dosage, scheduleNote ->
            viewModel.save(name, dosage, scheduleNote)
            showAdd = false
        })
    }

    editTarget?.let { m ->
        AddMedicationDialog(
            isEdit = true,
            initialName = m.name,
            initialDosage = m.dosage,
            initialScheduleNote = m.scheduleNote,
            onDismiss = { editTarget = null },
            onSave = { name, dosage, scheduleNote -> viewModel.edit(m, name, dosage, scheduleNote); editTarget = null },
        )
    }

    deleteTarget?.let { id ->
        val name = medications.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

    reminderTarget?.let { m ->
        MedicationReminderDialog(
            medication = m,
            onDismiss = { reminderTarget = null },
            onSave = { on, time -> viewModel.setReminder(m, on, time); reminderTarget = null },
        )
    }
}
