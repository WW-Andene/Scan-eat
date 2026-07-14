package fr.scanneat.presentation.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.reminders.ReminderSettings
import fr.scanneat.notifications.NotificationHelper
import fr.scanneat.presentation.reminders.components.AddCustomReminderDialog
import fr.scanneat.presentation.reminders.components.CustomReminderRow
import fr.scanneat.presentation.reminders.components.PermissionBanner
import fr.scanneat.presentation.reminders.components.ReminderRow
import fr.scanneat.presentation.reminders.components.permissionState
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalTime

// Default meal labels and the connector word previously hardcoded French
// regardless of the app's language setting — now sourced from the same
// string resources ReminderRow already uses for these fields.
@Composable
private fun nextReminderLabel(s: ReminderSettings): String? {
    val now = LocalTime.now()
    val breakfastLabel = s.breakfastLabel.ifBlank { stringResource(R.string.reminders_breakfast) }
    val snackLabel = s.snackLabel.ifBlank { stringResource(R.string.reminders_snack) }
    val lunchLabel = s.lunchLabel.ifBlank { stringResource(R.string.reminders_lunch) }
    val dinnerLabel = s.dinnerLabel.ifBlank { stringResource(R.string.reminders_dinner) }
    val digestLabel = stringResource(R.string.notif_summary_title)
    val upcoming = mutableListOf<Pair<LocalTime, String>>()
    if (s.breakfastOn) runCatching { LocalTime.parse(s.breakfastTime) }.getOrNull()?.let { if (it.isAfter(now)) upcoming += it to breakfastLabel }
    if (s.snackOn)     runCatching { LocalTime.parse(s.snackTime) }.getOrNull()?.let { if (it.isAfter(now)) upcoming += it to snackLabel }
    if (s.lunchOn)     runCatching { LocalTime.parse(s.lunchTime) }.getOrNull()?.let { if (it.isAfter(now)) upcoming += it to lunchLabel }
    if (s.dinnerOn)    runCatching { LocalTime.parse(s.dinnerTime) }.getOrNull()?.let { if (it.isAfter(now)) upcoming += it to dinnerLabel }
    s.customReminders.filter { it.on }.forEach { cr -> runCatching { LocalTime.parse(cr.time) }.getOrNull()?.let { if (it.isAfter(now)) upcoming += it to cr.label } }
    if (s.dailyDigestOn && LocalTime.of(21, 0).isAfter(now)) upcoming += LocalTime.of(21, 0) to digestLabel
    val isFrench = Locale.current.language == "fr"
    return upcoming.minByOrNull { it.first }?.let { (t, label) ->
        val timeStr = "%02d:%02d".format(t.hour, t.minute).let { if (isFrench) it.replace(':', 'h') else it }
        if (isFrench) "$label à $timeStr" else "$label at $timeStr"
    }
}

@Composable
fun MealRemindersCard(viewModel: RemindersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = viewModel.settings.collectAsStateWithLifecycle().value
    val (permGranted, permDenied, onRequest) = permissionState()
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Improvement: show next upcoming reminder time
        val nextLabel = nextReminderLabel(s)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            if (nextLabel != null) {
                Surface(shape = RoundedCornerShape(50), color = AccentCoral.copy(0.12f)) {
                    Row(modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, null, tint = AccentCoral, modifier = Modifier.size(12.dp))
                        Text(nextLabel, style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                    }
                }
            }
        }
        PermissionBanner(permGranted, permDenied, onRequest)
        ReminderRow(defaultLabel = stringResource(R.string.reminders_breakfast), customLabel = s.breakfastLabel, on = s.breakfastOn, time = s.breakfastTime,
            onToggle = { viewModel.setBreakfast(it, s.breakfastTime) }, onTimeChange = { viewModel.setBreakfast(s.breakfastOn, it) }, onLabelChange = { viewModel.setBreakfastLabel(it) },
            onTest = { NotificationHelper.show(context, 901, context.getString(R.string.reminders_breakfast), context.getString(R.string.reminders_test_body)) })
        ReminderRow(defaultLabel = stringResource(R.string.reminders_snack), customLabel = s.snackLabel, on = s.snackOn, time = s.snackTime,
            onToggle = { viewModel.setSnack(it, s.snackTime) }, onTimeChange = { viewModel.setSnack(s.snackOn, it) }, onLabelChange = { viewModel.setSnackLabel(it) },
            onTest = { NotificationHelper.show(context, 906, context.getString(R.string.reminders_snack), context.getString(R.string.reminders_test_body)) })
        ReminderRow(defaultLabel = stringResource(R.string.reminders_lunch), customLabel = s.lunchLabel, on = s.lunchOn, time = s.lunchTime,
            onToggle = { viewModel.setLunch(it, s.lunchTime) }, onTimeChange = { viewModel.setLunch(s.lunchOn, it) }, onLabelChange = { viewModel.setLunchLabel(it) },
            onTest = { NotificationHelper.show(context, 902, context.getString(R.string.reminders_lunch), context.getString(R.string.reminders_test_body)) })
        ReminderRow(defaultLabel = stringResource(R.string.reminders_dinner), customLabel = s.dinnerLabel, on = s.dinnerOn, time = s.dinnerTime,
            onToggle = { viewModel.setDinner(it, s.dinnerTime) }, onTimeChange = { viewModel.setDinner(s.dinnerOn, it) }, onLabelChange = { viewModel.setDinnerLabel(it) },
            onTest = { NotificationHelper.show(context, 903, context.getString(R.string.reminders_dinner), context.getString(R.string.reminders_test_body)) })
        s.customReminders.forEach { cr ->
            CustomReminderRow(reminder = cr, onUpdate = { viewModel.updateCustomReminder(it) }, onDelete = { viewModel.deleteCustomReminder(cr.id) },
                onTest = { NotificationHelper.show(context, cr.id, cr.label, cr.label) })
        }
        var showAddDialog by remember { mutableStateOf(false) }
        TextButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.XS))
            Text(stringResource(R.string.reminders_add_custom), style = MaterialTheme.typography.labelMedium)
        }
        if (showAddDialog) {
            AddCustomReminderDialog(onConfirm = { label, time -> viewModel.addCustomReminder(label, time); showAddDialog = false }, onDismiss = { showAddDialog = false })
        }

        // New: daily digest toggle — a 21:00 notification summarising the day's progress
        HorizontalDivider(color = OnBackground.copy(0.08f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminders_daily_digest_title), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Text(stringResource(R.string.reminders_daily_digest_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            Switch(
                checked = s.dailyDigestOn,
                onCheckedChange = { viewModel.setDailyDigest(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            )
        }
    }
}

@Composable
fun HydrationReminderCard(viewModel: RemindersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = viewModel.settings.collectAsStateWithLifecycle().value
    val (permGranted, permDenied, onRequest) = permissionState()
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        PermissionBanner(permGranted, permDenied, onRequest)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders_hydration), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { NotificationHelper.show(context, 904, context.getString(R.string.reminders_hydration), context.getString(R.string.reminders_test_body)) }) {
                    Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, stringResource(R.string.reminders_hydration)), tint = semanticBlue())
                }
                val lbl = stringResource(R.string.reminders_hydration)
                Switch(checked = s.hydrationOn, onCheckedChange = { viewModel.setHydration(it, s.hydrationIntervalHours) },
                    colors = SwitchDefaults.colors(checkedTrackColor = semanticBlue()), modifier = Modifier.semantics { contentDescription = lbl })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 6, 9, 12).forEach { h ->
                FilterChip(selected = s.hydrationIntervalHours == h, onClick = { viewModel.setHydration(s.hydrationOn, h) },
                    label = { Text(stringResource(R.string.reminders_every_n_hours, h)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = semanticBlue().copy(0.2f), selectedLabelColor = semanticBlue()))
            }
        }
        ReminderRow(defaultLabel = stringResource(R.string.reminders_custom_daily), on = s.hydrationCustomOn, time = s.hydrationCustomTime,
            onToggle = { viewModel.setHydrationCustom(it, s.hydrationCustomTime) }, onTimeChange = { viewModel.setHydrationCustom(s.hydrationCustomOn, it) },
            onTest = { NotificationHelper.show(context, 907, context.getString(R.string.reminders_hydration), context.getString(R.string.reminders_test_body)) })
    }
}

@Composable
fun WeightReminderCard(viewModel: RemindersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = viewModel.settings.collectAsStateWithLifecycle().value
    val (permGranted, permDenied, onRequest) = permissionState()
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        PermissionBanner(permGranted, permDenied, onRequest)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders_weight), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { NotificationHelper.show(context, 905, context.getString(R.string.reminders_weight), context.getString(R.string.reminders_test_body)) }) {
                    Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, stringResource(R.string.reminders_weight)), tint = Gold)
                }
                val lbl = stringResource(R.string.reminders_weight)
                Switch(checked = s.weightOn, onCheckedChange = { viewModel.setWeight(it, s.weightThresholdDays) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Gold), modifier = Modifier.semantics { contentDescription = lbl })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to R.string.reminders_weight_preset_day, 7 to R.string.reminders_weight_preset_week,
                30 to R.string.reminders_weight_preset_month, 365 to R.string.reminders_weight_preset_year).forEach { (d, res) ->
                FilterChip(selected = s.weightThresholdDays == d, onClick = { viewModel.setWeight(s.weightOn, d) },
                    label = { Text(stringResource(res)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold))
            }
        }
        ReminderRow(defaultLabel = stringResource(R.string.reminders_custom_daily), on = s.weightCustomOn, time = s.weightCustomTime,
            onToggle = { viewModel.setWeightCustom(it, s.weightCustomTime) }, onTimeChange = { viewModel.setWeightCustom(s.weightCustomOn, it) },
            onTest = { NotificationHelper.show(context, 908, context.getString(R.string.reminders_weight), context.getString(R.string.reminders_test_body)) })
    }
}

@Composable
fun RemindersCard(viewModel: RemindersViewModel = hiltViewModel()) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MealRemindersCard(viewModel)
        HydrationReminderCard(viewModel)
        WeightReminderCard(viewModel)
    }
}
