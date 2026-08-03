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
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.presentation.reminders.components.PermissionBanner
import fr.scanneat.presentation.reminders.components.permissionState
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
) {
    val m = medication
    val haptics = LocalHapticFeedback.current
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M), onClick = onEdit) {
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
                Icon(TablerIcons.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.4f))
            }
            var menuExpanded by remember { mutableStateOf(false) }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(TablerIcons.DotsVertical, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }, shape = RoundedCornerShape(CardRadius.CONTROL), containerColor = SurfaceVariant) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    leadingIcon = { Icon(TablerIcons.X, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete() },
                )
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
        containerColor = SurfaceVariant,
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
internal fun MedicationReminderDialog(medication: Medication, onDismiss: () -> Unit, onSave: (on: Boolean, time: String) -> Unit) {
    val m = medication
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
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
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
            }
        },
        confirmButton = {
            // Time correctness is irrelevant when the reminder is being turned off -
            // was gating Save on isValidTime unconditionally, so a stale/blank time
            // field blocked a user from saving even when their only intent was to
            // disable the reminder.
            val canSave = !on || isValidTime
            TextButton(
                onClick = { onSave(on, time) },
                enabled = canSave,
            ) { Text(stringResource(R.string.common_save), color = if (canSave) Teal else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
