package fr.scanneat.presentation.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }

    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)

            if (!permissionGranted) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(stringResource(R.string.reminders_enable_notifications), color = androidx.compose.ui.graphics.Color.Black) }
            }

            ReminderRow(
                label = stringResource(R.string.reminders_breakfast), on = s.breakfastOn, time = s.breakfastTime,
                onToggle = { viewModel.setBreakfast(it, s.breakfastTime) },
                onTimeChange = { viewModel.setBreakfast(s.breakfastOn, it) },
                onTest = { NotificationHelper.show(context, 901, context.getString(R.string.reminders_breakfast), context.getString(R.string.reminders_test_body)) },
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

            HorizontalDivider(color = OnBackground.copy(0.06f))

            // Hydration
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_hydration), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { NotificationHelper.show(context, 904, context.getString(R.string.reminders_hydration), context.getString(R.string.reminders_test_body)) }) {
                        Icon(Icons.Default.Notifications, null, tint = HydrationBlue)
                    }
                    Switch(checked = s.hydrationOn, onCheckedChange = { viewModel.setHydration(it, s.hydrationIntervalHours) },
                        colors = SwitchDefaults.colors(checkedTrackColor = HydrationBlue))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { h ->
                    FilterChip(
                        selected = s.hydrationIntervalHours == h,
                        onClick = { viewModel.setHydration(s.hydrationOn, h) },
                        label = { Text(stringResource(R.string.reminders_every_n_hours, h)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HydrationBlue.copy(0.2f), selectedLabelColor = HydrationBlue),
                    )
                }
            }

            HorizontalDivider(color = OnBackground.copy(0.06f))

            // Weight
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reminders_weight), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { NotificationHelper.show(context, 905, context.getString(R.string.reminders_weight), context.getString(R.string.reminders_test_body)) }) {
                        Icon(Icons.Default.Notifications, null, tint = Gold)
                    }
                    Switch(checked = s.weightOn, onCheckedChange = { viewModel.setWeight(it, s.weightThresholdDays) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Gold))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 3, 4, 7, 14).forEach { d ->
                    FilterChip(
                        selected = s.weightThresholdDays == d,
                        onClick = { viewModel.setWeight(s.weightOn, d) },
                        label = { Text(stringResource(R.string.reminders_every_n_days, d)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(
    label: String, on: Boolean, time: String,
    onToggle: (Boolean) -> Unit, onTimeChange: (String) -> Unit, onTest: () -> Unit,
) {
    var timeText by remember(time) { mutableStateOf(time) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackground, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it; onTimeChange(it) },
            modifier = Modifier.width(90.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
        )
        IconButton(onClick = onTest) {
            Icon(Icons.Default.Notifications, null, tint = Gold)
        }
        Switch(checked = on, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = Gold))
    }
}
