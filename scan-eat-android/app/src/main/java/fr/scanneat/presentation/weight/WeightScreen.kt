package fr.scanneat.presentation.weight

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar — used when
 * hosted as a Journal sub-tab, where the tab row itself is the header and a
 * second nested app bar (with a dead-end back arrow) would be redundant
 * chrome. Standalone push-navigation callers leave it false.
 */
@Composable
fun WeightScreen(
    viewModel: WeightViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val entries  = viewModel.entries.collectAsStateWithLifecycle()
    val summary  = viewModel.summary.collectAsStateWithLifecycle()
    val forecast = viewModel.forecast.collectAsStateWithLifecycle()
    val goalWeightKg = viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    // In-app language (Settings) can differ from the device locale, so day/month
    // abbreviations must follow it explicitly - ofPattern() alone defaults to
    // Locale.getDefault(), which would silently mix languages in the date labels.
    val fmt = remember(language.value) { DateTimeFormatter.ofPattern("dd MMM", Locale(language.value)) }

    var kgText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var useImperial by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    fun dispWeight(kg: Double): String =
        if (useImperial) "%.1f lb".format(kg * 2.20462) else "%.1f kg".format(kg)

    val content = @Composable { padding: PaddingValues ->
        val reversedEntries = remember(entries.value) { entries.value.reversed() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Unit toggle
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to "kg", true to "lb").forEach { (imperial, label) ->
                            FilterChip(
                                selected = useImperial == imperial,
                                onClick = { useImperial = imperial },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                            )
                        }
                    }
                }
            }

            // Summary card
            summary.value?.let { s ->
                item {
                    ScanEatCard(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(dispWeight(s.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                                val sign = if (s.deltaKg >= 0) "+" else ""
                                val dColor = if (s.deltaKg <= 0) FlagGreen else FlagRed
                                Text(stringResource(R.string.weight_delta_kg, "$sign${s.deltaKg}"), style = MaterialTheme.typography.labelSmall, color = dColor)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val tSign = if (s.trendKgPerWeek >= 0) "+" else ""
                                Text(stringResource(R.string.weight_trend_kg_week, "$tSign${s.trendKgPerWeek}"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                                if (forecast.value is WeightForecast.Ok) {
                                    val f = forecast.value as WeightForecast.Ok
                                    Text(stringResource(R.string.weight_goal_forecast, f.days), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                                }
                            }
                        }
                        goalWeightKg.value?.let { goal ->
                            HorizontalDivider(color = OnSurface.copy(0.08f))
                            val toGoal = s.latestKg - goal
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.weight_goal_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Text(
                                    stringResource(R.string.weight_goal_delta, "${if (toGoal > 0) "−" else "+"}${dispWeight(kotlin.math.abs(toGoal))}", dispWeight(goal)),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                                    color = if (kotlin.math.abs(toGoal) < 0.5) FlagGreen else AccentCoral,
                                )
                            }
                        }
                    }
                }
            }

            // Sparkline — last 8 entries
            if (entries.value.size > 1) {
                item {
                    val last8 = entries.value.takeLast(8)
                    val minW = last8.minOf { it.weightKg }
                    val maxW = (last8.maxOf { it.weightKg }).coerceAtLeast(minW + 0.1)
                    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.weight_trend_caption, last8.size), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            Spacer(Modifier.height(8.dp))
                            val trendDescription = stringResource(
                                R.string.weight_trend_cd,
                                dispWeight(last8.first().weightKg),
                                dispWeight(last8.last().weightKg),
                                last8.size,
                            )
                            Row(
                                Modifier.fillMaxWidth().height(48.dp).clearAndSetSemantics { contentDescription = trendDescription },
                                horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
                            ) {
                                last8.forEachIndexed { i, e ->
                                    val isLast = i == last8.size - 1
                                    val h = (((e.weightKg - minW) / (maxW - minW)) * 40.0 + 6.0).toInt()
                                    Box(Modifier.weight(1f).height(h.dp).background(if (isLast) AccentCoral else AccentCoral.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                    }
                    }
                }
            }

            // Entries
            items(reversedEntries, key = { it.id }) { e ->
                Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(10.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.date.format(fmt), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                        if (e.notes.isNotBlank()) {
                            Text(e.notes, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                        }
                    }
                    Text(dispWeight(e.weightKg), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { deleteTarget = e.id }) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = AccentCoral,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.weight_title), color = OnBackground) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                    actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentCoral) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            },
            containerColor = Background,
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.weight_dialog_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = kgText, onValueChange = { kgText = it },
                        label = { Text(if (useImperial) stringResource(R.string.weight_field_lb) else stringResource(R.string.weight_field_kg)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = scanEatTextFieldColors(),
                    )
                    OutlinedTextField(
                        value = notesText, onValueChange = { notesText = it },
                        label = { Text(stringResource(R.string.weight_field_notes)) }, singleLine = true,
                        colors = scanEatTextFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    kgText.replace(',', '.').toDoubleOrNull()?.let { v ->
                        val kg = if (useImperial) v / 2.20462 else v
                        viewModel.log(kg, notesText)
                        kgText = ""; notesText = ""; showAdd = false
                    }
                }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            containerColor = SurfaceVariant,
        )
    }

    deleteTarget?.let { id ->
        val name = entries.value.find { it.id == id }?.date?.format(fmt)
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}
