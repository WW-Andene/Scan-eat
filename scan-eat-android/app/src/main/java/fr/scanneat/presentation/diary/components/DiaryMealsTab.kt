package fr.scanneat.presentation.diary.components

import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Filter
import compose.icons.TablerIcons
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.dashboard.cards.NutrientBudgetCard
import fr.scanneat.presentation.diary.DiaryViewModel
import fr.scanneat.presentation.reminders.MealRemindersCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun MealsTab(
    viewModel: DiaryViewModel,
    snackbarHostState: SnackbarHostState,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.diary_deleted_message)
    val undoLabel = stringResource(R.string.diary_undo)
    val summary      = viewModel.summary.collectAsStateWithLifecycle()
    val selectedDate = viewModel.selectedDate.collectAsStateWithLifecycle()
    val isToday      = viewModel.isToday.collectAsStateWithLifecycle(initialValue = true)
    val dayNote      = viewModel.dayNote.collectAsStateWithLifecycle(initialValue = "")
    val language     = viewModel.language.collectAsStateWithLifecycle()
    val targets      = viewModel.targets.collectAsStateWithLifecycle()
    val goalTargets  = viewModel.goalTargets.collectAsStateWithLifecycle()
    val goalWeightKg = viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val diaryWarnings = viewModel.diaryWarnings.collectAsStateWithLifecycle()
    val diaryRecommended = viewModel.diaryRecommended.collectAsStateWithLifecycle()
    val useImperial  = viewModel.useImperial.collectAsStateWithLifecycle()
    // In-app language can differ from device locale - ofPattern() alone would
    // default to Locale.getDefault() and could show the day name in the wrong language.
    val dateFmt = remember(language.value) { DateTimeFormatter.ofPattern("EEE d MMM", Locale(language.value)) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var editTarget by remember { mutableStateOf<DiaryEntry?>(null) }
    var slotFilter by remember { mutableStateOf<MealSlot?>(null) }
    var showCalendar by remember { mutableStateOf(false) }
    var calendarMonth by remember(selectedDate.value) { mutableStateOf(java.time.YearMonth.from(selectedDate.value)) }
    // Fix 9: initialise to empty on date change; LaunchedEffect seeds the stored
    // note once per date and sets it — won't fire again while the user is typing because
    // selectedDate.value doesn't change until they navigate to a different day.
    // Seeds via a one-shot .first() (not an ongoing .collect) - an ongoing collector
    // here duplicated dayNote's own DataStore-observing pipeline, and could still
    // clobber in-progress unsaved typing if the note changed from elsewhere (e.g. a
    // backup import) while this screen was open.
    var noteText by remember(selectedDate.value) { mutableStateOf("") }
    LaunchedEffect(selectedDate.value) {
        noteText = viewModel.dayNote.first()
    }
    // Previously only saved via the small checkmark icon that appears once the
    // text differs from the stored note — navigating to another day via the
    // arrows/calendar/"today" button (the far more common way to move around)
    // discarded whatever was typed with zero warning, since noteText is
    // remember(selectedDate.value)-scoped and resets the instant the date changes.
    val saveNoteIfDirty = { if (noteText != dayNote.value) viewModel.saveNote(noteText) }

    val s = summary.value
    // Group by meal slot once per entries change; filter by selected slot chip.
    val bySlot = remember(s.entries) { s.entries.groupBy { it.mealSlot } }
    val filteredBySlot = remember(s.entries, slotFilter) {
        if (slotFilter == null) s.entries.groupBy { it.mealSlot }
        else mapOf(slotFilter!! to (bySlot[slotFilter] ?: emptyList()))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item { Spacer(Modifier.height(Spacing.XS)) }

        // Day navigation — meal logging is scoped to a single day; the other
        // Journal tabs (weight, water, activity, fasting) manage their own
        // date context internally, so this row is Meals-only.
        //
        // Wrapped in ScanEatCard: every other item in this list (the calendar
        // picker right below, MacroSummaryCard, DiaryKcalBreakdownCard,
        // DiaryProteinPerSlotCard) already gets the app's glass-card floating
        // treatment - this row alone used to render as a bare Row directly on
        // the screen's ambientGloom wash, squeezed right under the tab row's
        // own ScanEatCard with only a divider between them, reading as flat
        // chrome glued to the panel above it instead of floating like its
        // siblings (same class of fix the tab row itself already got - see
        // that ScanEatCard's own doc comment above).
        item {
            ScanEatCard(
                shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(horizontal = Spacing.S, vertical = Spacing.XS),
            ) {
                DiaryDayNavigationRow(
                    dateLabel = selectedDate.value.format(dateFmt),
                    isToday = isToday.value,
                    showCalendar = showCalendar,
                    onPrevDay = { saveNoteIfDirty(); viewModel.goToPreviousDay() },
                    onNextDay = { saveNoteIfDirty(); viewModel.goToNextDay() },
                    onToggleCalendar = { showCalendar = !showCalendar },
                    onCopyPreviousDay = { viewModel.copyPreviousDayMeals() },
                    onToday = { saveNoteIfDirty(); viewModel.goToToday() },
                )
            }
        }

        if (showCalendar) {
            item {
                ScanEatCard(
                    shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M),
                ) {
                    MonthCalendar(
                        month = calendarMonth,
                        selected = selectedDate.value,
                        locale = Locale(language.value),
                        onMonthChange = { calendarMonth = it },
                        onDayClick = { day -> saveNoteIfDirty(); viewModel.selectDate(day); showCalendar = false },
                    )
                }
            }
        }

        item { MacroSummaryCard(totals = s.totals, targets = targets.value, goalTargets = goalTargets.value, goalWeightKg = goalWeightKg.value, useImperial = useImperial.value) }

        // "Don't exceed" budgets (sat-fat/sugars/salt) - same card as Dashboard, so the
        // Journal's per-day view has the same feedback loop the Dashboard's today view does.
        targets.value?.let { t -> item { NutrientBudgetCard(totals = s.totals, targets = t) } }

        // Calorie intake breakdown bar — each meal slot's contribution as a colored segment
        if (s.entries.isNotEmpty()) {
            item { DiaryKcalBreakdownCard(totalKcal = s.totals.energyKcal, bySlot = bySlot) }
        }

        // Per-slot protein distribution — shows how protein is spread across meals.
        // Nothing in the current UI shows this; the totals card shows only the day sum.
        if (s.entries.isNotEmpty()) {
            item { DiaryProteinPerSlotCard(bySlot) }
        }

        // Meal slot filter chips
        if (s.entries.isNotEmpty()) {
            item { DiarySlotFilterRow(slotFilter = slotFilter, onFilterChange = { slotFilter = it }) }
        }

        item {
            DiaryNoteField(
                noteText = noteText,
                onNoteTextChange = { noteText = it },
                isDirty = noteText != dayNote.value,
                onSave = { viewModel.saveNote(noteText) },
            )
            Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.S))
        }
        if (s.entries.isEmpty()) {
            item { EmptyListState(TablerIcons.ClipboardList, stringResource(R.string.diary_empty_body)) }
        } else {
            MealSlot.values().forEach { slot ->
                val slotEntries = filteredBySlot[slot].orEmpty()
                if (slotEntries.isNotEmpty()) {
                    item {
                        Text(
                            slot.diaryLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentCoral.copy(0.8f),
                            modifier = Modifier.padding(top = Spacing.XS),
                        )
                    }
                    items(slotEntries, key = { it.id }) { entry ->
                        DiaryEntryCard(entry = entry, warning = diaryWarnings.value[entry.id], recommended = entry.id in diaryRecommended.value, onDelete = { deleteTarget = entry.id }, onEdit = { editTarget = entry })
                    }
                }
            }
            if (filteredBySlot.values.all { it.isEmpty() }) {
                item { EmptyListState(TablerIcons.Filter, stringResource(R.string.diary_filter_empty)) }
            }
        }
        item {
            Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.S))
        }
        item { MealRemindersCard() }
        item { Spacer(Modifier.height(Spacing.XXL)) }
    }

    // Delete confirmation — shared dialog, same as Weight/Templates/Recipes/
    // Activity/History instead of a one-off hand-rolled AlertDialog.
    deleteTarget?.let { id ->
        val target = s.entries.firstOrNull { it.id == id }
        DeleteConfirmDialog(
            itemName  = target?.productName,
            onConfirm = {
                viewModel.deleteEntry(id)
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

    // DiaryViewModel.updateEntry()/consumptionRepo.update() have always existed
    // but nothing in this screen called them - fixing a wrong portion size
    // required deleting and re-logging the entry from scratch.
    editTarget?.let { entry ->
        EditPortionDialog(
            entry = entry,
            onConfirm = { newPortionG, newMealSlot -> viewModel.updateEntry(entry.copy(portionG = newPortionG, mealSlot = newMealSlot)); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }
}
