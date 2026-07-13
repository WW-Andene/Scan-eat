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
import fr.scanneat.notifications.NotificationHelper
import fr.scanneat.presentation.ui.theme.*

@Composable
fun RemindersCard(viewModel: RemindersViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s = viewModel.settings.collectAsStateWithLifecycle().value

    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    // Once the user permanently denies (checked "don't ask again", or a 2nd
    // straight denial on API 30+), RequestPermission() silently returns false
    // without even showing the system dialog again - same gap ScanScreen's
    // camera-permission flow already handles by tracking this and swapping to
    // an "Open Settings" action once the OS won't show the dialog anymore.
    var requestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } ?: true
            if (requestedOnce && !canShowRationale) permanentlyDenied = true
        }
        requestedOnce = true
    }

    ScanEatCard(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)

            if (!permissionGranted) {
                if (permanentlyDenied) {
                    ScanEatPrimaryButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    ) { Text(stringResource(R.string.scan_open_settings_button)) }
                } else {
                    ScanEatPrimaryButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    ) { Text(stringResource(R.string.reminders_enable_notifications)) }
                }
            }

            ReminderRow(
                label = stringResource(R.string.reminders_breakfast), on = s.breakfastOn, time = s.breakfastTime,
                onToggle = { viewModel.setBreakfast(it, s.breakfastTime) },
                onTimeChange = { viewModel.setBreakfast(s.breakfastOn, it) },
                onTest = { NotificationHelper.show(context, 901, context.getString(R.string.reminders_breakfast), context.getString(R.string.reminders_test_body)) },
            )
            ReminderRow(
                label = stringResource(R.string.reminders_snack), on = s.snackOn, time = s.snackTime,
                onToggle = { viewModel.setSnack(it, s.snackTime) },
                onTimeChange = { viewModel.setSnack(s.snackOn, it) },
                onTest = { NotificationHelper.show(context, 906, context.getString(R.string.reminders_snack), context.getString(R.string.reminders_test_body)) },
            )
            ReminderRow(
                label = stringResource(R.string.reminders_lunch), on = s.lunchOn, time = s.lunchTime,
                onToggle = { viewModel.setLunch(it, s.lunchTime) },
                onTimeChange = { viewModel.setLunch(s.lunchOn, it) },
                onTest = { NotificationHelper.show(context, 902, context.getString(R.string.reminders_lunch), context.getString(R.string.reminders_test_body)) },
            )
            ReminderRow(
                label = stringResource(R.string.reminders_dinner), on = s.dinnerOn, time = s.dinnerTime,
                onToggle = { viewModel.setDinner(it, s.dinnerTime) },
                onTimeChange = { viewModel.setDinner(s.dinnerOn, it) },
                onTest = { NotificationHelper.show(context, 903, context.getString(R.string.reminders_dinner), context.getString(R.string.reminders_test_body)) },
            )

            ScanEatDivider()

            // Hydration
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_hydration), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { NotificationHelper.show(context, 904, context.getString(R.string.reminders_hydration), context.getString(R.string.reminders_test_body)) }) {
                        Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, stringResource(R.string.reminders_hydration)), tint = HydrationBlue)
                    }
                    val hydrationLabel = stringResource(R.string.reminders_hydration)
                    Switch(checked = s.hydrationOn, onCheckedChange = { viewModel.setHydration(it, s.hydrationIntervalHours) },
                        colors = SwitchDefaults.colors(checkedTrackColor = HydrationBlue),
                        modifier = Modifier.semantics { contentDescription = hydrationLabel })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 6, 9, 12).forEach { h ->
                    FilterChip(
                        selected = s.hydrationIntervalHours == h,
                        onClick = { viewModel.setHydration(s.hydrationOn, h) },
                        label = { Text(stringResource(R.string.reminders_every_n_hours, h)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HydrationBlue.copy(0.2f), selectedLabelColor = HydrationBlue),
                    )
                }
            }
            ReminderRow(
                label = stringResource(R.string.reminders_custom_daily), on = s.hydrationCustomOn, time = s.hydrationCustomTime,
                onToggle = { viewModel.setHydrationCustom(it, s.hydrationCustomTime) },
                onTimeChange = { viewModel.setHydrationCustom(s.hydrationCustomOn, it) },
                onTest = { NotificationHelper.show(context, 907, context.getString(R.string.reminders_hydration), context.getString(R.string.reminders_test_body)) },
            )

            ScanEatDivider()

            // Weight
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_weight), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { NotificationHelper.show(context, 905, context.getString(R.string.reminders_weight), context.getString(R.string.reminders_test_body)) }) {
                        Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, stringResource(R.string.reminders_weight)), tint = Gold)
                    }
                    val weightLabel = stringResource(R.string.reminders_weight)
                    Switch(checked = s.weightOn, onCheckedChange = { viewModel.setWeight(it, s.weightThresholdDays) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Gold),
                        modifier = Modifier.semantics { contentDescription = weightLabel })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    1 to R.string.reminders_weight_preset_day,
                    7 to R.string.reminders_weight_preset_week,
                    30 to R.string.reminders_weight_preset_month,
                    365 to R.string.reminders_weight_preset_year,
                ).forEach { (d, labelRes) ->
                    FilterChip(
                        selected = s.weightThresholdDays == d,
                        onClick = { viewModel.setWeight(s.weightOn, d) },
                        label = { Text(stringResource(labelRes)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                    )
                }
            }
            ReminderRow(
                label = stringResource(R.string.reminders_custom_daily), on = s.weightCustomOn, time = s.weightCustomTime,
                onToggle = { viewModel.setWeightCustom(it, s.weightCustomTime) },
                onTimeChange = { viewModel.setWeightCustom(s.weightCustomOn, it) },
                onTest = { NotificationHelper.show(context, 908, context.getString(R.string.reminders_weight), context.getString(R.string.reminders_test_body)) },
            )
    }
}

@Composable
private fun ReminderRow(
    label: String, on: Boolean, time: String,
    onToggle: (Boolean) -> Unit, onTimeChange: (String) -> Unit, onTest: () -> Unit,
) {
    var timeText by remember(time) { mutableStateOf(time) }
    // Persisting on every keystroke wrote unparseable intermediate strings
    // (e.g. "9:00" mid-type, or backgrounding after "07:3") straight to
    // DataStore - ReminderWorker.kt's LocalTime.parse(timeStr) then silently
    // fails and the reminder never fires again with zero UI feedback. Only
    // commit values LocalTime.parse actually accepts; isError flags the rest.
    val isValid = remember(timeText) { runCatching { java.time.LocalTime.parse(timeText) }.isSuccess }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackground, modifier = Modifier.weight(1f))
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
            Icon(Icons.Default.Notifications, stringResource(R.string.reminders_cd_test, label), tint = Gold)
        }
        Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Gold),
            modifier = Modifier.semantics { contentDescription = label })
    }
}
