package fr.scanneat.presentation.reminders

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.reminders.CustomReminder
import fr.scanneat.notifications.NotificationHelper
import fr.scanneat.presentation.ui.theme.*

@Composable
private fun permissionState(): Triple<Boolean, Boolean, () -> Unit> {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: true
            if (requestedOnce && !canShowRationale) permanentlyDenied = true
        }
        requestedOnce = true
    }
    return Triple(permissionGranted, permanentlyDenied) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
private fun PermissionBanner(permissionGranted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    if (!permissionGranted) {
        if (permanentlyDenied) {
            ScanEatPrimaryButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) {
                Text(stringResource(R.string.scan_open_settings_button))
            }
        } else {
            ScanEatPrimaryButton(onClick = onRequest) { Text(stringResource(R.string.reminders_enable_notifications)) }
        }
    }
}

@Composable
fun MealRemindersCard(viewModel: RemindersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = viewModel.settings.collectAsStateWithLifecycle().value
    val (permGranted, permDenied, onRequest) = permissionState()
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
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
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.reminders_add_custom), style = MaterialTheme.typography.labelMedium)
        }
        if (showAddDialog) {
            AddCustomReminderDialog(onConfirm = { label, time -> viewModel.addCustomReminder(label, time); showAddDialog = false }, onDismiss = { showAddDialog = false })
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
                    Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, stringResource(R.string.reminders_hydration)), tint = HydrationBlue)
                }
                val lbl = stringResource(R.string.reminders_hydration)
                Switch(checked = s.hydrationOn, onCheckedChange = { viewModel.setHydration(it, s.hydrationIntervalHours) },
                    colors = SwitchDefaults.colors(checkedTrackColor = HydrationBlue), modifier = Modifier.semantics { contentDescription = lbl })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 6, 9, 12).forEach { h ->
                FilterChip(selected = s.hydrationIntervalHours == h, onClick = { viewModel.setHydration(s.hydrationOn, h) },
                    label = { Text(stringResource(R.string.reminders_every_n_hours, h)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HydrationBlue.copy(0.2f), selectedLabelColor = HydrationBlue))
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

@Composable
private fun ReminderRow(
    defaultLabel: String,
    customLabel: String = "",
    on: Boolean, time: String,
    onToggle: (Boolean) -> Unit, onTimeChange: (String) -> Unit,
    onLabelChange: ((String) -> Unit)? = null,
    onTest: () -> Unit,
) {
    var timeText  by remember(time) { mutableStateOf(time) }
    var labelText by remember(customLabel) { mutableStateOf(customLabel) }
    val isValid = remember(timeText) { runCatching { java.time.LocalTime.parse(timeText) }.isSuccess }
    val displayLabel = labelText.ifBlank { defaultLabel }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (onLabelChange != null) {
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it; onLabelChange(it) },
                    placeholder = { Text(defaultLabel, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f)) },
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )
            } else {
                Text(displayLabel, style = MaterialTheme.typography.bodyMedium, color = OnBackground, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(
                value = timeText,
                onValueChange = { timeText = it; if (runCatching { java.time.LocalTime.parse(it) }.isSuccess) onTimeChange(it) },
                modifier = Modifier.width(90.dp),
                singleLine = true,
                isError = !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
            )
            IconButton(onClick = onTest) {
                Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, displayLabel), tint = Gold)
            }
            Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                modifier = Modifier.semantics { contentDescription = displayLabel })
        }
    }
}

@Composable
private fun CustomReminderRow(
    reminder: CustomReminder,
    onUpdate: (CustomReminder) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    var labelText by remember(reminder.label) { mutableStateOf(reminder.label) }
    var timeText  by remember(reminder.time)  { mutableStateOf(reminder.time) }
    val isValid = remember(timeText) { runCatching { java.time.LocalTime.parse(timeText) }.isSuccess }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it; onUpdate(reminder.copy(label = it)) },
            modifier = Modifier.weight(1f).padding(end = 6.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.15f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it; if (runCatching { java.time.LocalTime.parse(it) }.isSuccess) onUpdate(reminder.copy(time = it)) },
            modifier = Modifier.width(90.dp),
            singleLine = true,
            isError = !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCoral, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
        )
        IconButton(onClick = onTest) { Icon(Icons.Default.Notifications, null, tint = AccentCoral) }
        Switch(checked = reminder.on, onCheckedChange = { onUpdate(reminder.copy(on = it)) }, colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
            modifier = Modifier.semantics { contentDescription = labelText })
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(16.dp)) }
    }
}

@Composable
private fun AddCustomReminderDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var time  by remember { mutableStateOf("09:00") }
    val timeValid = remember(time) { runCatching { java.time.LocalTime.parse(time) }.isSuccess }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.reminders_add_custom), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it }, singleLine = true,
                    label = { Text(stringResource(R.string.reminders_custom_label_hint)) },
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it }, singleLine = true,
                    isError = !timeValid,
                    label = { Text(stringResource(R.string.reminders_custom_time_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label, time) }, enabled = label.isNotBlank() && timeValid) {
                Text(stringResource(R.string.common_add), color = if (label.isNotBlank() && timeValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
