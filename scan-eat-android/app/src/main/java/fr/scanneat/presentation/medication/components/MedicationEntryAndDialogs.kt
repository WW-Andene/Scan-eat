package fr.scanneat.presentation.medication.components

import compose.icons.tablericons.Bell
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Edit
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.util.formatDecimal
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.domain.engine.medication.MedicationDbEntry
import fr.scanneat.domain.engine.medication.generateMedicationHints
import fr.scanneat.presentation.reminders.components.PermissionBanner
import fr.scanneat.presentation.reminders.components.permissionState
import fr.scanneat.presentation.result.FactsCautionsColumn
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun MedicationEntryRow(
    medication: Medication,
    takenToday: MedicationLogEntry?,
    onToggleTaken: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onOpenReminder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    // R&D audit finding: Medication had zero cross-reference into Weight -
    // purely descriptive (weight before vs. after this medication's start
    // date), never a causal claim. Null when there isn't enough weight
    // history to compute a real "since" delta (see MedicationViewModel.
    // weightDeltaSinceStart's own doc comment).
    weightDeltaKg: Double? = null,
    // User-requested: there was no way to see a saved medication's own
    // information sheet at all - see MedicationDetailDialog's own doc
    // comment. Row tap now opens that (same "row = view detail, pencil =
    // edit" split DiaryEntryCard already uses), defaulting to onEdit so any
    // other call site that doesn't pass this keeps its old tap-to-edit
    // behavior unchanged.
    onOpenDetail: () -> Unit = onEdit,
) {
    val m = medication
    val haptics = LocalHapticFeedback.current
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M), onClick = onOpenDetail) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
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
                weightDeltaKg?.let { delta ->
                    val sign = if (delta >= 0) "+" else ""
                    // app-audit §J1: formatDecimal() (Locale.US) instead of a bare "%.1f".format(delta)
                    // - the same missing-Locale.US bug class UnitConversion.kt's dispWeight() was
                    // hardened against (a French-locale device would otherwise render "70,5" comma
                    // decimal here, inconsistent with every other weight figure in the app).
                    Text(
                        stringResource(R.string.medication_weight_since_start, "$sign${delta.formatDecimal()}"),
                        style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.45f),
                    )
                }
            }
            // "Taken today" - previously there was no way to log a dose at all,
            // only to keep/remove a medication from the active list.
            // Left at IconButton's default 48dp touch target (Material/WCAG
            // minimum) - a UI/UX audit found this row forcing 4 icon-sized
            // controls (plus a Switch) below the 48dp minimum.
            // Haptic on toggle - same tactile vocabulary as Grocery's checkbox
            // toggle (GroceryScreen.kt), a near-identical discrete on/off
            // interaction that already had one while this one didn't.
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggleTaken() }) {
                Icon(
                    if (takenToday != null) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline,
                    stringResource(if (takenToday != null) R.string.medication_cd_undo_taken else R.string.medication_cd_taken_today),
                    tint = if (takenToday != null) Teal else OnSurface.copy(0.4f),
                )
            }
            // Was the only toggle in this row with no haptic feedback - the adjacent
            // "taken today" IconButton already fires one, so two binary toggles
            // right next to each other gave inconsistent tactile confirmation.
            Switch(
                checked = m.active,
                onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSetActive(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Teal),
            )
            // Previously "schedule" was display-only text — no way to actually
            // be reminded to take a medication, unlike Fasting/Hydration/Weight
            // which all fire a real notification. Kept visible (not in the
            // overflow menu below) since its icon tint doubles as an at-a-glance
            // "reminder on/off" indicator, unlike Rename/Delete.
            IconButton(onClick = onOpenReminder) {
                Icon(
                    TablerIcons.Bell,
                    stringResource(R.string.medication_reminder_cd),
                    tint = if (m.reminderOn) Teal else OnSurface.copy(0.4f),
                )
            }
            // A fresh UX audit flagged Medication as the one tracker whose Edit
            // action was hidden behind an overflow menu while Weight/Activity both
            // expose an always-visible pencil icon - a user who just learned "tap
            // the pencil" on those two trackers found no pencil at all here. Delete
            // stays in the overflow (Medication already has 2 more always-visible
            // icons than Weight/Activity - taken-today + reminder - so there isn't
            // row width for a 5th icon button).
            IconButton(onClick = onEdit) {
                Icon(TablerIcons.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.5f))
            }
            var menuExpanded by remember { mutableStateOf(false) }
            val (menuAnchorWidth, menuWidthTracker) = rememberTrackedWidth()
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = menuWidthTracker) {
                    Icon(TablerIcons.DotsVertical, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
                }
                // DROPDOWN_MENU_GAP - app-wide standard gap between a DropdownMenu and its trigger (see its own doc comment).
                ScanEatDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, anchorWidth = menuAnchorWidth) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(TablerIcons.X, contentDescription = null) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, dosage: String, scheduleNote: String) -> Unit,
    initialName: String = "",
    initialDosage: String = "",
    initialScheduleNote: String = "",
    isEdit: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dosage by rememberSaveable { mutableStateOf(initialDosage) }
    var scheduleNote by rememberSaveable { mutableStateOf(initialScheduleNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(if (isEdit) R.string.medication_edit_dialog_title else R.string.medication_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.medication_field_name)) }, singleLine = true, colors = scanEatTextFieldColors())
                OutlinedTextField(value = dosage, onValueChange = { dosage = it }, label = { Text(stringResource(R.string.medication_field_dosage)) }, placeholder = { Text(stringResource(R.string.medication_field_dosage_hint)) }, singleLine = true, colors = scanEatTextFieldColors())
                OutlinedTextField(value = scheduleNote, onValueChange = { scheduleNote = it }, label = { Text(stringResource(R.string.medication_field_schedule)) }, singleLine = true, colors = scanEatTextFieldColors())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, dosage, scheduleNote) }, enabled = name.isNotBlank()) {
                Text(stringResource(if (isEdit) R.string.common_save else R.string.common_create), color = Teal)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}

@Composable
internal fun MedicationReminderDialog(
    medication: Medication,
    onDismiss: () -> Unit,
    // User-requested: medication tracking previously had no structured dosing
    // schedule at all - a single daily reminderTime and a free-text
    // scheduleNote ("2x/jour", "lundi/mercredi/vendredi") that never actually
    // drove the reminder. daysMask: 0 = every day (see Medication.
    // isScheduledOn); extraTimes: every dose time beyond the primary one.
    onSave: (on: Boolean, time: String, daysMask: Int, extraTimes: List<String>) -> Unit,
    // In-app language, not device locale - see MedicationViewModel.language's
    // own doc comment.
    language: String = "fr",
) {
    val m = medication
    var on by rememberSaveable(m.id) { mutableStateOf(m.reminderOn) }
    var time by rememberSaveable(m.id) { mutableStateOf(m.reminderTime) }
    var daysMask by rememberSaveable(m.id) { mutableStateOf(m.scheduleDaysMask) }
    var extraTimes by rememberSaveable(m.id) {
        mutableStateOf(m.extraReminderTimes.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }
    val isValidTime = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
    val invalidExtraTime = remember(extraTimes) { extraTimes.any { runCatching { java.time.LocalTime.parse(it) }.isFailure } }
    // Every sibling reminder card (meal/hydration/weight/activity, see
    // RemindersCard.kt) shows this banner - this dialog didn't, so a user with
    // POST_NOTIFICATIONS denied could enable a medication reminder that would
    // silently never fire (NotificationHelper.show() no-ops without the
    // permission), with the switch looking "on" and nothing telling them why.
    val (permGranted, permDenied, onRequest) = permissionState()
    val locale = remember(language) { java.util.Locale(language) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
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
                // Additional dose times ("matin et soir") - each fires and
                // re-notifies independently (see ReminderWorker's per-slot key).
                Text(stringResource(R.string.medication_reminder_extra_times_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                extraTimes.forEachIndexed { index, extraTime ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        OutlinedTextField(
                            value = extraTime,
                            onValueChange = { newValue -> extraTimes = extraTimes.toMutableList().also { it[index] = newValue } },
                            placeholder = { Text("20:00") }, singleLine = true,
                            isError = runCatching { java.time.LocalTime.parse(extraTime) }.isFailure,
                            modifier = Modifier.weight(1f),
                            colors = scanEatTextFieldColors(),
                        )
                        IconButton(onClick = { extraTimes = extraTimes.toMutableList().also { it.removeAt(index) } }) {
                            Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f))
                        }
                    }
                }
                TextButton(onClick = { extraTimes = extraTimes + "12:00" }) {
                    Text(stringResource(R.string.medication_reminder_add_time), color = Teal)
                }
                // Day-of-week picker - an empty selection means "every day" (mask 0),
                // matching every pre-existing medication's implicit behavior; tapping
                // any day switches to that explicit subset.
                Text(stringResource(R.string.medication_reminder_days_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    java.time.DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        val selected = daysMask != 0 && (daysMask and bit) != 0
                        FilterChip(
                            selected = selected,
                            onClick = { daysMask = if (selected) daysMask and bit.inv() else daysMask or bit },
                            // app-audit §J2: SHORT, not NARROW - NARROW collapses to a single
                            // letter ambiguous in both supported languages (FR: Mardi/Mercredi
                            // both "M"; EN: Tuesday/Thursday both "T"), same bug class
                            // WeeklyBarsCard.kt already found and fixed - worse here since
                            // these are individually-tappable day-selector chips, not just
                            // read-only labels, so ambiguity risks toggling the wrong day.
                            label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f),
                                selectedLabelColor = AccentCoral,
                                labelColor = OnBackground.copy(0.7f),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Time correctness is irrelevant when the reminder is being turned off -
            // was gating Save on isValidTime unconditionally, so a stale/blank time
            // field blocked a user from saving even when their only intent was to
            // disable the reminder.
            val canSave = !on || (isValidTime && !invalidExtraTime)
            TextButton(
                onClick = { onSave(on, time, daysMask, extraTimes) },
                enabled = canSave,
            ) { Text(stringResource(R.string.common_save), color = if (canSave) Teal else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}

/**
 * User-requested: "there's no info panel for medications" - a saved
 * medication had no way to see its own composition/side-effects/risks/
 * pregnancy-and-condition-specific cautions/usage advice again after the
 * one-time MedicationFound dialog shown at scan time (ScanStateOverlay.kt) -
 * once added to Traitement, that information was gone for good. Not a
 * nutrition score (there's nothing to score for a medication) - reuses the
 * exact same sourced facts/cautions generateMedicationHints() already
 * produces from the real BDPM record (composition, dispensing condition,
 * Wikipedia-sourced substance facts, EU SmPC-level drug-class cautions
 * cross-referenced against the user's own Profile.healthConditions - see
 * MedicationSubstanceDb.kt's own doc comment on why this is deliberately
 * NOT fabricated dosage/diagnosis/treatment advice).
 *
 * [dbEntry] is null while the async BDPM lookup is still running OR when it
 * genuinely found nothing (no barcode, or no BDPM match for a manually-typed
 * name) - shown as an honest "no detailed info available" state rather than
 * inventing content for a medication this app can't actually identify.
 */
@Composable
internal fun MedicationDetailDialog(
    medication: Medication,
    dbEntry: MedicationDbEntry?,
    isLoading: Boolean,
    healthConditions: Set<String>,
    language: String,
    onDismiss: () -> Unit,
) {
    val m = medication
    val hints = remember(dbEntry, healthConditions, language) {
        dbEntry?.let { generateMedicationHints(it, healthConditions, language) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(m.name, color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                if (m.dosage.isNotBlank()) {
                    Text(m.dosage, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                }
                when {
                    isLoading -> Box(Modifier.fillMaxWidth().padding(Spacing.L), contentAlignment = Alignment.Center) {
                        ScanEatLoadingIndicator(color = AccentCoral)
                    }
                    dbEntry != null -> {
                        // Real BDPM fields, not an inference - form/route/composition are
                        // quoted as-is from the official database record this matched.
                        Text(stringResource(R.string.medication_detail_form, dbEntry.form, dbEntry.route),
                            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                        if (dbEntry.activeSubstances.isNotEmpty()) {
                            Text(
                                stringResource(R.string.medication_detail_composition, dbEntry.activeSubstances.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f),
                            )
                        }
                        hints?.let { FactsCautionsColumn(it.facts, it.cautions) }
                    }
                    else -> Text(
                        stringResource(R.string.medication_detail_no_data),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = OnBackground.copy(0.6f)) } },
    )
}
