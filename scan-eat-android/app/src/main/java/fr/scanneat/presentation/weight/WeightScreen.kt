package fr.scanneat.presentation.weight

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter


private val fmt = DateTimeFormatter.ofPattern("dd MMM")

@Composable
fun WeightScreen(
    viewModel: WeightViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val entries  = viewModel.entries.collectAsStateWithLifecycle()
    val summary  = viewModel.summary.collectAsStateWithLifecycle()
    val forecast = viewModel.forecast.collectAsStateWithLifecycle()

    var kgText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weight_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = AccentGreen) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Summary card
            summary.value?.let { s ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(stringResource(R.string.weight_kg, s.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                                    val sign = if (s.deltaKg >= 0) "+" else ""
                                    val dColor = if (s.deltaKg <= 0) FlagGreen else FlagRed
                                    Text(stringResource(R.string.weight_delta_kg, "$sign${s.deltaKg}"), style = MaterialTheme.typography.labelSmall, color = dColor)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val tSign = if (s.trendKgPerWeek >= 0) "+" else ""
                                    Text(stringResource(R.string.weight_trend_kg_week, "$tSign${s.trendKgPerWeek}"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                                    if (forecast.value is WeightForecast.Ok) {
                                        val f = forecast.value as WeightForecast.Ok
                                        Text(stringResource(R.string.weight_goal_forecast, f.days), style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Entries
            items(entries.value.reversed()) { e ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(e.date.format(fmt), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                    Text(stringResource(R.string.weight_kg, e.weightKg), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                    IconButton(onClick = { viewModel.delete(e.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.weight_dialog_title), color = OnBackground) },
            text = {
                OutlinedTextField(
                    value = kgText, onValueChange = { kgText = it },
                    label = { Text(stringResource(R.string.weight_field_kg)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = OnBackground.copy(0.2f), focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    kgText.toDoubleOrNull()?.let { viewModel.log(it); kgText = ""; showAdd = false }
                }) { Text(stringResource(R.string.common_save), color = AccentGreen) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            containerColor = SurfaceVariant,
        )
    }

}
