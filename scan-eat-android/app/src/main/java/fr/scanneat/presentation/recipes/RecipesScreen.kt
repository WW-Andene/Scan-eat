package fr.scanneat.presentation.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt


@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val warnings = viewModel.recipeWarnings.collectAsStateWithLifecycle()
    val officialWarnings = viewModel.officialRecipeWarnings.collectAsStateWithLifecycle()
    val pairings = viewModel.recipePairings.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Recipe?>(null) }
    var logOfficialTarget by remember { mutableStateOf<OfficialRecipe?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.recipes_cd_new), tint = AccentCoral) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- Official starter recipes — real CIQUAL/ANSES-sourced nutrition,
            // built to the Santé publique France / PNNS "assiette-type" portion
            // model. Read-only: log them directly, or clone into an editable
            // recipe of your own. See OfficialRecipeDb.kt for full provenance. ----
            item {
                Text(stringResource(R.string.recipes_official_section_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                Text(stringResource(R.string.recipes_official_section_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            items(viewModel.officialRecipes) { recipe ->
                OfficialRecipeCard(
                    recipe   = recipe,
                    isFrench = language.value == "fr",
                    warning  = officialWarnings.value[recipe.nameFr],
                    pairings = viewModel.officialRecipePairings[recipe.nameFr] ?: emptyList(),
                    onLog    = { logOfficialTarget = recipe },
                    onClone  = { viewModel.cloneOfficial(recipe) },
                )
            }
            item { Spacer(Modifier.height(Spacing.M)) }
            item {
                Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }

            if (recipes.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.RestaurantMenu, stringResource(R.string.recipes_empty_body),
                        ctaLabel = stringResource(R.string.recipes_cd_new), onCta = { showAdd = true },
                    )
                }
            }
            items(recipes.value, key = { it.id }) { recipe ->
                RecipeCard(recipe, warning = warnings.value[recipe.id], pairings = pairings.value[recipe.id] ?: emptyList(), onLog = { logTarget = recipe }, onDelete = { deleteTarget = recipe.id }, onRename = { renameTarget = recipe })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) AddRecipeDialog(onDismiss = { showAdd = false }, onSave = { name, comps, servings -> viewModel.save(name, comps, servings); showAdd = false })
    logTarget?.let { LogRecipeDialog(recipe = it, onDismiss = { logTarget = null }, onLog = { slot, frac -> viewModel.log(it, slot, frac); logTarget = null }) }
    logOfficialTarget?.let { recipe ->
        LogOfficialRecipeDialog(recipe = recipe, isFrench = language.value == "fr", onDismiss = { logOfficialTarget = null }, onLog = { slot -> viewModel.logOfficial(recipe, slot); logOfficialTarget = null })
    }
    renameTarget?.let { recipe ->
        RenameDialog(
            currentName = recipe.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> viewModel.rename(recipe, newName); renameTarget = null },
        )
    }

    deleteTarget?.let { id ->
        val name = recipes.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}

@Composable
private fun RecipeCard(recipe: Recipe, warning: String?, pairings: List<String>, onLog: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.recipes_summary, recipe.totalKcal.toInt(), recipe.components.size, recipe.totalGrams.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                    }
                    Row {
                        // Sized to match TemplatesScreen's identical Log+Delete row pattern —
                        // left at the unconstrained 48dp default, this delete control had a
                        // larger hit target than every other delete affordance in the app,
                        // right next to the primary Log action.
                        IconButton(onClick = onLog, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral) }
                        IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.common_rename), tint = OnSurface.copy(0.5f)) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f)) }
                    }
                }
                recipe.components.take(3).forEach { c ->
                    Text(stringResource(R.string.templates_item_summary, c.productName, c.grams.toInt(), c.kcal.toInt()),
                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                }
                if (recipe.components.size > 3) Text(stringResource(R.string.templates_more_items, recipe.components.size - 3), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
                // Diet/allergen check previously only ever ran on scanned products -
                // a recipe built from ingredients the user's own profile forbids
                // (allergen or diet violation) had no warning anywhere in this screen.
                warning?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
                    }
                }
                // findPairings()/PairingsDb.kt (Ahn et al. flavor-network data) was
                // already used for scanned products but never reached Recipes - the
                // exact same "what goes well with this" question applies here too.
                if (pairings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.recipes_pairs_well_with, pairings.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OfficialRecipeCard(recipe: OfficialRecipe, isFrench: Boolean, warning: String?, pairings: List<String>, onLog: () -> Unit, onClone: () -> Unit) {
    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isFrench) recipe.nameFr else recipe.nameEn, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.recipes_summary, recipe.totalKcal.toInt(), recipe.ingredients.size, recipe.totalGrams.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f),
                        )
                    }
                    Row {
                        IconButton(onClick = onLog, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral) }
                        IconButton(onClick = onClone, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ContentCopy, stringResource(R.string.recipes_official_clone_cd), tint = OnSurface.copy(0.5f)) }
                    }
                }
                recipe.ingredients.take(4).forEach { ing ->
                    Text("${ing.foodName} · ${ing.grams.toInt()} g", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                }
                warning?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(16.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = semanticAmber())
                    }
                }
                if (pairings.isNotEmpty()) {
                    Text(
                        stringResource(R.string.recipes_pairs_well_with, pairings.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LogOfficialRecipeDialog(recipe: OfficialRecipe, isFrench: Boolean, onDismiss: () -> Unit, onLog: (MealSlot) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.LUNCH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_log_dialog_title, if (isFrench) recipe.nameFr else recipe.nameEn), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onLog(slot) }) { Text(stringResource(R.string.common_log), color = AccentCoral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}

@Composable
private fun AddRecipeDialog(onDismiss: () -> Unit, onSave: (String, List<RecipeComponent>, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var components by remember { mutableStateOf(listOf<RecipeComponent>()) }
    var newIngName by remember { mutableStateOf("") }
    var newIngGrams by remember { mutableStateOf("") }
    var newIngKcal by remember { mutableStateOf("") }
    // The recipe model has always supported a `servings` count (RecipeRepository.Recipe /
    // RecipeEntity), but this dialog never collected it, so every recipe saved through the
    // UI was silently persisted with servings=1 regardless of how many portions it actually
    // makes — the log dialog's "portion fraction" then had to be reverse-engineered by hand
    // (e.g. entering 0.25 for "1 of 4 servings"). Collecting it here is what makes the
    // servings-based logging in LogRecipeDialog below meaningful.
    var servingsText by remember { mutableStateOf("1") }

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
                    OutlinedTextField(value = newIngName, onValueChange = { newIngName = it }, label = { Text(stringResource(R.string.recipes_field_ingredient)) }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = scanEatTextFieldColors())
                    OutlinedTextField(value = newIngGrams, onValueChange = { newIngGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = scanEatTextFieldColors())
                    OutlinedTextField(value = newIngKcal, onValueChange = { newIngKcal = it }, label = { Text("kcal") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = scanEatTextFieldColors())
                }
                TextButton(onClick = {
                    // A zero/negative gram amount would divide-by-zero or invert the
                    // per-100g nutrition math wherever this component is later scaled.
                    val g = newIngGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@TextButton
                    val k = newIngKcal.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
                    if (newIngName.isNotBlank()) {
                        components = components + RecipeComponent(newIngName, g, k)
                        newIngName = ""; newIngGrams = ""; newIngKcal = ""
                    }
                }) { Text(stringResource(R.string.recipes_add_ingredient_button), color = AccentCoral) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && components.isNotEmpty()) {
                    val servings = servingsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    onSave(name, components, servings)
                }
            }, enabled = name.isNotBlank() && components.isNotEmpty()) {
                Text(stringResource(R.string.common_create), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}

// ============================================================================
// FEATURE: log-by-servings — recipes always stored a `servings` count, but
// nothing in the app ever read it, so logging a portion of a multi-serving
// dish meant mentally computing "1 serving / 4 servings = 0.25" and typing
// that fraction by hand. This lets the user say how many servings they ate
// directly and does the fraction math for them, showing the per-serving
// gram weight as a sanity check.
// ============================================================================
@Composable
private fun LogRecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onLog: (MealSlot, Double) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.LUNCH) }
    var servingsText by remember { mutableStateOf("1") }
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
            TextButton(onClick = {
                servingsText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let { servingsToLog ->
                    onLog(slot, servingsToLog / recipe.servings)
                }
            }) {
                Text(stringResource(R.string.common_log), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
