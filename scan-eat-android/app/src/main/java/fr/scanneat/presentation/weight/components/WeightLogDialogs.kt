package fr.scanneat.presentation.weight.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun AddWeightDialog(
    kgText: String,
    onKgTextChange: (String) -> Unit,
    notesText: String,
    onNotesTextChange: (String) -> Unit,
    useImperial: Boolean,
    entryDate: LocalDate,
    fmt: DateTimeFormatter,
    onPickDate: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    // A negative/zero/absurd weight fed straight into WeightSummary/WeightForecast/
    // BMI trend calcs with no guard at all - bound it to a sane human range in
    // whichever unit is displayed, matching ActivityScreen's validated-numeric pattern.
    val kgValue = kgText.replace(',', '.').toDoubleOrNull()
    val isValidWeight = kgValue != null && (if (useImperial) kgValue in 44.0..880.0 else kgValue in 20.0..400.0)
    // UX friction pass: weight logging is a daily action for anyone tracking
    // it, yet neither field had an imeAction - the keyboard's Next/Done keys
    // did nothing, so the user always had to manually tap into the notes
    // field and then reach down to tap "Enregistrer" by hand.
    val focusManager = LocalFocusManager.current
    // Same unit conversion the confirmButton below already applies - shared
    // here so the notes field's onDone (added by this UX friction pass)
    // can't drift from it.
    fun confirm() { if (isValidWeight) onSave(if (useImperial) kgValue!! / KG_TO_LB else kgValue!!) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                OutlinedTextField(
                    value = kgText, onValueChange = onKgTextChange,
                    label = { Text(if (useImperial) stringResource(R.string.weight_field_lb) else stringResource(R.string.weight_field_kg)) }, singleLine = true,
                    isError = kgText.isNotBlank() && !isValidWeight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = notesText, onValueChange = onNotesTextChange,
                    label = { Text(stringResource(R.string.weight_field_notes)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).clickable(onClick = onPickDate),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    color = Color.Transparent,
                    border = BorderStroke(2.dp, OnBackground.copy(0.2f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.M),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(stringResource(R.string.weight_field_date), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                            Text(entryDate.format(fmt), style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                        }
                        Icon(Icons.Rounded.DateRange, null, tint = OnBackground.copy(0.6f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirm() },
                enabled = isValidWeight,
            ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}

@Composable
internal fun WeightDatePickerDialog(entryDate: LocalDate, onDateSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = entryDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    ) {
        // art-direction-engine §DCO5: the confirm button was already AccentCoral,
        // but the calendar itself (the visually dominant part of this dialog) had
        // no colors param, so it rendered with Material's default Gold selection
        // color - two competing accents inside the same dialog.
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = AccentCoral,
                todayDateBorderColor      = AccentCoral,
                todayContentColor         = AccentCoral,
            ),
        )
    }
}

/**
 * User-requested: "develop the tool" for Weight - goalWeightKg was only ever
 * settable from Profile > body section, even though it's read and displayed
 * prominently on this exact screen (WeightSummaryCard's goal row, the
 * forecast). Lets it be set/changed/cleared directly from here - see
 * WeightViewModel.setGoalWeightKg's own doc comment. Same shape as
 * HydrationGoalEditorDialog (single field + a clear/reset action when a
 * value is already set), displayed/edited in whichever unit the rest of
 * this screen already shows (kg/lb), converted to kg only on confirm.
 */
@Composable
internal fun WeightGoalEditorDialog(
    initialGoalKg: Double?,
    useImperial: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    var text by rememberSaveable {
        mutableStateOf(initialGoalKg?.let { if (useImperial) (it * KG_TO_LB).formatDecimal(1) else it.formatDecimal(1) } ?: "")
    }
    // Comma must survive the filter (same fix AddWeightDialog's own field
    // needed) - replace happens on parse below, not while typing.
    val nativeValue = text.replace(',', '.').toDoubleOrNull()
    val kgValue = nativeValue?.let { if (useImperial) it / KG_TO_LB else it }
    val isValid = kgValue != null && (if (useImperial) nativeValue in 44.0..880.0 else nativeValue in 20.0..400.0)
    fun confirm() { if (isValid) onConfirm(kgValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.weight_goal_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(if (useImperial) stringResource(R.string.weight_field_lb) else stringResource(R.string.weight_field_kg)) },
                    isError = text.isNotBlank() && !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                if (initialGoalKg != null) {
                    TextButton(onClick = { onConfirm(null) }) {
                        Text(stringResource(R.string.weight_goal_clear), color = AccentCoral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = isValid) {
                Text(stringResource(R.string.common_save), color = if (isValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
