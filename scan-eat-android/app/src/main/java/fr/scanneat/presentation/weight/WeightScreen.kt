package fr.scanneat.presentation.weight

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.reminders.WeightReminderCard
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.ui.theme.dispWeight as sharedDispWeight
import fr.scanneat.presentation.weight.components.AddWeightDialog
import fr.scanneat.presentation.weight.components.WeeklyAverageCard
import fr.scanneat.presentation.weight.components.WeightDatePickerDialog
import fr.scanneat.presentation.weight.components.WeightEntryRow
import fr.scanneat.presentation.weight.components.WeightSummaryCard
import fr.scanneat.presentation.weight.components.WeightTrendChart
import fr.scanneat.presentation.weight.components.WeightUnitToggleRow
import fr.scanneat.util.formatDecimal
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    // Only meaningful when [embedded] — the host (DiaryScreen) supplies this so
    // this screen's own LazyColumn reserves the same floating-bottom-nav
    // clearance the host itself is already reserving, now that MainShell no
    // longer pads content away from that nav bar at the outer level.
    embeddedBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    // Same reasoning as embeddedBottomPadding, for the host's floating header:
    // passed straight into this screen's own LazyColumn contentPadding (not a
    // Modifier.padding on some wrapping Box) so scrolled items can still scroll
    // up into the region behind the header - the whole point of a floating,
    // blurred header is that content passes underneath it; a hard outer padding
    // would just make that region permanently empty instead.
    embeddedTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
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

    var kgText by rememberSaveable { mutableStateOf("") }
    var notesText by rememberSaveable { mutableStateOf("") }
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
    // Every "open Add" entry point (FAB, top-bar action, empty-state CTA) must reset the
    // dialog fields, not just the save path - otherwise cancelling an Edit and then tapping
    // Add reopens the dialog still prefilled with the edited entry's weight/notes/date, and
    // saving silently overwrites that entry's date again instead of logging today.
    fun openAddDialog() { kgText = ""; notesText = ""; entryDate = LocalDate.now(); showAdd = true }
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

    fun dispWeight(kg: Double): String = sharedDispWeight(kg, useImperial)

    val content = @Composable { padding: PaddingValues ->
        val reversedEntries = remember(entries.value) { entries.value.reversed() }
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = Gold, secondary = AccentCoral)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // Unit toggle + calendar nav — previously an inline single-domain
            // MonthCalendar toggled here; now routes to the unified Calendar
            // (Dashboard) which shows weight alongside every other tracker.
            item {
                WeightUnitToggleRow(useImperial = useImperial, onUnitChange = ::setUseImperial, onOpenCalendar = onOpenCalendar)
            }

            // Summary card
            summary.value?.let { s ->
                item {
                    WeightSummaryCard(
                        summary = s,
                        forecast = forecast.value,
                        goalWeightKg = goalWeightKg.value,
                        heightCm = heightCm.value,
                        loggingStreakDays = loggingStreakDays.value,
                        dispWeight = ::dispWeight,
                    )
                }
            }

            // Line chart — up to 30 most-recent entries as a Canvas polyline
            if (entries.value.size > 1) {
                item {
                    WeightTrendChart(
                        chartEntries = entries.value.takeLast(30),
                        goalKg = goalWeightKg.value,
                        fmt = fmt,
                        dispWeight = ::dispWeight,
                    )
                }
            }

            // New: weekly average comparison card — daily weigh-ins are noisy;
            // comparing this week's average to last week's gives a clearer trend.
            weeklyAvg.value?.let { (thisWeek, lastWeek) ->
                item {
                    WeeklyAverageCard(thisWeek = thisWeek, lastWeek = lastWeek, useImperial = useImperial)
                }
            }

            // Entries — improvement: per-row delta shows gain/loss vs previous weigh-in
            if (reversedEntries.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Rounded.Scale, stringResource(R.string.weight_empty_body),
                        ctaLabel = stringResource(R.string.weight_cd_add), onCta = { openAddDialog() },
                    )
                }
            }
            itemsIndexed(reversedEntries, key = { _, e -> e.id }) { idx, e ->
                // reversedEntries is newest-first (entries.value is DAO-ordered oldest->newest),
                // so the chronologically-older neighbor for a "change since last weigh-in" is the
                // NEXT element (idx + 1), not the previous one - reversedEntries[idx - 1] is a
                // newer entry, which inverted the sign shown here and left the newest row (idx 0)
                // with no delta at all since idx - 1 was never valid for it.
                val prev = reversedEntries.getOrNull(idx + 1)
                val delta = prev?.let { e.weightKg - it.weightKg }
                WeightEntryRow(
                    entry = e, delta = delta, useImperial = useImperial, fmt = fmt, dispWeight = ::dispWeight,
                    onEdit = {
                        // log()/upsertForDate replaces the existing row for this date rather than
                        // creating a duplicate, so reopening the same Add dialog prefilled with the
                        // entry's own values is a correct edit path - no separate update() needed.
                        kgText = if (useImperial) (e.weightKg * KG_TO_LB).formatDecimal(1) else e.weightKg.formatDecimal(1)
                        notesText = e.notes
                        entryDate = e.date
                        showAdd = true
                    },
                    onDelete = { deleteTarget = e.id },
                )
            }
            item { WeightReminderCard() }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(top = embeddedTopPadding, bottom = embeddedBottomPadding))
            FloatingActionButton(
                onClick = { openAddDialog() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = embeddedBottomPadding + Spacing.L, end = Spacing.L),
                containerColor = AccentCoral,
            ) { Icon(Icons.Rounded.Add, stringResource(R.string.common_add), tint = Color.Black) }
            ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.weight_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            actions = { IconButton(onClick = { openAddDialog() }) { Icon(Icons.Rounded.Add, stringResource(R.string.common_add), tint = AccentCoral) } },
            snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AddWeightDialog(
            kgText = kgText,
            onKgTextChange = { kgText = it },
            notesText = notesText,
            onNotesTextChange = { notesText = it },
            useImperial = useImperial,
            entryDate = entryDate,
            fmt = fmt,
            onPickDate = { showDatePicker = true },
            onDismiss = { showAdd = false; kgText = ""; notesText = ""; entryDate = LocalDate.now() },
            onSave = { kg ->
                viewModel.log(kg, notesText, entryDate)
                kgText = ""; notesText = ""; entryDate = LocalDate.now(); showAdd = false
            },
        )
    }

    if (showDatePicker) {
        WeightDatePickerDialog(
            entryDate = entryDate,
            onDateSelected = { entryDate = it },
            onDismiss = { showDatePicker = false },
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
