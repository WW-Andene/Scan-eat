package fr.scanneat.presentation.recipes.components

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.Recipe
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.label
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import kotlin.math.roundToInt

// FEATURE: log-by-servings — recipes always stored a `servings` count, but
// nothing in the app ever read it, so logging a portion of a multi-serving
// dish meant mentally computing "1 serving / 4 servings = 0.25" and typing
// that fraction by hand. This lets the user say how many servings they ate
// directly and does the fraction math for them, showing the per-serving
// gram weight as a sanity check.
@Composable
internal fun LogRecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onLog: (MealSlot, Double) -> Unit) {
    var slot by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MealSlot.LUNCH) }
    var servingsText by rememberSaveable { mutableStateOf("1") }
    val gramsPerServing = recipe.totalGrams / recipe.servings
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_log_dialog_title, recipe.name), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                    }
                }
                OutlinedTextField(value = servingsText, onValueChange = { servingsText = it },
                    label = { Text(stringResource(R.string.recipes_servings_to_log_label, recipe.servings)) }, singleLine = true,
                    isError = servingsText.isNotBlank() && (servingsText.replace(',', '.').toDoubleOrNull()?.let { it <= 0.0 } != false),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = scanEatTextFieldColors())
                Text(stringResource(R.string.recipes_per_serving_hint, gramsPerServing.roundToInt()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
        },
        confirmButton = {
            // A zero/negative servings-to-log would log negative kcal/macros via
            // consumptionRepo.log - same numeric-entry guard as Weight/CustomFood.
            val servingsToLog = servingsText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
            TextButton(onClick = { servingsToLog?.let { onLog(slot, it / recipe.servings) } }, enabled = servingsToLog != null) {
                Text(stringResource(R.string.common_log), color = if (servingsToLog != null) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
