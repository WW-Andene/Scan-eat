package fr.scanneat.presentation.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
                Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.templates_summary, template.totalKcal, template.items.size, template.meal.name.lowercase()),
                                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                                Row {
                                    IconButton(onClick = { logTarget = template }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral)
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
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    logTarget?.let { t ->
        var slot by remember { mutableStateOf(t.meal) }
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
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.logTemplate(t, mealOverride = slot); logTarget = null }) { Text(stringResource(R.string.common_log), color = AccentCoral) } },
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
