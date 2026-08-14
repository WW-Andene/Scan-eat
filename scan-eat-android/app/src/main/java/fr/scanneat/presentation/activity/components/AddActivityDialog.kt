package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.ACTIVITY_SUB_TYPES
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.domain.engine.health.ActivityRelevantDrugClass
import fr.scanneat.domain.engine.health.checkDailyOvertraining
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
    // User-requested: outdoor sun exposure is a real (if rough) vitamin D
    // source - DashboardAggregator credits a flat per-day estimate when at
    // least one of the day's activities has this set.
    val wasOutdoors: Boolean = false,
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
    val onWasOutdoorsChange: (Boolean) -> Unit = {},
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
    // User-requested: is the app "aware" of an unusually large single-day
    // training volume (e.g. 6h of running) and does it warn about it? Answer:
    // it wasn't - see checkDailyOvertraining's own doc comment. This is the
    // selected type's own minutes already logged today (excluding the entry
    // being edited, if any) - the dialog adds the in-progress minutes field to
    // it live, so the warning updates as the user types, before they even save.
    todayMinutesForType: Int = 0,
    ageYears: Int? = null,
    healthConditions: Set<String> = emptySet(),
    drugClasses: Set<ActivityRelevantDrugClass> = emptySet(),
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
    val onWasOutdoorsChange = actions.onWasOutdoorsChange
    val onMinutesTextChange = actions.onMinutesTextChange
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
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
                                    // User-reported: same stray-Teal-instead-of-brand-color bug
                                    // found in ProfileSelectors.kt's ConditionsSelector - this
                                    // dialog's own comment above already says Activity's identity
                                    // is Warm (matching the type picker), so these two chip groups
                                    // using Teal instead was the inconsistency, not a deliberate choice.
                                    selectedContainerColor = Warm.copy(0.2f), selectedLabelColor = Warm,
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
                                    // User-reported: same stray-Teal-instead-of-brand-color bug
                                    // found in ProfileSelectors.kt's ConditionsSelector - this
                                    // dialog's own comment above already says Activity's identity
                                    // is Warm (matching the type picker), so these two chip groups
                                    // using Teal instead was the inconsistency, not a deliberate choice.
                                    selectedContainerColor = Warm.copy(0.2f), selectedLabelColor = Warm,
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
                    // §A5-audit finding: neither this field nor distanceText below gave
                    // any feedback for an unparseable value - ActivityScreen.onAdd's own
                    // distanceKm/weightUsedKg computation (toDoubleOrNull()?.coerceIn(...))
                    // silently drops it to null and logs the activity anyway, so a typo
                    // here just quietly discarded the data with nothing telling the user -
                    // same "silent data loss" class as EditPortionDialog's identical gap,
                    // and this dialog's own minutesText field two rows below already shows
                    // exactly this isError/supportingText pattern.
                    OutlinedTextField(
                        value = weightUsedText, onValueChange = onWeightUsedTextChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.activity_weight_used_label)) }, singleLine = true,
                        isError = weightUsedText.isNotBlank() && weightUsedText.replace(',', '.').toDoubleOrNull() == null,
                        supportingText = {
                            if (weightUsedText.isNotBlank() && weightUsedText.replace(',', '.').toDoubleOrNull() == null) {
                                Text(stringResource(R.string.activity_number_invalid), color = semanticRed())
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanEatTextFieldColors(),
                    )
                }
                if (selectedType == ActivityType.RUNNING || selectedType == ActivityType.CYCLING || selectedType == ActivityType.SWIMMING) {
                    OutlinedTextField(
                        value = distanceText, onValueChange = onDistanceTextChange, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.activity_distance_label)) }, singleLine = true,
                        isError = distanceText.isNotBlank() && distanceText.replace(',', '.').toDoubleOrNull() == null,
                        supportingText = {
                            if (distanceText.isNotBlank() && distanceText.replace(',', '.').toDoubleOrNull() == null) {
                                Text(stringResource(R.string.activity_number_invalid), color = semanticRed())
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanEatTextFieldColors(),
                    )
                }
                val minutes = minutesText.toIntOrNull()
                val overtraining = minutes?.let {
                    checkDailyOvertraining(selectedType, todayMinutesForType + it, ageYears, healthConditions, drugClasses)
                }
                val minutesValid = minutes != null && minutes in 1..1440
                OutlinedTextField(
                    value = minutesText, onValueChange = onMinutesTextChange,
                    label = { Text(stringResource(R.string.activity_duration_label)) }, singleLine = true,
                    isError = minutesText.isNotBlank() && !minutesValid,
                    supportingText = {
                        if (minutesText.isNotBlank() && !minutesValid) {
                            Text(stringResource(R.string.activity_duration_invalid), color = semanticRed())
                        } else if (overtraining != null) {
                            // Non-blocking (unlike the isError case above) - unusual volume
                            // is worth flagging, not preventing, same as every other
                            // caution in this app (Result's cautions/vetoes never block a
                            // save either).
                            Text(overtrainingMessage(overtraining), color = semanticAmber())
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = scanEatTextFieldColors(),
                )
                // User-requested: a small "was it outdoors" checkbox so the
                // dashboard can credit a rough vitamin D estimate for sun
                // exposure (see DashboardAggregator.VITD_OUTDOOR_UG).
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onWasOutdoorsChange(!values.wasOutdoors) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = values.wasOutdoors, onCheckedChange = onWasOutdoorsChange,
                        colors = CheckboxDefaults.colors(checkedColor = Warm))
                    Text(stringResource(R.string.activity_was_outdoors), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                }
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
