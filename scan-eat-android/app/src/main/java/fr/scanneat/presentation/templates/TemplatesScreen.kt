package fr.scanneat.presentation.templates

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (templates.value.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.ListAlt, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(40.dp))
                            Text(stringResource(R.string.templates_empty_body), color = OnBackground.copy(0.5f))
                        }
                    }
                }
            }
            items(templates.value, key = { it.id }) { template ->
                Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(14.dp))) {
                    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.name, style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.templates_summary, template.totalKcal, template.items.size, template.meal.name.lowercase()),
                                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                                Row {
                                    IconButton(onClick = { logTarget = template }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentGreen)
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MealSlot.values().forEach { s ->
                            FilterChip(selected = slot == s, onClick = { slot = s },
                                label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen, labelColor = OnBackground.copy(0.7f)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.logTemplate(t, mealOverride = slot); logTarget = null }) { Text(stringResource(R.string.common_log), color = AccentGreen) } },
            dismissButton = { TextButton(onClick = { logTarget = null }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

    deleteTarget?.let { id ->
        DeleteConfirmDialog(onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}
