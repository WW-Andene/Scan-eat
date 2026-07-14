package fr.scanneat.presentation.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
            if (templates.value.isEmpty()) {
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
                                    // Previously the only way a template could get items was
                                    // never - create() always built one with an empty list and
                                    // there was no UI anywhere to add to it afterward.
                                    IconButton(onClick = { itemsTarget = template }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.templates_manage_items_cd), tint = Teal)
                                    }
                                    IconButton(onClick = { logTarget = template }, modifier = Modifier.size(36.dp), enabled = template.items.isNotEmpty()) {
                                        Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = if (template.items.isNotEmpty()) AccentCoral else OnSurface.copy(0.25f))
                                    }
                                    IconButton(onClick = { renameTarget = template }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Edit, stringResource(R.string.common_rename), tint = OnSurface.copy(0.5f))
                                    }
                                    IconButton(onClick = { deleteTarget = template.id }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f))
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
                                    M("P", template.totalProteinG, semanticGreen())
                                    M("G", template.totalCarbsG, AccentCoral)
                                    M("L", template.totalFatG, Gold)
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
                        Text("Portion", style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                        Text("×${String.format("%.1f", portion)}  →  ${(t.totalKcal * portion).toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = AccentCoral)
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
            onDismiss = { itemsTarget = null },
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
) {
    var newName by remember { mutableStateOf("") }
    var newGrams by remember { mutableStateOf("") }
    var newKcal by remember { mutableStateOf("") }

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
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.recipes_field_ingredient)) }, modifier = Modifier.weight(2f), singleLine = true,
                        colors = scanEatTextFieldColors())
                    OutlinedTextField(value = newGrams, onValueChange = { newGrams = it }, label = { Text("g") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = scanEatTextFieldColors())
                    OutlinedTextField(value = newKcal, onValueChange = { newKcal = it }, label = { Text("kcal") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = scanEatTextFieldColors())
                }
                TextButton(onClick = {
                    val g = newGrams.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return@TextButton
                    val k = newKcal.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 } ?: 0.0
                    if (newName.isNotBlank()) {
                        onAdd(TemplateItem(productName = newName.trim(), grams = g, meal = template.meal.name.lowercase(), kcal = k))
                        newName = ""; newGrams = ""; newKcal = ""
                    }
                }) { Text(stringResource(R.string.recipes_add_ingredient_button), color = AccentCoral) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
