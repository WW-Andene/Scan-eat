package fr.scanneat.presentation.customfood

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
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.presentation.ui.theme.*

@Composable
fun CustomFoodScreen(
    viewModel: CustomFoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val foods   = viewModel.foods.collectAsStateWithLifecycle()
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.searchResults.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

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
                        Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val customNames = remember(foods.value) { foods.value.mapTo(hashSetOf()) { it.name } }
            // Search bar
            OutlinedTextField(
                value = query.value,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                shape = RoundedCornerShape(12.dp),
                colors = scanEatTextFieldColors(),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    )
                }

                item { Spacer(Modifier.height(32.dp)) }
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

    // Delete confirmation
    deleteTarget?.let { name ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor   = SurfaceVariant,
            title = { Text(stringResource(R.string.customfood_delete_confirm_title, name), color = OnBackground) },
            text  = { Text(stringResource(R.string.customfood_delete_confirm_body), color = OnBackground.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(name); deleteTarget = null }) {
                    Text(stringResource(R.string.common_delete), color = FlagRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
                }
            },
        )
    }
}

@Composable
private fun FoodEntryRow(entry: FoodEntry, isCustom: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (isCustom) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AccentGreen.copy(0.15f),
                    ) {
                        Text(
                            stringResource(R.string.customfood_custom_badge),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.customfood_macro_summary, entry.kcal.toInt(), entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(0.55f),
            )
        }
        if (isCustom) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close, stringResource(R.string.common_delete),
                    tint = OnSurface.copy(0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddFoodDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double, Double, Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var prot by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat  by remember { mutableStateOf("") }
    var fib  by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }

    val valid = name.isNotBlank() && kcal.replace(',', '.').toDoubleOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceVariant,
        title = { Text(stringResource(R.string.customfood_add_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FoodField(stringResource(R.string.customfood_field_name), name, KeyboardType.Text) { name = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodField(stringResource(R.string.customfood_field_kcal), kcal, KeyboardType.Decimal, Modifier.weight(1f)) { kcal = it }
                    FoodField(stringResource(R.string.customfood_field_protein), prot, KeyboardType.Decimal, Modifier.weight(1f)) { prot = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodField(stringResource(R.string.customfood_field_carbs), carb, KeyboardType.Decimal, Modifier.weight(1f)) { carb = it }
                    FoodField(stringResource(R.string.customfood_field_fat), fat, KeyboardType.Decimal, Modifier.weight(1f)) { fat = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoodField(stringResource(R.string.customfood_field_fiber), fib, KeyboardType.Decimal, Modifier.weight(1f)) { fib = it }
                    FoodField(stringResource(R.string.customfood_field_salt), salt, KeyboardType.Decimal, Modifier.weight(1f)) { salt = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name.trim(),
                        kcal.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        prot.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        carb.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        fat.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        fib.replace(',', '.').toDoubleOrNull()  ?: 0.0,
                        salt.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    )
                },
                enabled = valid,
            ) {
                Text(stringResource(R.string.common_create), color = if (valid) AccentGreen else OnBackground.copy(0.3f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f))
            }
        },
    )
}

@Composable
private fun FoodField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = AccentGreen,
            unfocusedBorderColor = OnBackground.copy(0.18f),
            focusedTextColor     = OnBackground,
            unfocusedTextColor   = OnBackground,
        ),
    )
}
