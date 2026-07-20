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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val query      = viewModel.searchQuery.collectAsStateWithLifecycle()
    val results    = viewModel.searchResults.collectAsStateWithLifecycle()
    val scanResults = viewModel.scanSearchResults.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<FoodEntry?>(null) }
    var selectedScan by remember { mutableStateOf<ScanResult?>(null) }

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

    // Picking a scan-history result logs its real product directly (full
    // OFF/LLM-sourced nutrition, barcode, source) instead of going through the
    // lossy FoodEntry-shaped reconstruction above.
    val pickedScan = selectedScan
    if (pickedScan != null) {
        LogSheet(
            product    = pickedScan.product,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onConfirm  = { portionG, mealSlot ->
                viewModel.addEntryFromScan(pickedScan, portionG, mealSlot)
                selectedScan = null
                onDismiss()
            },
            onDismiss  = { selectedScan = null },
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
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Spacer(Modifier.height(Spacing.S))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (query.value.isNotBlank() && results.value.isEmpty() && scanResults.value.isEmpty()) {
                        item {
                            Text(stringResource(R.string.diary_add_entry_no_results, query.value),
                                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
                                modifier = Modifier.padding(vertical = Spacing.M))
                        }
                    }
                    // Scan-history hits first - a barcoded product with real OFF/LLM-
                    // sourced nutrition is a more precise match than a FOOD_DB
                    // approximation of the same food.
                    items(scanResults.value, key = { "scan-${it.dbId}" }) { scan ->
                        Surface(
                            onClick = { selectedScan = scan },
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                            color = SurfaceVariant.copy(alpha = 0.42f),
                            modifier = Modifier.fillMaxWidth()
                                .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.diary_add_entry_kcal_per_100g, scan.product.nutrition.energyKcal.toInt()),
                                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            }
                        }
                    }
                    items(results.value, key = { it.name }) { entry ->
                        Surface(
                            onClick = { selected = entry },
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                            color = SurfaceVariant.copy(alpha = 0.42f),
                            modifier = Modifier.fillMaxWidth()
                                .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.clearSearch(); onDismiss() }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
