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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.scanneat.presentation.ui.theme.semanticRed
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.presentation.reminders.components.PermissionBanner
import fr.scanneat.presentation.reminders.components.permissionState
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

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
    var renameTarget by remember { mutableStateOf<Medication?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var reminderTarget by remember { mutableStateOf<Medication?>(null) }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = Teal, secondary = AccentCoral)
                .padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }
            // Previously no calendar entry point at all for Traitement (unlike
            // Weight/Activity/Hydration, which each had their own local one) -
            // routes straight to the unified Calendar rather than embedding
            // another single-domain grid here.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // New: daily adherence streak badge
                    if (adherenceStreak.value > 0) {
                        Surface(shape = RoundedCornerShape(50), color = Teal.copy(0.15f)) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                            ) {
                                Icon(Icons.Default.LocalFireDepartment, null, tint = Teal, modifier = Modifier.size(16.dp))
                                Text("${adherenceStreak.value}j", style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.medication_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }

            // Improvement: drug interaction warning banners. Safety-relevant text, so it
            // must follow the app's own language setting rather than a hardcoded one —
            // the ViewModel returns typed InteractionWarning values (it has no
            // stringResource() access) and this is where they're localized.
            if (interactionWarnings.value.isNotEmpty()) {
                items(interactionWarnings.value) { warning ->
                    val message = when (warning) {
                        is InteractionWarning.GroupDuplicate -> {
                            val groupLabel = when (warning.group) {
                                DrugGroup.ANTICOAGULANTS -> stringResource(R.string.medication_group_anticoagulants)
                                DrugGroup.ANTIPLATELETS  -> stringResource(R.string.medication_group_antiplatelets)
                                DrugGroup.NSAIDS         -> stringResource(R.string.medication_group_nsaids)
                                DrugGroup.SSRI_SNRI      -> stringResource(R.string.medication_group_ssri_snri)
                                DrugGroup.MAOI           -> stringResource(R.string.medication_group_maoi)
                            }
                            stringResource(R.string.medication_interaction_group_dup, groupLabel)
                        }
                        is InteractionWarning.AnticoagNsaid -> stringResource(R.string.medication_interaction_anticoag_nsaid)
                        is InteractionWarning.SsriMaoi      -> stringResource(R.string.medication_interaction_ssri_maoi)
                    }
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(0.1f), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Icon(Icons.Default.Warning, null, tint = semanticRed(), modifier = Modifier.size(18.dp))
                            Column {
                                Text(stringResource(R.string.medication_interaction_title), style = MaterialTheme.typography.labelMedium, color = semanticRed(), fontWeight = FontWeight.Bold)
                                Text(message, style = MaterialTheme.typography.bodySmall, color = semanticRed().copy(0.8f))
                                Text(stringResource(R.string.medication_interaction_cta), style = MaterialTheme.typography.labelSmall, color = semanticRed().copy(0.6f))
                            }
                        }
                    }
                }
            }

            // Today's adherence summary: compact chip row showing taken/not-taken per medication.
            // Previously you had to scroll through the full list to see overall adherence — no aggregate view existed.
            if (medications.value.filter { it.active }.size >= 2) {
                item {
                    val active = medications.value.filter { it.active }
                    val allTaken = active.all { m -> todayTaken.value.any { it.medicationId == m.id } }
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = if (allTaken) Teal.copy(0.1f) else SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Text(
                                stringResource(R.string.medication_today_summary_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurface.copy(0.5f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                                active.forEach { m ->
                                    val taken = todayTaken.value.any { it.medicationId == m.id }
                                    Surface(shape = RoundedCornerShape(50), color = if (taken) Teal.copy(0.2f) else OnSurface.copy(0.08f)) {
                                        Row(
                                            Modifier.padding(horizontal = Spacing.S, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Icon(
                                                if (taken) Icons.Default.Check else Icons.Default.Close,
                                                null,
                                                tint = if (taken) Teal else OnSurface.copy(0.35f),
                                                modifier = Modifier.size(10.dp),
                                            )
                                            Text(
                                                m.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (taken) Teal else OnSurface.copy(0.5f),
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Weekly adherence chart - adherenceStreak above only reports the current
            // unbroken run, which resets to 0 on the very first miss. Weight/Activity/
            // Hydration all have a 7-day chart showing the actual week's shape; this was
            // the one tracker where a single bad day made the week look identical to
            // one with zero activity at all (both just "streak: 0").
            if (medications.value.any { it.active }) {
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Text(stringResource(R.string.medication_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                                weeklyAdherence.value.forEach { day ->
                                    val frac = (day.pct ?: 0) / 100f
                                    val barColor = when {
                                        day.pct == null -> OnSurface.copy(0.12f)
                                        day.pct >= 100   -> Teal
                                        day.pct > 0      -> semanticAmber()
                                        else             -> semanticRed()
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(0.04f)).background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                weeklyAdherence.value.forEach { day ->
                                    Text(
                                        day.date.dayOfWeek.name.take(1),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = OnSurface.copy(if (day.date == LocalDate.now()) 0.8f else 0.4f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
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
                ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, contentPadding = PaddingValues(Spacing.M)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        // Left at IconButton's default 48dp touch target (Material/WCAG
                        // minimum) - a UI/UX audit found this row forcing 4 icon-sized
                        // controls (plus a Switch) below the 48dp minimum.
                        IconButton(onClick = { if (takenToday != null) viewModel.undoTaken(takenToday) else viewModel.markTaken(m) }) {
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
                        // which all fire a real notification. Kept visible (not in the
                        // overflow menu below) since its icon tint doubles as an at-a-glance
                        // "reminder on/off" indicator, unlike Rename/Delete.
                        IconButton(onClick = { reminderTarget = m }) {
                            Icon(
                                Icons.Default.Notifications,
                                stringResource(R.string.medication_reminder_cd),
                                tint = if (m.reminderOn) Teal else OnSurface.copy(0.4f),
                            )
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_rename)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; renameTarget = m },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_delete)) },
                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                onClick = { menuExpanded = false; deleteTarget = m.id },
                            )
                        }
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
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    } else {
        Scaffold(
            topBar = {
                FloatingTopBar(
                    title = { Text(stringResource(R.string.medication_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                    actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.medication_cd_new), tint = Teal) } },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Background,
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        var name by rememberSaveable { mutableStateOf("") }
        var dosage by rememberSaveable { mutableStateOf("") }
        var scheduleNote by rememberSaveable { mutableStateOf("") }
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
        var on by rememberSaveable(m.id) { mutableStateOf(m.reminderOn) }
        var time by rememberSaveable(m.id) { mutableStateOf(m.reminderTime) }
        val isValidTime = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
        // Every sibling reminder card (meal/hydration/weight/activity, see
        // RemindersCard.kt) shows this banner - this dialog didn't, so a user with
        // POST_NOTIFICATIONS denied could enable a medication reminder that would
        // silently never fire (NotificationHelper.show() no-ops without the
        // permission), with the switch looking "on" and nothing telling them why.
        val (permGranted, permDenied, onRequest) = permissionState()
        AlertDialog(
            onDismissRequest = { reminderTarget = null },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.medication_reminder_dialog_title, m.name), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PermissionBanner(permGranted, permDenied, onRequest)
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
