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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.util.hasValidGs1CheckDigit

/**
 * Doubles as the edit dialog when [initial] is non-null - previously the only
 * way to fix a custom food's macro values (kcal/protein/etc) was delete and
 * re-create it from scratch, losing its id (and, per CustomFoodRepository.save's
 * own doc comment on the `id` param, "Add" on an existing id already updates in
 * place - this dialog just never passed one through). RenameDialog still covers
 * the name-only case elsewhere (Recipes/Templates); this is a full-field edit.
 */
@Composable
internal fun AddFoodDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, Double, Double, Double, Double, List<String>, String?) -> Unit,
    initial: FoodEntry? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var kcal by rememberSaveable { mutableStateOf(initial?.kcal?.let { formatFieldValue(it) } ?: "") }
    var prot by rememberSaveable { mutableStateOf(initial?.proteinG?.let { formatFieldValue(it) } ?: "") }
    var carb by rememberSaveable { mutableStateOf(initial?.carbsG?.let { formatFieldValue(it) } ?: "") }
    var fat  by rememberSaveable { mutableStateOf(initial?.fatG?.let { formatFieldValue(it) } ?: "") }
    var fib  by rememberSaveable { mutableStateOf(initial?.fiberG?.let { formatFieldValue(it) } ?: "") }
    var salt by rememberSaveable { mutableStateOf(initial?.saltG?.let { formatFieldValue(it) } ?: "") }
    // CustomFoodRepository.save() already accepted/persisted aliases (built-in
    // FOOD_DB entries carry them so a bilingual search finds them either way),
    // but this dialog never collected any - every custom food a user created
    // was permanently unreachable by an alternate spelling/translation.
    var aliases by rememberSaveable { mutableStateOf(initial?.aliases?.joinToString(", ") ?: "") }
    // CustomFoodRepository.save() also already accepted a barcode (used when saving
    // a scanned product as a custom food, see ResultViewModel.saveToDestinations),
    // but a manually-typed custom food had no way to attach one - so a food someone
    // typed in by hand could never be recognized again on a future rescan of the
    // same product. Optional: most manual entries (a homemade dish, a market item)
    // genuinely have no barcode.
    // Add-mode only - FoodEntry (the edit dialog's [initial]) carries no barcode
    // field to pre-fill from, and CustomFoodViewModel.update() preserves the
    // existing barcode itself (see its own doc comment) rather than risk this
    // dialog silently wiping it by passing back an empty value.
    var barcode by rememberSaveable { mutableStateOf("") }

    // Blank macro fields default to 0.0 at save time - only reject a field that's
    // actually filled in, and only accept values in a physically plausible range
    // (previously only negatives were rejected, so a typo like "1000" in the
    // protein field, or a missing decimal point turning 45.0g into 4500g, saved
    // silently - CustomFoodRepository.save() now clamps to the same bounds as a
    // defensive backstop, but rejecting it here lets the user actually fix the typo).
    fun inRangeOrBlank(s: String, max: Double) = s.isBlank() || (s.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..max } == true)
    val kcalValid = kcal.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..900.0 } == true
    val protValid = inRangeOrBlank(prot, 100.0)
    val carbValid = inRangeOrBlank(carb, 100.0)
    val fatValid  = inRangeOrBlank(fat, 100.0)
    val fibValid  = inRangeOrBlank(fib, 100.0)
    val saltValid = inRangeOrBlank(salt, 100.0)
    // A mistyped barcode previously saved silently (only digits were filtered),
    // so this custom food could never be matched back on a future rescan of
    // the same product - see hasValidGs1CheckDigit's own doc comment.
    val barcodeValid = barcode.isBlank() || hasValidGs1CheckDigit(barcode)
    val valid = name.isNotBlank() && kcalValid && protValid && carbValid && fatValid && fibValid && saltValid && barcodeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(if (initial != null) R.string.customfood_edit_title else R.string.customfood_add_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                val kcalError = stringResource(R.string.customfood_field_kcal_invalid)
                val macroError = stringResource(R.string.customfood_field_macro_invalid)
                val barcodeError = stringResource(R.string.customfood_field_barcode_invalid)
                FoodField(stringResource(R.string.customfood_field_name), name, KeyboardType.Text) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_kcal), kcal, KeyboardType.Decimal, Modifier.weight(1f), errorText = kcalError.takeIf { kcal.isNotBlank() && !kcalValid }) { kcal = it }
                    FoodField(stringResource(R.string.customfood_field_protein), prot, KeyboardType.Decimal, Modifier.weight(1f), errorText = macroError.takeIf { prot.isNotBlank() && !protValid }) { prot = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_carbs), carb, KeyboardType.Decimal, Modifier.weight(1f), errorText = macroError.takeIf { carb.isNotBlank() && !carbValid }) { carb = it }
                    FoodField(stringResource(R.string.customfood_field_fat), fat, KeyboardType.Decimal, Modifier.weight(1f), errorText = macroError.takeIf { fat.isNotBlank() && !fatValid }) { fat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FoodField(stringResource(R.string.customfood_field_fiber), fib, KeyboardType.Decimal, Modifier.weight(1f), errorText = macroError.takeIf { fib.isNotBlank() && !fibValid }) { fib = it }
                    FoodField(stringResource(R.string.customfood_field_salt), salt, KeyboardType.Decimal, Modifier.weight(1f), errorText = macroError.takeIf { salt.isNotBlank() && !saltValid }) { salt = it }
                }
                FoodField(stringResource(R.string.customfood_field_aliases), aliases, KeyboardType.Text) { aliases = it }
                if (initial == null) {
                    FoodField(
                        stringResource(R.string.customfood_field_barcode), barcode, KeyboardType.Number,
                        errorText = barcodeError.takeIf { barcode.isNotBlank() && !barcodeValid },
                    ) { barcode = it.filter(Char::isDigit) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        kcal.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        prot.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        carb.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        fat.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        fib.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        salt.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        aliases.split(',').map { it.trim() }.filter { it.isNotBlank() },
                        barcode.trim().ifBlank { null },
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(if (initial != null) R.string.common_save else R.string.common_create), color = if (valid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
            }
        },
    )
}

/** Trims a trailing ".0" (e.g. "45.0" -> "45") so a pre-filled numeric field
 *  doesn't force the user to delete two extra characters just to type over an
 *  edited value - int-valued macros are overwhelmingly the common case. */
private fun formatFieldValue(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun FoodField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    errorText: String? = null,
    onValue: (String) -> Unit,
) {
    // Previously the Save button just went silently disabled with no
    // indication of which of the seven fields was the problem - a user typing
    // "1200" in the kcal field (max 900) had to guess.
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it, color = semanticRed()) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = scanEatTextFieldColors(),
    )
}
