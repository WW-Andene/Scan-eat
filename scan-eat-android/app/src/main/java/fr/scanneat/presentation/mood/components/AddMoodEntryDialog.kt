package fr.scanneat.presentation.mood.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * Two 1-5 sliders (mood, stress) plus notes - same slider-in-dialog shape
 * AddSleepEntryDialog's quality rating already uses.
 */
@Composable
internal fun AddMoodEntryDialog(
    onDismiss: () -> Unit,
    onAdd: (mood: Int, stress: Int, notes: String) -> Unit,
) {
    var mood by remember { mutableFloatStateOf(3f) }
    var stress by remember { mutableFloatStateOf(3f) }
    var notes by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mood_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Column {
                    Text(stringResource(R.string.mood_mood_label, mood.toInt()), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                    Slider(
                        value = mood, onValueChange = { mood = it }, valueRange = 1f..5f, steps = 3,
                        colors = SliderDefaults.colors(thumbColor = semanticGreen(), activeTrackColor = semanticGreen()),
                    )
                }
                Column {
                    Text(stringResource(R.string.mood_stress_label, stress.toInt()), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                    Slider(
                        value = stress, onValueChange = { stress = it }, valueRange = 1f..5f, steps = 3,
                        colors = SliderDefaults.colors(thumbColor = semanticAmber(), activeTrackColor = semanticAmber()),
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.mood_notes_label)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(mood.toInt(), stress.toInt(), notes) }) { Text(stringResource(R.string.common_save), color = Violet) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
