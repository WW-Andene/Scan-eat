package fr.scanneat.presentation.templates.components

import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.data.repository.planning.TemplateItem
import fr.scanneat.presentation.ui.theme.*

/** Same ingredient-adding shape as RecipesScreen's AddRecipeDialog — templates never had an equivalent, so items could only ever be added by directly editing the database. */
@Composable
internal fun EditTemplateItemsDialog(
    template: MealTemplate,
    onAdd: (TemplateItem) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
    searchResults: List<fr.scanneat.domain.engine.nutrition.FoodEntry> = emptyList(),
    onQueryChange: (String) -> Unit = {},
) {
    var newName by rememberSaveable { mutableStateOf("") }
    var newGrams by rememberSaveable { mutableStateOf("") }
    var newKcal by rememberSaveable { mutableStateOf("") }
    // Set once a FOOD_DB/custom-food search result is picked - carries full
    // macros (protein/carbs/fat/fiber/salt), not just the kcal the manual field
    // below ever captured. Cleared whenever the name is edited by hand again, same
    // pattern AddRecipeDialog already uses for the identical situation.
    var selectedFood by remember { mutableStateOf<fr.scanneat.domain.engine.nutrition.FoodEntry?>(null) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_manage_items_title, template.name), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SM)) {
                if (template.items.isEmpty()) {
                    Text(stringResource(R.string.templates_no_items_yet), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                template.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.recipes_ingredient_summary_compact, item.productName, item.grams.toInt(), item.kcal.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Tiny))
                        }
                    }
                }
                HorizontalDivider(color = OnBackground.copy(0.1f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; selectedFood = null; onQueryChange(it) },
                        label = { Text(stringResource(R.string.recipes_field_ingredient)) }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = scanEatTextFieldColors(),
                    )
                    OutlinedTextField(value = newGrams, onValueChange = { newGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
                        isError = newGrams.isNotBlank() && (newGrams.replace(',', '.').toDoubleOrNull()?.let { it > 0 } != true),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = scanEatTextFieldColors())
                    // Manual kcal entry only matters as a fallback once no database match
                    // is picked - hidden once one is, since its full macros are used instead.
                    if (selectedFood == null) {
                        OutlinedTextField(value = newKcal, onValueChange = { newKcal = it }, label = { Text("kcal") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = scanEatTextFieldColors())
                    }
                }
                // FOOD_DB + custom-food search results - previously this dialog had no
                // lookup at all, so every item's protein/carbs/fat/fiber defaulted to 0
                // even when the food was already known, leaving TemplatesScreen's macro
                // summary chips permanently 0g/0g/0g.
                if (selectedFood == null && searchResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
                        searchResults.take(5).forEach { food ->
                            Text(
                                food.name, style = MaterialTheme.typography.bodySmall, color = AccentCoral,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedFood = food
                                    newName = food.name
                                    onQueryChange("")
                                }.padding(vertical = Spacing.XS),
                            )
                        }
                    }
                }
                // Was gated only inside the click handler (silent return@TextButton on
                // invalid grams/blank name) with no enabled/error state - a mistyped
                // grams field (e.g. "0" or blank) made "Add" visibly do nothing, with
                // no signal the tap even registered.
                val addGramsValid = newGrams.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true
                val canAdd = addGramsValid && newName.isNotBlank()
                TextButton(onClick = {
                    val g = newGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@TextButton
                    if (newName.isBlank()) return@TextButton
                    val food = selectedFood
                    val item = if (food != null) {
                        TemplateItem(
                            productName = food.name, grams = g, meal = template.meal.name.lowercase(),
                            kcal     = food.kcal * g / 100.0,
                            proteinG = food.proteinG * g / 100.0,
                            carbsG   = food.carbsG * g / 100.0,
                            fatG     = food.fatG * g / 100.0,
                            fiberG   = food.fiberG * g / 100.0,
                            saltG    = food.saltG * g / 100.0,
                        )
                    } else {
                        val k = newKcal.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
                        TemplateItem(productName = newName.trim(), grams = g, meal = template.meal.name.lowercase(), kcal = k)
                    }
                    onAdd(item)
                    newName = ""; newGrams = ""; newKcal = ""; selectedFood = null; onQueryChange("")
                }, enabled = canAdd) { Text(stringResource(R.string.recipes_add_ingredient_button), color = if (canAdd) AccentCoral else OnBackground.copy(0.3f)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
