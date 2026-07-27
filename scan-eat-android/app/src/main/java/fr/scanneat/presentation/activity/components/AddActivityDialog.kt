package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.ACTIVITY_SUB_TYPES
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddActivityDialog(
    sortedTypes: List<ActivityType>,
    typeLabels: Map<ActivityType, String>,
    subTypeLabels: Map<String, String>,
    pastSubTypes: Map<ActivityType, List<String>>,
    selectedType: ActivityType, onSelectedTypeChange: (ActivityType) -> Unit,
    selectedSubType: String?, onSelectedSubTypeChange: (String?) -> Unit,
    customSubTypeText: String, onCustomSubTypeTextChange: (String) -> Unit,
    onClearCustomSubTypeText: () -> Unit,
    setsText: String, onSetsTextChange: (String) -> Unit,
    repsText: String, onRepsTextChange: (String) -> Unit,
    distanceText: String, onDistanceTextChange: (String) -> Unit,
    weightUsedText: String, onWeightUsedTextChange: (String) -> Unit,
    minutesText: String, onMinutesTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.activity_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                // Type picker — Improvement: sorted by most recently used first
                Text(stringResource(R.string.activity_type_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sortedTypes.forEach { type ->
                        val label = typeLabels[type] ?: type.name
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { onSelectedTypeChange(type) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                                labelColor = OnBackground.copy(0.7f),
                            ),
                        )
                    }
                }
                val availableSubTypes = ACTIVITY_SUB_TYPES[selectedType].orEmpty()
                if (availableSubTypes.isNotEmpty()) {
                    Text(stringResource(R.string.activity_subtype_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            ) { Text(stringResource(R.string.common_add), color = if (minutesValid) AccentCoral else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
