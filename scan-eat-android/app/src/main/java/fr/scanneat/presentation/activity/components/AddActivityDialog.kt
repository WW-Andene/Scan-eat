package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.ACTIVITY_SUB_TYPES
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*

/**
 * Snapshot of the dialog's editable fields. Grouped into one immutable value
 * instead of 8 flat parameters — the dialog previously took 26 individual
 * value/callback parameters, one pair per editable field.
 */
internal data class AddActivityFormValues(
    val selectedType: ActivityType,
    val selectedSubType: String?,
    val customSubTypeText: String,
    val setsText: String,
    val repsText: String,
    val distanceText: String,
    val weightUsedText: String,
    val minutesText: String,
)

/** Callback bundle mirroring [AddActivityFormValues], one setter per field. */
internal class AddActivityFormActions(
    val onSelectedTypeChange: (ActivityType) -> Unit,
    val onSelectedSubTypeChange: (String?) -> Unit,
    val onCustomSubTypeTextChange: (String) -> Unit,
    val onClearCustomSubTypeText: () -> Unit,
    val onSetsTextChange: (String) -> Unit,
    val onRepsTextChange: (String) -> Unit,
    val onDistanceTextChange: (String) -> Unit,
    val onWeightUsedTextChange: (String) -> Unit,
    val onMinutesTextChange: (String) -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddActivityDialog(
    sortedTypes: List<ActivityType>,
    typeLabels: Map<ActivityType, String>,
    subTypeLabels: Map<String, String>,
    pastSubTypes: Map<ActivityType, List<String>>,
    values: AddActivityFormValues,
    actions: AddActivityFormActions,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    val (selectedType, selectedSubType, customSubTypeText, setsText, repsText, distanceText, weightUsedText, minutesText) = values
    val onSelectedTypeChange = actions.onSelectedTypeChange
    val onSelectedSubTypeChange = actions.onSelectedSubTypeChange
    val onCustomSubTypeTextChange = actions.onCustomSubTypeTextChange
    val onClearCustomSubTypeText = actions.onClearCustomSubTypeText
    val onSetsTextChange = actions.onSetsTextChange
    val onRepsTextChange = actions.onRepsTextChange
    val onDistanceTextChange = actions.onDistanceTextChange
    val onWeightUsedTextChange = actions.onWeightUsedTextChange
    val onMinutesTextChange = actions.onMinutesTextChange
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.activity_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                // Type picker — Improvement: sorted by most recently used first
                Text(stringResource(R.string.activity_type_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                // art-direction-engine §CARDS: Activity's real identity is Warm (see
                // ActivityStreakRow/ActivityScreen's ambientGloom), and the established
                // precedent (AddMedicationDialog's confirm button uses Teal, matching
                // Medication's own identity, not a universal accent) is that entry
                // dialogs match their own tracker - this was AccentCoral instead.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    sortedTypes.forEach { type ->
                        val label = typeLabels[type] ?: type.name
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { onSelectedTypeChange(type) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Warm.copy(0.2f), selectedLabelColor = Warm,
                                labelColor = OnBackground.copy(0.7f),
                            ),
                        )
                    }
                }
                val availableSubTypes = ACTIVITY_SUB_TYPES[selectedType].orEmpty()
                if (availableSubTypes.isNotEmpty()) {
                    Text(stringResource(R.string.activity_subtype_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        availableSubTypes.forEach { key ->
                            FilterChip(
                                selected = selectedSubType == key,
                                onClick = { onSelectedSubTypeChange(if (selectedSubType == key) null else key); onClearCustomSubTypeText() },
                                label = { Text(subTypeLabels[key] ?: key, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                                    labelColor = OnBackground.copy(0.7f),
                                ),
                            )
                        }
                    }
                }
                // Free-text sub-type — the fixed chip lists above only cover a
                // handful of common exercises per type; there was previously no
                // way to log something like "rowing" or "pilates" at all.
                // Suggestions drawn from the user's own past entries (no new
                // data source), excluding names already offered as fixed chips.
                val pastForType = remember(selectedType, pastSubTypes) {
                    pastSubTypes[selectedType].orEmpty().filter { it !in availableSubTypes }
                }
                if (pastForType.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        pastForType.forEach { suggestion ->
                            FilterChip(
                                selected = selectedSubType == suggestion,
                                onClick = { onSelectedSubTypeChange(suggestion); onCustomSubTypeTextChange(suggestion) },
                                label = { Text(suggestion, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                                    labelColor = OnBackground.copy(0.7f),
                                ),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customSubTypeText,
                    onValueChange = { onCustomSubTypeTextChange(it); onSelectedSubTypeChange(it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.activity_subtype_custom_label)) },
                    singleLine = true,
                    colors = scanEatTextFieldColors(),
                )
                if (selectedType == ActivityType.STRENGTH) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        OutlinedTextField(
                            value = setsText, onValueChange = onSetsTextChange, modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.activity_sets_label)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = scanEatTextFieldColors(),
                        )
                        OutlinedTextField(
                            value = repsText, onValueChange = onRepsTextChange, modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.activity_reps_label)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = scanEatTextFieldColors(),
                        )
                    }
                    OutlinedTextField(
                        value = weightUsedText, onValueChange = onWeightUsedTextChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.activity_weight_used_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanEatTextFieldColors(),
                    )
                }
                if (selectedType == ActivityType.RUNNING || selectedType == ActivityType.CYCLING || selectedType == ActivityType.SWIMMING) {
                    OutlinedTextField(
                        value = distanceText, onValueChange = onDistanceTextChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.activity_distance_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanEatTextFieldColors(),
                    )
                }
                val minutes = minutesText.toIntOrNull()
                val minutesValid = minutes != null && minutes in 1..1440
                OutlinedTextField(
                    value = minutesText, onValueChange = onMinutesTextChange,
                    label = { Text(stringResource(R.string.activity_duration_label)) }, singleLine = true,
                    isError = minutesText.isNotBlank() && !minutesValid,
                    supportingText = {
                        if (minutesText.isNotBlank() && !minutesValid) {
                            Text(stringResource(R.string.activity_duration_invalid), color = semanticRed())
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            val minutesValid = (minutesText.toIntOrNull() ?: 0) in 1..1440
            TextButton(
                onClick = onAdd,
                enabled = minutesValid,
            ) { Text(stringResource(R.string.common_add), color = if (minutesValid) Warm else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
