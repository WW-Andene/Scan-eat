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
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.ui.theme.*


@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.recipes_cd_new), tint = AccentGreen) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (recipes.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.RestaurantMenu, stringResource(R.string.recipes_empty_body),
                        ctaLabel = stringResource(R.string.recipes_cd_new), onCta = { showAdd = true },
                    )
                }
            }
            items(recipes.value, key = { it.id }) { recipe ->
                RecipeCard(recipe, onLog = { logTarget = recipe }, onDelete = { deleteTarget = recipe.id })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) AddRecipeDialog(onDismiss = { showAdd = false }, onSave = { name, comps -> viewModel.save(name, comps); showAdd = false })
    logTarget?.let { LogRecipeDialog(recipe = it, onDismiss = { logTarget = null }, onLog = { slot, frac -> viewModel.log(it, slot, frac); logTarget = null }) }

    deleteTarget?.let { id ->
        val name = recipes.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}

@Composable
private fun RecipeCard(recipe: Recipe, onLog: () -> Unit, onDelete: () -> Unit) {
    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(14.dp))) {
        Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.recipes_summary, recipe.totalKcal.toInt(), recipe.components.size, recipe.totalGrams.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                    }
                    Row {
                        IconButton(onClick = onLog, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentGreen) }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f)) }
                    }
                }
                recipe.components.take(3).forEach { c ->
                    Text(stringResource(R.string.templates_item_summary, c.productName, c.grams.toInt(), c.kcal.toInt()),
                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
                }
                if (recipe.components.size > 3) Text(stringResource(R.string.templates_more_items, recipe.components.size - 3), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
            }
        }
    }
}

@Composable
private fun AddRecipeDialog(onDismiss: () -> Unit, onSave: (String, List<RecipeComponent>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var components by remember { mutableStateOf(listOf<RecipeComponent>()) }
    var newIngName by remember { mutableStateOf("") }
    var newIngGrams by remember { mutableStateOf("") }
    var newIngKcal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor  = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.recipes_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = scanEatTextFieldColors())
                HorizontalDivider(color = OnBackground.copy(0.1f))
                Text(stringResource(R.string.recipes_ingredients_label), style = MaterialTheme.typography.labelMedium, color = AccentGreen)
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
                    val g = newIngGrams.replace(',', '.').toDoubleOrNull() ?: return@TextButton
                    val k = newIngKcal.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (newIngName.isNotBlank()) {
                        components = components + RecipeComponent(newIngName, g, k)
                        newIngName = ""; newIngGrams = ""; newIngKcal = ""
                    }
                }) { Text(stringResource(R.string.recipes_add_ingredient_button), color = AccentGreen) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && components.isNotEmpty()) onSave(name, components) }, enabled = name.isNotBlank() && components.isNotEmpty()) {
                Text(stringResource(R.string.common_create), color = AccentGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}

@Composable
private fun LogRecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onLog: (MealSlot, Double) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.LUNCH) }
    var fractionText by remember { mutableStateOf("1.0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_log_dialog_title, recipe.name), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen, labelColor = OnBackground.copy(0.7f)))
                    }
                }
                OutlinedTextField(value = fractionText, onValueChange = { fractionText = it }, label = { Text(stringResource(R.string.recipes_portion_label)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = scanEatTextFieldColors())
            }
        },
        confirmButton = { TextButton(onClick = { fractionText.replace(',', '.').toDoubleOrNull()?.let { onLog(slot, it) } }) { Text(stringResource(R.string.common_log), color = AccentGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
