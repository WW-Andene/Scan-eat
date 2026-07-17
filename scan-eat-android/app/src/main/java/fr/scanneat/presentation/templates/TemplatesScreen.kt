package fr.scanneat.presentation.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate


@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val templates = viewModel.templates.collectAsStateWithLifecycle()
    val mealFilter = viewModel.mealFilter.collectAsStateWithLifecycle()
    val libraryTotalKcal = viewModel.libraryTotalKcal.collectAsStateWithLifecycle()
    val ingredientSearchResults = viewModel.ingredientSearchResults.collectAsStateWithLifecycle()
    val templateWarnings = viewModel.templateWarnings.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<MealTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<MealTemplate?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var itemsTarget by remember { mutableStateOf<MealTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.templates_cd_new), tint = AccentCoral) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Templates had zero list-level filtering despite MealTemplate.meal being
                // the exact same ready-made filter dimension Recipes already uses for its
                // own GoalFilter chips.
                val mealOptions = listOf<MealSlot?>(null) + MealSlot.values().toList()
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    items(mealOptions) { slot ->
                        FilterChip(
                            selected = mealFilter.value == slot,
                            onClick = { viewModel.setMealFilter(slot) },
                            label = { Text(slot?.label() ?: stringResource(R.string.recipes_filter_all)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                        )
                    }
                }
            }
            if (templates.value.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = OnBackground.copy(0.06f)) {
                            Text(
                                stringResource(R.string.templates_stats_count, templates.value.size),
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                            )
                        }
                        if (libraryTotalKcal.value > 0) {
                            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f)) {
                                Text(
                                    stringResource(R.string.templates_stats_total_kcal, libraryTotalKcal.value),
                                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                    style = MaterialTheme.typography.labelSmall, color = AccentCoral,
                                )
                            }
                        }
                    }
                }
            } else {
                item { EmptyListState(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.templates_empty_body)) }
            }
            items(templates.value, key = { it.id }) { template ->
                Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CONTROL))) {
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.templates_summary, template.totalKcal, template.items.size, template.meal.name.lowercase()),
                                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                                Row {
                                    // Templates had no equivalent to Recipes'/ScanHistory's favorite
                                    // pin at all - same Star/StarBorder pattern as RecipeCard.
                                    // Left at IconButton's default 48dp touch target (Material/WCAG
                                    // minimum) - a UI/UX audit found 6 icon-sized controls competing
                                    // for width in this row, each forced below the 48dp minimum.
                                    IconButton(onClick = { viewModel.toggleFavorite(template) }) {
                                        Icon(
                                            if (template.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            stringResource(if (template.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                                            tint = if (template.favorite) Gold else OnSurface.copy(0.3f),
                                        )
                                    }
                                    // Previously the only way a template could get items was
                                    // never - create() always built one with an empty list and
                                    // there was no UI anywhere to add to it afterward.
                                    IconButton(onClick = { itemsTarget = template }) {
                                        Icon(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.templates_manage_items_cd), tint = Teal)
                                    }
                                    IconButton(onClick = { logTarget = template }, enabled = template.items.isNotEmpty()) {
                                        Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = if (template.items.isNotEmpty()) AccentCoral else OnSurface.copy(0.25f))
                                    }
                                    // Save-as-Recipe/Rename/Delete moved into an overflow menu -
                                    // Favorite/Manage-items/Log are the frequent actions and stay
                                    // directly visible at full size.
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, stringResource(R.string.recipes_cd_more_actions), tint = OnSurface.copy(0.5f))
                                    }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        // Templates/Recipes had no way to convert between the two -
                                        // this saves a copy into the user's Recipes library.
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.templates_cd_save_as_recipe)) },
                                            leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null) },
                                            enabled = template.items.isNotEmpty(),
                                            onClick = { menuExpanded = false; viewModel.saveAsRecipe(template) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.common_rename)) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = { menuExpanded = false; renameTarget = template },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.common_delete)) },
                                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                            onClick = { menuExpanded = false; deleteTarget = template.id },
                                        )
                                    }
                                }
                            }
                            template.items.take(3).forEach { item ->
                                Text(stringResource(R.string.templates_item_summary, item.productName, item.grams.toInt(), item.kcal.toInt()),
                                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                            }
                            if (template.items.size > 3) {
                                Text(stringResource(R.string.templates_more_items, template.items.size - 3), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
                            }
                            // Diet/allergen check previously only ever ran on Recipes/Grocery -
                            // a template built from ingredients the user's own profile forbids
                            // had no warning anywhere in this screen, despite being logged
                            // repeatedly.
                            templateWarnings.value[template.id]?.let { warning ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
                                    Text(warning, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
                                }
                            }
                            // Macro summary — protein/carbs/fat totals were only available
                            // in the log dialog; here on the card a user had no quick read
                            // on whether the template fits their current macro targets.
                            if (template.items.isNotEmpty()) {
                                HorizontalDivider(color = OnSurface.copy(0.07f))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                                    @Composable fun M(label: String, v: Int, color: androidx.compose.ui.graphics.Color) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.45f))
                                            Text("${v}g", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                    M(stringResource(R.string.macro_protein_abbr), template.totalProteinG, semanticGreen())
                                    M(stringResource(R.string.macro_carbs_abbr), template.totalCarbsG, AccentCoral)
                                    M(stringResource(R.string.macro_fat_abbr), template.totalFatG, Gold)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    logTarget?.let { t ->
        var slot by remember { mutableStateOf(t.meal) }
        var portion by remember { mutableFloatStateOf(1f) }
        AlertDialog(
            onDismissRequest = { logTarget = null },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.templates_log_dialog_title, t.name), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealSlot.values().forEach { s ->
                            FilterChip(selected = slot == s, onClick = { slot = s },
                                label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                        }
                    }
                    // Portion scale slider — previously the template was always logged at
                    // 100% with no way to adjust for a half portion or a double serving.
                    HorizontalDivider(color = OnBackground.copy(0.08f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.templates_log_portion_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                        Text(stringResource(R.string.templates_log_portion_value, String.format("%.1f", portion), (t.totalKcal * portion).toInt()), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                    }
                    Slider(
                        value = portion, onValueChange = { portion = it },
                        valueRange = 0.25f..3f, steps = 10,
                        colors = SliderDefaults.colors(thumbColor = AccentCoral, activeTrackColor = AccentCoral),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.logTemplate(t, mealOverride = slot, portionScale = portion.toDouble()); logTarget = null }) { Text(stringResource(R.string.common_log), color = AccentCoral) } },
            dismissButton = { TextButton(onClick = { logTarget = null }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

    deleteTarget?.let { id ->
        val name = templates.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

    renameTarget?.let { template ->
        RenameDialog(
            currentName = template.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> viewModel.rename(template, newName); renameTarget = null },
        )
    }

    itemsTarget?.let { t ->
        // Re-fetch the live template by id every recomposition — after each
        // add/remove the underlying list changes via the repo, and `t` itself
        // is a stale snapshot from whenever the dialog was opened.
        val live = templates.value.find { it.id == t.id } ?: t
        EditTemplateItemsDialog(
            template = live,
            onAdd    = { item -> viewModel.addItem(live, item) },
            onRemove = { index -> viewModel.removeItem(live, index) },
            onDismiss = { viewModel.setIngredientQuery(""); itemsTarget = null },
            searchResults = ingredientSearchResults.value,
            onQueryChange = viewModel::setIngredientQuery,
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var meal by remember { mutableStateOf(MealSlot.LUNCH) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.templates_add_dialog_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.recipes_field_name)) }, singleLine = true, colors = scanEatTextFieldColors())
                    Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealSlot.values().forEach { s ->
                            FilterChip(selected = meal == s, onClick = { meal = s },
                                label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.create(name, meal); showAdd = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.common_create), color = AccentCoral)
                }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

}

/** Same ingredient-adding shape as RecipesScreen's AddRecipeDialog — templates never had an equivalent, so items could only ever be added by directly editing the database. */
@Composable
private fun EditTemplateItemsDialog(
    template: MealTemplate,
    onAdd: (TemplateItem) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
    searchResults: List<fr.scanneat.domain.engine.nutrition.FoodEntry> = emptyList(),
    onQueryChange: (String) -> Unit = {},
) {
    var newName by remember { mutableStateOf("") }
    var newGrams by remember { mutableStateOf("") }
    var newKcal by remember { mutableStateOf("") }
    // Set once a FOOD_DB/custom-food search result is picked - carries full
    // macros (protein/carbs/fat/fiber/salt), not just the kcal the manual field
    // below ever captured. Cleared whenever the name is edited by hand again, same
    // pattern AddRecipeDialog already uses for the identical situation.
    var selectedFood by remember { mutableStateOf<fr.scanneat.domain.engine.nutrition.FoodEntry?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.templates_manage_items_title, template.name), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (template.items.isEmpty()) {
                    Text(stringResource(R.string.templates_no_items_yet), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                template.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.recipes_ingredient_summary_compact, item.productName, item.grams.toInt(), item.kcal.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = OnSurface, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
                HorizontalDivider(color = OnBackground.copy(0.1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; selectedFood = null; onQueryChange(it) },
                        label = { Text(stringResource(R.string.recipes_field_ingredient)) }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = scanEatTextFieldColors(),
                    )
                    OutlinedTextField(value = newGrams, onValueChange = { newGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        searchResults.take(5).forEach { food ->
                            Text(
                                food.name, style = MaterialTheme.typography.bodySmall, color = AccentCoral,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedFood = food
                                    newName = food.name
                                    onQueryChange("")
                                }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
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
                }) { Text(stringResource(R.string.recipes_add_ingredient_button), color = AccentCoral) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
