package fr.scanneat.presentation.customfood.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant

@Composable
internal fun AddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, Double, List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var prot by rememberSaveable { mutableStateOf("") }
    var carb by rememberSaveable { mutableStateOf("") }
    var fat  by rememberSaveable { mutableStateOf("") }
    var fib  by rememberSaveable { mutableStateOf("") }
    var salt by rememberSaveable { mutableStateOf("") }
    // CustomFoodRepository.save() already accepted/persisted aliases (built-in
    // FOOD_DB entries carry them so a bilingual search finds them either way),
    // but this dialog never collected any - every custom food a user created
    // was permanently unreachable by an alternate spelling/translation.
    var aliases by rememberSaveable { mutableStateOf("") }

    // Blank macro fields default to 0.0 at save time - only reject a field that's
    // actually filled in, and only accept values in a physically plausible range
    // (previously only negatives were rejected, so a typo like "1000" in the
    // protein field, or a missing decimal point turning 45.0g into 4500g, saved
    // silently - CustomFoodRepository.save() now clamps to the same bounds as a
    // defensive backstop, but rejecting it here lets the user actually fix the typo).
    fun inRangeOrBlank(s: String, max: Double) = s.isBlank() || (s.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..max } == true)
    val valid = name.isNotBlank() &&
        (kcal.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..900.0 } == true) &&
        inRangeOrBlank(prot, 100.0) && inRangeOrBlank(carb, 100.0) && inRangeOrBlank(fat, 100.0) && inRangeOrBlank(fib, 100.0) && inRangeOrBlank(salt, 100.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceVariant,
        title = { Text(stringResource(R.string.customfood_add_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodField(stringResource(R.string.customfood_field_name), name, KeyboardType.Text) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_kcal), kcal, KeyboardType.Decimal, Modifier.weight(1f)) { kcal = it }
                    FoodField(stringResource(R.string.customfood_field_protein), prot, KeyboardType.Decimal, Modifier.weight(1f)) { prot = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_carbs), carb, KeyboardType.Decimal, Modifier.weight(1f)) { carb = it }
                    FoodField(stringResource(R.string.customfood_field_fat), fat, KeyboardType.Decimal, Modifier.weight(1f)) { fat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_fiber), fib, KeyboardType.Decimal, Modifier.weight(1f)) { fib = it }
                    FoodField(stringResource(R.string.customfood_field_salt), salt, KeyboardType.Decimal, Modifier.weight(1f)) { salt = it }
                }
                FoodField(stringResource(R.string.customfood_field_aliases), aliases, KeyboardType.Text) { aliases = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        kcal.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        prot.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        carb.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        fat.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        fib.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        salt.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        aliases.split(',').map { it.trim() }.filter { it.isNotBlank() },
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.common_create), color = if (valid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
            }
        },
    )
}

@Composable
private fun FoodField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentCoral,
            unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor     = OnBackground,
            unfocusedTextColor   = OnBackground,
        ),
    )
}
