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
import fr.scanneat.presentation.reminders.WeightReminderCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch
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
    var showCalendar by remember { mutableStateOf(false) }
    var calendarMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
    var calendarSelected by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.weight_deleted_message)
    val undoLabel = stringResource(R.string.weight_undo)

    fun dispWeight(kg: Double): String =
        if (useImperial) "%.1f lb".format(Locale.US, kg * 2.20462) else "%.1f kg".format(Locale.US, kg)

    val content = @Composable { padding: PaddingValues ->
        val reversedEntries = remember(entries.value) { entries.value.reversed() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Unit toggle + calendar toggle
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = if (showCalendar) AccentCoral else OnBackground.copy(0.5f))
                    }
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

            if (showCalendar) {
                item {
                    val markedDates = remember(entries.value) { entries.value.map { it.date }.toSet() }
                    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(Spacing.M)) {
                                MonthCalendar(
                                    month = calendarMonth,
                                    selected = calendarSelected,
                                    markedDates = markedDates,
                                    locale = Locale(language.value),
                                    onMonthChange = { calendarMonth = it },
                                    onDayClick = { calendarSelected = if (calendarSelected == it) null else it },
                                )
                                calendarSelected?.let { day ->
                                    val entry = entries.value.find { it.date == day }
                                    Spacer(Modifier.height(Spacing.S))
                                    Text(
                                        if (entry != null) stringResource(R.string.weight_calendar_day_summary, day.format(fmt), dispWeight(entry.weightKg))
                                        else stringResource(R.string.weight_calendar_day_empty, day.format(fmt)),
                                        style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summary card
            summary.value?.let { s ->
                item {
                    ScanEatCard(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(dispWeight(s.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                                val sign = if (s.deltaKg >= 0) "+" else ""
                                val dColor = if (s.deltaKg <= 0) semanticGreen() else semanticRed()
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
                                    color = if (kotlin.math.abs(toGoal) < 0.5) semanticGreen() else AccentCoral,
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
                        Column(Modifier.padding(Spacing.M)) {
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
                Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(12.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant).padding(Spacing.M),
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
            item { WeightReminderCard() }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(0.dp))
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Background,
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        // A negative/zero/absurd weight fed straight into WeightSummary/WeightForecast/
        // BMI trend calcs with no guard at all - bound it to a sane human range in
        // whichever unit is displayed, matching ActivityScreen's validated-numeric pattern.
        val kgValue = kgText.replace(',', '.').toDoubleOrNull()
        val isValidWeight = kgValue != null && (if (useImperial) kgValue in 44.0..880.0 else kgValue in 20.0..400.0)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.weight_dialog_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = kgText, onValueChange = { kgText = it },
                        label = { Text(if (useImperial) stringResource(R.string.weight_field_lb) else stringResource(R.string.weight_field_kg)) }, singleLine = true,
                        isError = kgText.isNotBlank() && !isValidWeight,
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
                TextButton(
                    onClick = {
                        val kg = if (useImperial) kgValue!! / 2.20462 else kgValue!!
                        viewModel.log(kg, notesText)
                        kgText = ""; notesText = ""; showAdd = false
                    },
                    enabled = isValidWeight,
                ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            containerColor = SurfaceVariant,
        )
    }

    deleteTarget?.let { id ->
        val target = entries.value.find { it.id == id }
        val name = target?.date?.format(fmt)
        DeleteConfirmDialog(
            itemName = name,
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
                if (target != null) {
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(target)
                    }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

}
