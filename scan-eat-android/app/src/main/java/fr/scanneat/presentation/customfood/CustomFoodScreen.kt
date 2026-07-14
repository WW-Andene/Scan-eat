package fr.scanneat.presentation.customfood

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import fr.scanneat.presentation.customfood.components.AddFoodDialog
import fr.scanneat.presentation.customfood.components.FoodEntryRow
import fr.scanneat.presentation.ui.theme.*

@Composable
fun CustomFoodScreen(
    viewModel: CustomFoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val foods   = viewModel.foods.collectAsStateWithLifecycle()
    val foodsWithId = viewModel.foodsWithId.collectAsStateWithLifecycle()
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.searchResults.collectAsStateWithLifecycle()
    val latestScan = viewModel.latestScan.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to current name

    val displayList = if (query.value.isBlank()) foods.value else results.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.customfood_title), color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                            "Importer « ${scan.product.name} » depuis le dernier scan",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCoral,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.importFromScan(scan) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
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

                items(displayList, key = { it.name }) { entry ->
                    FoodEntryRow(
                        entry    = entry,
                        isCustom = entry.name in customNames,
                        onDelete = { deleteTarget = entry.name },
                        onRename = {
                            // Previously delete was the only entry point — a typo in a
                            // custom food's name could never be fixed without deleting
                            // and re-creating it from scratch.
                            foodsWithId.value.firstOrNull { it.second.name == entry.name }
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
            onSave    = { name, kcal, prot, carb, fat, fib, salt ->
                viewModel.save(name, kcal, prot, carb, fat, fib, salt)
                showAdd = false
            },
        )
    }

    // Delete confirmation — shared dialog, same as Weight/Templates/Recipes/Activity.
    deleteTarget?.let { name ->
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = { viewModel.delete(name); deleteTarget = null },
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
