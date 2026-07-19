package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

@Composable
internal fun AddRecipeDialog(
    onDismiss: () -> Unit, onConfirm: (String, List<RecipeComponent>, Int, String) -> Unit,
    searchResults: List<FoodEntry> = emptyList(), onQueryChange: (String) -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var components by remember { mutableStateOf(listOf<RecipeComponent>()) }
    var newIngName by rememberSaveable { mutableStateOf("") }
    var newIngGrams by rememberSaveable { mutableStateOf("") }
    var newIngKcal by rememberSaveable { mutableStateOf("") }
    // Set once a FOOD_DB/custom-food search result is picked - carries full
    // macros (protein/carbs/fat/fiber), not just the kcal the manual fields
    // below ever captured. Cleared whenever the name is edited by hand again,
    // so a stale selection can't silently get applied to a since-retyped name.
    var selectedFood by remember { mutableStateOf<FoodEntry?>(null) }
    // The recipe model has always supported a `servings` count (RecipeRepository.Recipe /
    // RecipeEntity), but this dialog never collected it, so every recipe saved through the
    // UI was silently persisted with servings=1 regardless of how many portions it actually
    // makes — the log dialog's "portion fraction" then had to be reverse-engineered by hand
    // (e.g. entering 0.25 for "1 of 4 servings"). Collecting it here is what makes the
    // servings-based logging in LogRecipeDialog below meaningful.
    var servingsText by rememberSaveable { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor  = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.recipes_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = scanEatTextFieldColors())
                OutlinedTextField(value = servingsText, onValueChange = { servingsText = it }, label = { Text(stringResource(R.string.recipes_field_servings)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = scanEatTextFieldColors())
                HorizontalDivider(color = OnBackground.copy(0.1f))
                Text(stringResource(R.string.recipes_ingredients_label), style = MaterialTheme.typography.labelMedium, color = AccentCoral)
                components.forEach { c ->
                    Text(stringResource(R.string.recipes_ingredient_summary_compact, c.productName, c.grams.toInt(), c.kcal.toInt()), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newIngName,
                        onValueChange = { newIngName = it; selectedFood = null; onQueryChange(it) },
                        label = { Text(stringResource(R.string.recipes_field_ingredient)) }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = scanEatTextFieldColors(),
                    )
                    OutlinedTextField(value = newIngGrams, onValueChange = { newIngGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = scanEatTextFieldColors())
                    // Manual kcal entry only matters as a fallback once no database match is
                    // picked - hidden once one is, since its full macros are used instead.
                    if (selectedFood == null) {
                        OutlinedTextField(value = newIngKcal, onValueChange = { newIngKcal = it }, label = { Text("kcal") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = scanEatTextFieldColors())
                    }
                }
                // FOOD_DB + custom-food search results - previously this dialog had no
                // lookup at all, so ingredients could never carry real protein/carbs/
                // fat/fiber, and a user's own custom food could never be reused here.
                if (selectedFood == null && searchResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        searchResults.take(5).forEach { food ->
                            Text(
                                food.name, style = MaterialTheme.typography.bodySmall, color = AccentCoral,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedFood = food
                                    newIngName = food.name
                                    onQueryChange("")
                                }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    // A zero/negative gram amount would divide-by-zero or invert the
                    // per-100g nutrition math wherever this component is later scaled.
                    val g = newIngGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@TextButton
                    if (newIngName.isBlank()) return@TextButton
                    val food = selectedFood
                    val component = if (food != null) {
                        RecipeComponent(
                            productName = food.name, grams = g,
                            kcal     = food.kcal * g / 100.0,
                            proteinG = food.proteinG * g / 100.0,
                            carbsG   = food.carbsG * g / 100.0,
                            fatG     = food.fatG * g / 100.0,
                            fiberG   = food.fiberG * g / 100.0,
                            // RecipeComponent.saltG feeds Recipe.nutritionPer100g.saltG
                            // (used by toCheckProduct()'s diet checks and any diary
                            // entry logged from this recipe) - previously never
                            // copied from the picked food, silently zeroing sodium
                            // for every ingredient added via search.
                            saltG    = food.saltG * g / 100.0,
                        )
                    } else {
                        val k = newIngKcal.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
                        RecipeComponent(newIngName, g, k)
                    }
                    components = components + component
                    newIngName = ""; newIngGrams = ""; newIngKcal = ""; selectedFood = null; onQueryChange("")
                }) { Text(stringResource(R.string.recipes_add_ingredient_button), color = AccentCoral) }
                HorizontalDivider(color = OnBackground.copy(0.1f))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.recipes_field_notes)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, minLines = 2, maxLines = 5,
                    colors = scanEatTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && components.isNotEmpty()) {
                    val servings = servingsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onConfirm(name, components, servings, notes.trim())
                }
            }, enabled = name.isNotBlank() && components.isNotEmpty()) {
                Text(stringResource(R.string.common_create), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
