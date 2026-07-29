package fr.scanneat.presentation.weight.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                OutlinedTextField(
                    value = kgText, onValueChange = onKgTextChange,
                    label = { Text(if (useImperial) stringResource(R.string.weight_field_lb) else stringResource(R.string.weight_field_kg)) }, singleLine = true,
                    isError = kgText.isNotBlank() && !isValidWeight,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                OutlinedTextField(
                    value = notesText, onValueChange = onNotesTextChange,
                    label = { Text(stringResource(R.string.weight_field_notes)) }, singleLine = true,
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).clickable(onClick = onPickDate),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, OnBackground.copy(0.2f)),
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
                onClick = {
                    val kg = if (useImperial) kgValue!! / KG_TO_LB else kgValue!!
                    onSave(kg)
                },
                enabled = isValidWeight,
            ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant,
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
