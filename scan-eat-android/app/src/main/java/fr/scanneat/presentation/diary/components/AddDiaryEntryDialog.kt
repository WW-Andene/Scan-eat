package fr.scanneat.presentation.diary.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.diary.DiaryViewModel
import fr.scanneat.presentation.result.LogSheet
import fr.scanneat.presentation.ui.theme.*

/**
 * Search-and-log flow — previously the only way to add a diary entry was via
 * barcode/photo scan; there was no way to log a home-cooked meal, a fruit, or
 * anything not barcode-scanned directly from the Journal. Two steps: pick a
 * food from search (built-in FOOD_DB + the user's own custom foods), then
 * reuse the same portion/meal-slot picker (LogSheet) the scan-result flow uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDiaryEntryDialog(viewModel: DiaryViewModel, onDismiss: () -> Unit) {
    val query   = viewModel.searchQuery.collectAsStateWithLifecycle()
    val results = viewModel.searchResults.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FoodEntry?>(null) }

    val picked = selected
    if (picked != null) {
        val product = remember(picked) {
            Product(
                name = picked.name, category = ProductCategory.OTHER, novaClass = NovaClass.UNPROCESSED,
                ingredients = listOf(Ingredient(name = picked.name, percentage = 100.0, category = IngredientCategory.FOOD, isWholeFood = true)),
                nutrition = NutritionPer100g(
                    energyKcal = picked.kcal, fatG = picked.fatG, saturatedFatG = 0.0, carbsG = picked.carbsG,
                    sugarsG = 0.0, fiberG = picked.fiberG, proteinG = picked.proteinG, saltG = picked.saltG,
                ),
                weightG = 100.0,
            )
        }
        LogSheet(
            product    = product,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onConfirm  = { portionG, mealSlot ->
                viewModel.addEntry(picked, portionG, mealSlot)
                selected = null
                onDismiss()
            },
            onDismiss  = { selected = null },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { viewModel.clearSearch(); onDismiss() },
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.diary_add_entry_title), color = OnBackground) },
        text = {
            Column(modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 360.dp)) {
                OutlinedTextField(
                    value = query.value,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.diary_add_entry_search_hint), color = OnBackground.copy(0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = OnBackground.copy(0.5f)) },
                    trailingIcon = {
                        if (query.value.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = scanEatTextFieldColors(),
                )
                Spacer(Modifier.height(Spacing.S))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (query.value.isNotBlank() && results.value.isEmpty()) {
                        item {
                            Text(stringResource(R.string.diary_add_entry_no_results, query.value),
                                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
                                modifier = Modifier.padding(vertical = Spacing.M))
                        }
                    }
                    items(results.value, key = { it.name }) { entry ->
                        Surface(
                            onClick = { selected = entry },
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.diary_add_entry_kcal_per_100g, entry.kcal.toInt()),
                                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.clearSearch(); onDismiss() }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
