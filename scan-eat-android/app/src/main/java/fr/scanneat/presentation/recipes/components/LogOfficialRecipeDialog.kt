package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.label
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import kotlin.math.roundToInt

// FEATURE: log-by-portion — this always logged the recipe's full totalGrams
// with no way to log a partial portion, unlike LogRecipeDialog's servings
// slider for the user's own (clonable) recipes. OfficialRecipe has no
// `servings` count of its own (unlike Recipe), so this asks for grams eaten
// directly instead, defaulting to the full recipe weight.
@Composable
internal fun LogOfficialRecipeDialog(recipe: OfficialRecipe, isFrench: Boolean, onDismiss: () -> Unit, onLog: (MealSlot, Double) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.LUNCH) }
    val totalGrams = recipe.totalGrams.takeIf { it > 0 } ?: 100.0
    var gramsText by remember { mutableStateOf(totalGrams.roundToInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.recipes_log_dialog_title, if (isFrench) recipe.nameFr else recipe.nameEn), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                    }
                }
                OutlinedTextField(
                    value = gramsText, onValueChange = { gramsText = it },
                    label = { Text(stringResource(R.string.recipes_portion_grams_label, totalGrams.roundToInt())) }, singleLine = true,
                    isError = gramsText.isNotBlank() && (gramsText.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            // A zero/negative portion would log negative kcal/macros - same numeric-entry
            // guard LogRecipeDialog already uses for its servings-to-log field.
            val gramsToLog = gramsText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
            TextButton(onClick = { gramsToLog?.let { onLog(slot, it / totalGrams) } }, enabled = gramsToLog != null) {
                Text(stringResource(R.string.common_log), color = if (gramsToLog != null) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
