package fr.scanneat.presentation.weight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
    onOpenCalendar: () -> Unit = {},
) {
    val entries  = viewModel.entries.collectAsStateWithLifecycle()
    val summary  = viewModel.summary.collectAsStateWithLifecycle()
    val forecast = viewModel.forecast.collectAsStateWithLifecycle()
    val goalWeightKg = viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val heightCm = viewModel.heightCm.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val useImperialState = viewModel.useImperial.collectAsStateWithLifecycle()
    val weeklyAvg = viewModel.weeklyAvg.collectAsStateWithLifecycle()
    val loggingStreakDays = viewModel.loggingStreakDays.collectAsStateWithLifecycle()
    // In-app language (Settings) can differ from the device locale, so day/month
    // abbreviations must follow it explicitly - ofPattern() alone defaults to
    // Locale.getDefault(), which would silently mix languages in the date labels.
    val fmt = remember(language.value) { DateTimeFormatter.ofPattern("dd MMM", Locale(language.value)) }

    var kgText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    // Log dialog previously always wrote LocalDate.now() — WeightRepository.log()/
    // WeightDao.upsertForDate already fully support an arbitrary date (used by
    // restore() for the Undo snackbar), but there was no way to enter a missed
    // weigh-in for a past day from the UI itself.
    var entryDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Previously plain remember state with no backing store — reset to kg on
    // every screen reopen/process recreation. Local var still used as the
    // read/write surface everywhere below (unchanged call sites), just backed
    // by the persisted StateFlow instead of a value that can never survive
    // leaving the screen.
    val useImperial = useImperialState.value
    fun setUseImperial(v: Boolean) = viewModel.setUseImperial(v)
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.weight_deleted_message)
    val undoLabel = stringResource(R.string.weight_undo)
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    fun dispWeight(kg: Double): String =
        if (useImperial) "%.1f lb".format(Locale.US, kg * 2.20462) else "%.1f kg".format(Locale.US, kg)

    val content = @Composable { padding: PaddingValues ->
        val reversedEntries = remember(entries.value) { entries.value.reversed() }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Unit toggle + calendar nav — previously an inline single-domain
            // MonthCalendar toggled here; now routes to the unified Calendar
            // (Dashboard) which shows weight alongside every other tracker.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to "kg", true to "lb").forEach { (imperial, label) ->
                            FilterChip(
                                selected = useImperial == imperial,
                                onClick = { setUseImperial(imperial) },
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
                    ScanEatCard(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(dispWeight(s.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                                val sign = if (s.deltaKg >= 0) "+" else ""
                                // Previously hardcoded "down = green, up = red" regardless of the
                                // user's actual goal — a user with a gain goal (goalWeightKg above
                                // current weight, e.g. a bulk/recovery program) saw progress toward
                                // their own goal colored red.
                                val wantsGain = goalWeightKg.value?.let { it > s.latestKg } ?: false
                                val dColor = if (wantsGain) {
                                    if (s.deltaKg >= 0) semanticGreen() else semanticRed()
                                } else {
                                    if (s.deltaKg <= 0) semanticGreen() else semanticRed()
                                }
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
                        // BMI row — only shown when profile height is set
                        heightCm.value?.let { hcm ->
                            val hm = hcm / 100.0
                            val bmi = s.latestKg / (hm * hm)
                            val (bmiLabel, bmiColor) = when {
                                bmi < 18.5 -> stringResource(R.string.weight_bmi_underweight) to semanticBlue()
                                bmi < 25.0 -> stringResource(R.string.weight_bmi_normal) to semanticGreen()
                                bmi < 30.0 -> stringResource(R.string.weight_bmi_overweight) to semanticAmber()
                                else       -> stringResource(R.string.weight_bmi_obese) to semanticRed()
                            }
                            HorizontalDivider(color = OnSurface.copy(0.08f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.weight_bmi_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    Text("%.1f".format(Locale.US, bmi), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = bmiColor)
                                    Surface(shape = RoundedCornerShape(4.dp), color = bmiColor.copy(0.15f)) {
                                        Text(bmiLabel, style = MaterialTheme.typography.labelSmall, color = bmiColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
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
                        val streak = loggingStreakDays.value
                        if (streak > 0) {
                            HorizontalDivider(color = OnSurface.copy(0.08f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.weight_logging_streak_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Surface(shape = RoundedCornerShape(50), color = Gold.copy(0.15f)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        // Icon, not the 🔥 emoji baked into the string before -
                                        // same LocalFireDepartment streak-badge convention already
                                        // used by Activity/Medication/Fasting/Hydration's own streaks.
                                        Icon(Icons.Default.LocalFireDepartment, null, tint = Gold, modifier = Modifier.size(14.dp))
                                        Text(
                                            stringResource(R.string.weight_logging_streak_value, streak),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Gold,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Line chart — up to 30 most-recent entries as a Canvas polyline
            if (entries.value.size > 1) {
                item {
                    val chartEntries = entries.value.takeLast(30)
                    val goalKg = goalWeightKg.value
                    // Improvement: include goal weight in range so the dashed goal line is always visible
                    val allWeights = chartEntries.map { it.weightKg } + listOfNotNull(goalKg)
                    val minW = allWeights.min()
                    val maxW = allWeights.max().coerceAtLeast(minW + 0.5)
                    val lineColor = AccentCoral
                    val dotColor  = AccentCoral
                    val goalLineColor = semanticGreen()
                    val trendDescription = stringResource(
                        R.string.weight_trend_cd,
                        dispWeight(chartEntries.first().weightKg),
                        dispWeight(chartEntries.last().weightKg),
                        chartEntries.size,
                    )
                    Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(CardRadius.CONTROL))) {
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.M)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.weight_trend_caption, chartEntries.size), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Text(dispWeight(chartEntries.last().weightKg), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = AccentCoral)
                            }
                            Spacer(Modifier.height(Spacing.S))
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clearAndSetSemantics { contentDescription = trendDescription },
                            ) {
                                val w = size.width
                                val h = size.height
                                val n = chartEntries.size
                                if (n < 2) return@Canvas
                                val xStep = w / (n - 1).toFloat()
                                fun xAt(i: Int) = i * xStep
                                fun yAt(kg: Double) = h * (1f - ((kg - minW) / (maxW - minW)).toFloat()).coerceIn(0f, 1f)

                                // Fill area under the line
                                val fillPath = Path().apply {
                                    moveTo(xAt(0), h)
                                    chartEntries.forEachIndexed { i, e -> lineTo(xAt(i), yAt(e.weightKg)) }
                                    lineTo(xAt(n - 1), h)
                                    close()
                                }
                                drawPath(fillPath, color = lineColor.copy(alpha = 0.12f))

                                // Line
                                val linePath = Path().apply {
                                    chartEntries.forEachIndexed { i, e ->
                                        val x = xAt(i); val y = yAt(e.weightKg)
                                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                                    }
                                }
                                drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

                                // Dots
                                chartEntries.forEachIndexed { i, e ->
                                    val isLast = i == n - 1
                                    drawCircle(
                                        color = if (isLast) dotColor else dotColor.copy(0.4f),
                                        radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                                        center = Offset(xAt(i), yAt(e.weightKg)),
                                    )
                                }

                                // Goal line — dashed horizontal at the target weight
                                goalKg?.let { gk ->
                                    val gy = yAt(gk)
                                    drawLine(
                                        color = goalLineColor.copy(0.7f),
                                        start = Offset(0f, gy),
                                        end   = Offset(w, gy),
                                        strokeWidth = 1.5.dp.toPx(),
                                        pathEffect  = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                                    )
                                }
                            }
                            Spacer(Modifier.height(Spacing.XS))
                            // Bumped from 0.35f - a UI/UX audit flagged these axis dates
                            // as real chart data rendered too faint against the dark surface.
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(chartEntries.first().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Text(chartEntries.last().date.format(fmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            }
                        }
                    }
                    }
                }
            }

            // New: weekly average comparison card — daily weigh-ins are noisy;
            // comparing this week's average to last week's gives a clearer trend.
            weeklyAvg.value?.let { (thisWeek, lastWeek) ->
                item {
                    val delta = thisWeek - lastWeek
                    val better = delta < 0 // losing weight = better for most goals
                    val dColor = if (delta < -0.1) semanticGreen() else if (delta > 0.1) semanticRed() else OnSurface.copy(0.6f)
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.M), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(stringResource(R.string.weight_weekly_avg_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                                    Text(dispWeight(thisWeek), style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                                    Text("vs ${dispWeight(lastWeek)}", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                }
                            }
                            Surface(shape = RoundedCornerShape(50), color = dColor.copy(0.15f)) {
                                val sign = if (delta >= 0) "+" else ""
                                Text(
                                    "$sign${"%.1f".format(Locale.US, if (useImperial) delta * 2.20462 else delta)} ${if (useImperial) "lb" else "kg"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = dColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Entries — improvement: per-row delta shows gain/loss vs previous weigh-in
            if (reversedEntries.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.Scale, stringResource(R.string.weight_empty_body),
                        ctaLabel = stringResource(R.string.weight_cd_add), onCta = { showAdd = true },
                    )
                }
            }
            itemsIndexed(reversedEntries, key = { _, e -> e.id }) { idx, e ->
                val prev = if (idx > 0) reversedEntries[idx - 1] else null
                val delta = prev?.let { e.weightKg - it.weightKg }
                Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(CardRadius.CONTROL))) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).background(SurfaceVariant).padding(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.date.format(fmt), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                        if (e.notes.isNotBlank()) {
                            Text(e.notes, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                        }
                    }
                    if (delta != null) {
                        val dColor = if (delta < -0.05) semanticGreen() else if (delta > 0.05) semanticRed() else OnSurface.copy(0.4f)
                        val sign = if (delta >= 0) "+" else ""
                        Text(
                            "$sign${"%.1f".format(Locale.US, if (useImperial) delta * 2.20462 else delta)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = dColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = Spacing.XS),
                        )
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
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { showDatePicker = true },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, OnBackground.copy(0.2f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(stringResource(R.string.weight_field_date), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                                Text(entryDate.format(fmt), style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                            }
                            Icon(Icons.Default.DateRange, null, tint = OnBackground.copy(0.6f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val kg = if (useImperial) kgValue!! / 2.20462 else kgValue!!
                        viewModel.log(kg, notesText, entryDate)
                        kgText = ""; notesText = ""; entryDate = LocalDate.now(); showAdd = false
                    },
                    enabled = isValidWeight,
                ) { Text(stringResource(R.string.common_save), color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            containerColor = SurfaceVariant,
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = entryDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        entryDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        ) { DatePicker(state = datePickerState) }
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
