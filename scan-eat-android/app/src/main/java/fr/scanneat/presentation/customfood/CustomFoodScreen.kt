package fr.scanneat.presentation.customfood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.presentation.customfood.components.AddFoodDialog
import fr.scanneat.presentation.customfood.components.FoodEntryRow
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*

@Composable
fun CustomFoodScreen(
    viewModel: CustomFoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    val foods   = viewModel.foods.collectAsStateWithLifecycle()
    val foodsWithId = viewModel.foodsWithId.collectAsStateWithLifecycle()
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.searchResults.collectAsStateWithLifecycle()
    val latestScan = viewModel.latestScan.collectAsStateWithLifecycle()
    val avgKcal = viewModel.avgKcal.collectAsStateWithLifecycle()
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to current name

    val displayList = if (query.value.isBlank()) foods.value else results.value

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.customfood_title), color = OnBackground) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
            }
        },
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.CUSTOM_FOODS, onNavigate = onNavigateToPlanning)
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = AccentCoral, secondary = CalorieOrange)) {
            // Search bar
            OutlinedTextField(
                value = query.value,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.L, vertical = Spacing.S),
                placeholder = { Text(stringResource(R.string.customfood_search_placeholder), color = OnBackground.copy(0.4f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = OnBackground.copy(0.5f)) },
                trailingIcon = {
                    if (query.value.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Default.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )

            // Library stat chips — count + avg kcal, shown once there's at least one custom food.
            if (foods.value.isNotEmpty() && query.value.isBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = OnBackground.copy(0.06f)) {
                        Text(
                            stringResource(R.string.customfood_stats_count, foods.value.size),
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                        )
                    }
                    avgKcal.value?.let { avg ->
                        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f)) {
                            Text(
                                stringResource(R.string.customfood_stats_avg_kcal, avg),
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                style = MaterialTheme.typography.labelSmall, color = AccentCoral,
                            )
                        }
                    }
                }
            }

            // Import from last scan banner — surfaces when the most recent scan isn't
            // already saved as a custom food, offering a one-tap import.
            val scan = latestScan.value
            val customNames = remember(foods.value) { foods.value.mapTo(hashSetOf()) { it.name } }
            if (scan != null && scan.product.name !in customNames) {
                Surface(
                    color = AccentCoral.copy(0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS),
                ) {
                    Row(
                        Modifier.padding(Spacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.customfood_import_from_scan, scan.product.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCoral,
                            modifier = Modifier.weight(1f),
                        )
                        // Left at IconButton's default 48dp touch target (Material/WCAG
                        // minimum, was 32dp) and given a real contentDescription (was
                        // null - a TalkBack user heard nothing for this import action).
                        IconButton(onClick = { viewModel.importFromScan(scan) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.customfood_import_from_scan, scan.product.name), tint = AccentCoral, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                if (displayList.isEmpty()) {
                    item {
                        EmptyListState(
                            Icons.Default.RestaurantMenu,
                            if (query.value.isBlank()) stringResource(R.string.customfood_empty_body)
                            else stringResource(R.string.customfood_empty_query, query.value),
                        )
                    }
                }

                // Indexed (rather than keyed purely on name) so two entries that happen to
                // share a display name — e.g. a custom food named the same as a built-in
                // FOOD_DB hit while searching — can never collide on a LazyColumn key and
                // crash Compose.
                itemsIndexed(displayList, key = { index, entry -> "$index:${entry.name}" }) { _, entry ->
                    FoodEntryRow(
                        entry    = entry,
                        isCustom = entry.name in customNames,
                        hints    = generateProductHints(viewModel.toProduct(entry), profile.value, language.value),
                        // Matched on full structural equality (every field), not just name -
                        // two custom foods sharing a name (e.g. after a backup restore, or a
                        // displayed row that's actually a built-in FOOD_DB hit sharing a
                        // custom food's name) previously resolved via a name-only firstOrNull,
                        // so tapping delete/rename on the row the user is actually looking at
                        // could silently mutate/delete a different row instead.
                        onDelete = {
                            foodsWithId.value.firstOrNull { it.second == entry }
                                ?.let { (id, _) -> deleteTarget = id to entry.name }
                        },
                        onRename = {
                            // Previously delete was the only entry point — a typo in a
                            // custom food's name could never be fixed without deleting
                            // and re-creating it from scratch.
                            foodsWithId.value.firstOrNull { it.second == entry }
                                ?.let { (id, _) -> renameTarget = id to entry.name }
                        },
                    )
                }

                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
        }
    }

    // Add dialog
    if (showAdd) {
        AddFoodDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, kcal, prot, carb, fat, fib, salt, aliases, barcode ->
                viewModel.save(name, kcal, prot, carb, fat, fib, salt, aliases, barcode)
                showAdd = false
            },
        )
    }

    // Delete confirmation — shared dialog, same as Weight/Templates/Recipes/Activity.
    deleteTarget?.let { (id, name) ->
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = { viewModel.delete(id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }

    renameTarget?.let { (id, currentName) ->
        RenameDialog(
            currentName = currentName,
            onDismiss   = { renameTarget = null },
            onConfirm   = { newName -> viewModel.rename(id, newName); renameTarget = null },
        )
    }
}
