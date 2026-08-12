package fr.scanneat.presentation.sleep.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Bedtime/wake as HH:mm text fields - same "custom time window" shape
 * FastingScreen's StartFastForm already uses for the exact same overnight-
 * span problem (a fast/night can cross midnight, so two same-day LocalTimes
 * aren't enough on their own). Bedtime is assumed to be the evening BEFORE
 * today (yesterday 23:00 -> today 07:00 is the common case); wake is today.
 */
@Composable
internal fun AddSleepEntryDialog(
    currentGoalHours: Double,
    onGoalChange: (Double) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (bedtimeMs: Long, wakeMs: Long, quality: Int, notes: String) -> Unit,
) {
    var bedtimeStr by remember { mutableStateOf("23:00") }
    var wakeStr by remember { mutableStateOf("07:00") }
    var quality by remember { mutableFloatStateOf(3f) }
    var notes by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf(currentGoalHours.toString()) }

    val zone = ZoneId.systemDefault()
    val computed = remember(bedtimeStr, wakeStr) {
        runCatching {
            val bedtime = LocalTime.parse(bedtimeStr)
            val wake = LocalTime.parse(wakeStr)
            val today = LocalDate.now()
            val bedtimeDateTime = LocalDateTime.of(today.minusDays(1), bedtime)
            var wakeDateTime = LocalDateTime.of(today, wake)
            // Same overnight-span correction as StartFastForm's customHours -
            // if wake time is earlier in the day than bedtime and we already
            // anchored bedtime to yesterday, wake belongs to today; but if the
            // user typed a same-day nap-like pair (bedtime AFTER wake time),
            // nudge wake forward a day so duration stays positive.
            if (wakeDateTime.isBefore(bedtimeDateTime)) wakeDateTime = wakeDateTime.plusDays(1)
            val bedtimeMs = bedtimeDateTime.atZone(zone).toInstant().toEpochMilli()
            val wakeMs = wakeDateTime.atZone(zone).toInstant().toEpochMilli()
            val hours = (wakeMs - bedtimeMs) / 3_600_000.0
            Triple(bedtimeMs, wakeMs, hours)
        }.getOrNull()
    }
    val showError = bedtimeStr.isNotBlank() && wakeStr.isNotBlank() && computed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        title = { Text(stringResource(R.string.sleep_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(
                        value = bedtimeStr, onValueChange = { bedtimeStr = it },
                        label = { Text(stringResource(R.string.sleep_bedtime_label)) },
                        singleLine = true, modifier = Modifier.width(110.dp),
                        isError = showError, colors = scanEatTextFieldColors(),
                    )
                    Text("→", style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
                    OutlinedTextField(
                        value = wakeStr, onValueChange = { wakeStr = it },
                        label = { Text(stringResource(R.string.sleep_wake_label)) },
                        singleLine = true, modifier = Modifier.width(110.dp),
                        isError = showError, colors = scanEatTextFieldColors(),
                    )
                }
                if (computed != null) {
                    Text(stringResource(R.string.sleep_hours_value, computed.third), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                } else if (showError) {
                    Text(stringResource(R.string.fasting_custom_time_error), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                }
                Column {
                    Text(stringResource(R.string.sleep_quality_label, quality.toInt()), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                    Slider(
                        value = quality, onValueChange = { quality = it }, valueRange = 1f..5f, steps = 3,
                        colors = SliderDefaults.colors(thumbColor = Violet, activeTrackColor = Violet),
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.sleep_notes_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = goalText,
                    // A sleep goal is a number of hours within a single day - unbounded
                    // parsing previously let a typo (e.g. "80" instead of "8") set an
                    // impossible goal that SleepMoodCorrelation/DashboardGapAnalysis would
                    // then treat as a real target with no correcting UI feedback.
                    onValueChange = { goalText = it; it.toDoubleOrNull()?.coerceIn(1.0, 24.0)?.let(onGoalChange) },
                    label = { Text(stringResource(R.string.sleep_goal_field_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = computed != null,
                onClick = { computed?.let { (bedtimeMs, wakeMs, _) -> onAdd(bedtimeMs, wakeMs, quality.toInt(), notes) } },
            ) { Text(stringResource(R.string.common_save), color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
