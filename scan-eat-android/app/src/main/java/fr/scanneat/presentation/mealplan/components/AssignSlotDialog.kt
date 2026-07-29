package fr.scanneat.presentation.mealplan.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.presentation.ui.theme.*

// ============================================================================
// FEATURE: assign a saved Recipe or Template to a meal-plan slot. Previously
// the only way to populate a day's plan was a free-text note — the recipe/
// template picker (and the model support behind it — MealPlanSlot.RecipeSlot/
// TemplateSlot, MealPlanRepository's "recipe"/"template" serialization kinds,
// MealPlanViewModel's orphan-pruning safeguard) already existed one layer
// down but had no UI entry point to actually create one.
// ============================================================================
@Composable
internal fun AssignSlotDialog(
    mealLabel: String,
    recipes: List<Recipe>,
    templates: List<MealTemplate>,
    onPickRecipe: (Recipe) -> Unit,
    onPickTemplate: (MealTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.mealplan_assign_title, mealLabel), color = OnBackground) },
        text = {
            if (recipes.isEmpty() && templates.isEmpty()) {
                // Was plain Text() - every other empty condition in the app
                // routes through this shared icon+message component.
                EmptyListState(Icons.Rounded.RestaurantMenu, stringResource(R.string.mealplan_assign_empty))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (recipes.isNotEmpty()) {
                        item { Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.labelMedium, color = AccentCoral) }
                        items(recipes, key = { "r_${it.id}" }) { recipe ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPickRecipe(recipe) }.padding(vertical = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                Icon(Icons.Rounded.RestaurantMenu, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
                                Text(recipe.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            }
                        }
                    }
                    if (templates.isNotEmpty()) {
                        item { Text(stringResource(R.string.templates_title), style = MaterialTheme.typography.labelMedium, color = AccentCoral) }
                        items(templates, key = { "t_${it.id}" }) { template ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPickTemplate(template) }.padding(vertical = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
                                Text(template.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
