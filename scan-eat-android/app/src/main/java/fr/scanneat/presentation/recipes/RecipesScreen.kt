package fr.scanneat.presentation.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.data.repository.*
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recettes", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Nouvelle recette", tint = AccentGreen) } },
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
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🍳", style = MaterialTheme.typography.displaySmall)
                            Text("Aucune recette. Créez votre première recette multi-ingrédients.", color = OnBackground.copy(0.5f))
                        }
                    }
                }
            }
            items(recipes.value) { recipe ->
                RecipeCard(recipe, onLog = { logTarget = recipe }, onDelete = { viewModel.delete(recipe.id) })
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) AddRecipeDialog(onDismiss = { showAdd = false }, onSave = { name, comps -> viewModel.save(name, comps); showAdd = false })
    logTarget?.let { LogRecipeDialog(recipe = it, onDismiss = { logTarget = null }, onLog = { slot, frac -> viewModel.log(it, slot, frac); logTarget = null }) }

}

@Composable
private fun RecipeCard(recipe: Recipe, onLog: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text("${recipe.totalKcal.toInt()} kcal · ${recipe.components.size} ingrédients · ${recipe.totalGrams.toInt()} g",
                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                }
                Row {
                    IconButton(onClick = onLog, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "Logger", tint = AccentGreen) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Supprimer", tint = OnSurface.copy(0.4f)) }
                }
            }
            recipe.components.take(3).forEach { c ->
                Text("• ${c.productName} · ${c.grams.toInt()} g · ${c.kcal.toInt()} kcal",
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
            }
            if (recipe.components.size > 3) Text("+ ${recipe.components.size - 3} de plus…", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.4f))
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
        title = { Text("Nouvelle recette", color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la recette") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
                HorizontalDivider(color = OnBackground.copy(0.1f))
                Text("Ingrédients", style = MaterialTheme.typography.labelMedium, color = AccentGreen)
                components.forEach { c ->
                    Text("• ${c.productName} ${c.grams.toInt()}g ${c.kcal.toInt()}kcal", style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = newIngName, onValueChange = { newIngName = it }, label = { Text("Ingrédient") }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
                    OutlinedTextField(value = newIngGrams, onValueChange = { newIngGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
                    OutlinedTextField(value = newIngKcal, onValueChange = { newIngKcal = it }, label = { Text("kcal") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
                }
                TextButton(onClick = {
                    val g = newIngGrams.toDoubleOrNull() ?: return@TextButton
                    val k = newIngKcal.toDoubleOrNull() ?: 0.0
                    if (newIngName.isNotBlank()) {
                        components = components + RecipeComponent(newIngName, g, k)
                        newIngName = ""; newIngGrams = ""; newIngKcal = ""
                    }
                }) { Text("+ Ajouter ingrédient", color = AccentGreen) }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && components.isNotEmpty()) onSave(name, components) }, enabled = name.isNotBlank() && components.isNotEmpty()) {
                Text("Créer", color = AccentGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = OnBackground.copy(0.6f)) } },
    )
}

@Composable
private fun LogRecipeDialog(recipe: Recipe, onDismiss: () -> Unit, onLog: (MealSlot, Double) -> Unit) {
    var slot by remember { mutableStateOf(MealSlot.LUNCH) }
    var fractionText by remember { mutableStateOf("1.0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text("Logger « ${recipe.name} »", color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Repas", style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.name.take(3), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen, labelColor = OnBackground.copy(0.7f)))
                    }
                }
                OutlinedTextField(value = fractionText, onValueChange = { fractionText = it }, label = { Text("Portion (1.0 = entière)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
            }
        },
        confirmButton = { TextButton(onClick = { fractionText.toDoubleOrNull()?.let { onLog(slot, it) } }) { Text("Logger", color = AccentGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = OnBackground.copy(0.6f)) } },
    )
}
