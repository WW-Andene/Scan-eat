package fr.scanneat.presentation.pantry.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.pantry.PantryUnit
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.expenses.components.displayLabel
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun AddPantryItemDialog(
    initialName: String = "",
    initialQuantity: Double = 1.0,
    initialUnit: PantryUnit = PantryUnit.UNITS,
    initialExpiryDate: LocalDate? = null,
    initialCategory: ProductCategory = ProductCategory.OTHER,
    lockName: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: Double, unit: PantryUnit, expiryDate: LocalDate?, category: ProductCategory) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var quantityText by rememberSaveable { mutableStateOf(formatQtyForEdit(initialQuantity)) }
    var unit by rememberSaveable { mutableStateOf(initialUnit) }
    var expiryDate by rememberSaveable { mutableStateOf(initialExpiryDate) }
    var category by rememberSaveable(stateSaver = enumSaver<ProductCategory>()) { mutableStateOf(initialCategory) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val quantity = quantityText.replace(',', '.').toDoubleOrNull()
    val isValid = name.isNotBlank() && quantity != null && quantity > 0

    if (showDatePicker) {
        PantryDatePickerDialog(
            initialDate = expiryDate ?: LocalDate.now(),
            onDateSelected = { expiryDate = it; showDatePicker = false },
            onDismiss = { showDatePicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(if (lockName) R.string.pantry_edit_dialog_title else R.string.pantry_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                if (!lockName) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text(stringResource(R.string.pantry_field_name)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        colors = scanEatTextFieldColors(),
                    )
                } else {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = OnBackground)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(
                        value = quantityText, onValueChange = { quantityText = it },
                        label = { Text(stringResource(R.string.pantry_field_quantity)) }, singleLine = true,
                        isError = quantityText.isNotBlank() && (quantity == null || quantity <= 0),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        colors = scanEatTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.pantry_field_unit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            PantryUnit.entries.forEach { u ->
                                val selected = unit == u
                                FilterChip(
                                    selected = selected,
                                    onClick = { unit = u },
                                    label = { Text(u.key, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.pantry_field_category), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                    TextButton(onClick = { showCategoryPicker = true }) { Text(category.displayLabel(), color = AccentCoral) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        expiryDate?.let { it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } ?: stringResource(R.string.pantry_field_no_expiry),
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f),
                    )
                    Row {
                        if (expiryDate != null) {
                            TextButton(onClick = { expiryDate = null }) { Text(stringResource(R.string.pantry_clear_expiry), color = OnBackground.copy(0.6f)) }
                        }
                        TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.pantry_set_expiry), color = AccentCoral) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { quantity?.let { onAdd(name.trim(), it, unit, expiryDate, category) } },
                enabled = isValid,
            ) { Text(stringResource(if (lockName) R.string.common_save else R.string.common_add), color = if (isValid) AccentCoral else OnBackground.copy(0.3f)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )

    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
            modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
            shape = RoundedCornerShape(CardRadius.PROMINENT),
            title = { Text(stringResource(R.string.pantry_field_category), color = OnBackground) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
                    items(ProductCategory.entries, key = { it.key }) { c ->
                        Text(
                            c.displayLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (c == category) AccentCoral else OnBackground,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { category = c; showCategoryPicker = false }
                                .padding(vertical = Spacing.S),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCategoryPicker = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }
}

private fun formatQtyForEdit(q: Double): String = if (q == q.toLong().toDouble()) q.toLong().toString() else "%.1f".format(q)

@Composable
private fun PantryDatePickerDialog(initialDate: LocalDate, onDateSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
