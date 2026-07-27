package fr.scanneat.presentation.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.activity.ActivityScreen
import fr.scanneat.presentation.dashboard.cards.NutrientBudgetCard
import fr.scanneat.presentation.diary.components.AddDiaryEntryDialog
import fr.scanneat.presentation.diary.components.DiaryDayNavigationRow
import fr.scanneat.presentation.diary.components.DiaryEntryCard
import fr.scanneat.presentation.diary.components.DiaryKcalBreakdownCard
import fr.scanneat.presentation.diary.components.DiaryNoteField
import fr.scanneat.presentation.diary.components.DiaryProteinPerSlotCard
import fr.scanneat.presentation.diary.components.DiarySlotFilterRow
import fr.scanneat.presentation.diary.components.diaryLabel
import fr.scanneat.presentation.diary.components.EditPortionDialog
import fr.scanneat.presentation.diary.components.MacroSummaryCard
import fr.scanneat.presentation.fasting.FastingScreen
import fr.scanneat.presentation.hydration.HydrationScreen
import fr.scanneat.presentation.medication.MedicationScreen
import fr.scanneat.presentation.reminders.MealRemindersCard
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.weight.WeightScreen
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DiaryTab(val labelRes: Int) {
    MEALS(R.string.diary_tab_meals),
    WEIGHT(R.string.diary_tab_weight),
    WATER(R.string.diary_tab_water),
    ACTIVITY(R.string.diary_tab_activity),
    FASTING(R.string.diary_tab_fasting),
    TREATMENT(R.string.diary_tab_treatment),
}

/** Bundle doesn't natively round-trip an enum - process death (a low-memory
 *  background kill, the most common reason Android recreates an Activity)
 *  otherwise silently reset whichever Journal sub-tab (Weight/Water/Activity/
 *  Fasting/Treatment) the user was on back to Meals with no indication anything moved. */
private val DiaryTabSaver = Saver<DiaryTab, String>(save = { it.name }, restore = { DiaryTab.valueOf(it) })

/**
 * Journal — the single home for every "log something today" task: meals
 * (this screen's original scope), weight, water, activity, and fasting.
 * These used to be scattered across Dashboard's launcher-tile grid, one tap
 * removed from a screen that was supposed to be a glance-and-go overview,
 * not a hub. Internal tab-row pattern mirrors BiolismScreen's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
    onOpenCalendar: () -> Unit = {},
    // Calendar's "Open in Journal" action hands a date back through Navigation-Compose's
    // SavedStateHandle result pattern (see AppNavGraph.kt) - there was previously no way
    // to land on Journal already showing a specific past day picked from Calendar.
    pendingSelectedDate: String? = null,
    onPendingDateConsumed: () -> Unit = {},
) {
    var activeTab by rememberSaveable(stateSaver = DiaryTabSaver) { mutableStateOf(DiaryTab.MEALS) }
    var showAddEntry by remember { mutableStateOf(false) }

    LaunchedEffect(pendingSelectedDate) {
        val date = pendingSelectedDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        if (date != null) {
            viewModel.selectDate(date)
            activeTab = DiaryTab.MEALS
        }
        onPendingDateConsumed()
    }

    // Same pattern as WeightScreen - a failed Room write (delete/update/log/note) now
    // surfaces as a one-shot snackbar instead of going back to silent once the crash
    // was fixed.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.diary_header), color = OnBackground, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (!isTabRoot) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) }
            }
        },
        showBottomNavClearance = true,
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        val bottomClearance = padding.calculateBottomPadding()
        Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                // Wrapped in ScanEatCard: this file's own doc comment claims
                // this tab row "mirrors BiolismScreen's" — true of the layout
                // only, not the look. Biolism's equivalent tab row lives inside
                // its own floating glass header, while this one used to be a
                // plain flat Row directly on the screen's ambientGloom wash,
                // reading as flat chrome rather than floating like the rest of
                // the app's tab bars/cards.
                ScanEatCard(
                    modifier = Modifier.padding(horizontal = Spacing.L).padding(bottom = Spacing.S),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    contentPadding = PaddingValues(Spacing.XS),
                    accent = AccentCoral,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DiaryTab.entries.forEach { tab ->
                            val isActive = tab == activeTab
                            Surface(
                                onClick = { activeTab = tab },
                                modifier = Modifier.weight(1f).semantics { role = Role.Tab; selected = isActive },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) AccentCoral.copy(0.15f) else Color.Transparent,
                                border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, AccentCoral.copy(0.4f)) else null,
                            ) {
                                Text(
                                    stringResource(tab.labelRes),
                                    modifier = Modifier.padding(vertical = Spacing.S),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive) AccentCoral else OnBackground.copy(0.5f),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                ScanEatDivider()

                when (activeTab) {
                    DiaryTab.MEALS    -> MealsTab(viewModel, bottomPadding = bottomClearance)
                    DiaryTab.WEIGHT   -> WeightScreen(onBack = {}, embedded = true, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                    DiaryTab.WATER    -> HydrationScreen(onBack = {}, embedded = true, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                    DiaryTab.ACTIVITY -> ActivityScreen(onBack = {}, embedded = true, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                    DiaryTab.FASTING  -> FastingScreen(onBack = {}, embedded = true, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                    DiaryTab.TREATMENT -> MedicationScreen(onBack = {}, embedded = true, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                }
            }
            // Only Meals has a manual "search and log" entry point — the other tabs
            // (weight/water/activity/fasting) each already have their own add
            // affordance (a "+" button in their own embedded screen).
            if (activeTab == DiaryTab.MEALS) {
                FloatingActionButton(
                    onClick = { showAddEntry = true },
                    containerColor = AccentCoral,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomClearance + Spacing.L, end = Spacing.L),
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.diary_add_entry_title), tint = Color.Black)
                }
            }
        }
    }

    if (showAddEntry) {
        AddDiaryEntryDialog(viewModel = viewModel, onDismiss = { showAddEntry = false })
    }
}

@Composable
private fun MealsTab(viewModel: DiaryViewModel, bottomPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val summary      = viewModel.summary.collectAsStateWithLifecycle()
    val selectedDate = viewModel.selectedDate.collectAsStateWithLifecycle()
    val isToday      = viewModel.isToday.collectAsStateWithLifecycle(initialValue = true)
    val dayNote      = viewModel.dayNote.collectAsStateWithLifecycle(initialValue = "")
    val language     = viewModel.language.collectAsStateWithLifecycle()
    val targets      = viewModel.targets.collectAsStateWithLifecycle()
    val goalTargets  = viewModel.goalTargets.collectAsStateWithLifecycle()
    val goalWeightKg = viewModel.goalWeightKg.collectAsStateWithLifecycle()
    val diaryWarnings = viewModel.diaryWarnings.collectAsStateWithLifecycle()
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
        contentPadding = PaddingValues(bottom = bottomPadding),
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
            item { EmptyListState(Icons.Default.RestaurantMenu, stringResource(R.string.diary_empty_body)) }
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
                        DiaryEntryCard(entry = entry, warning = diaryWarnings.value[entry.id], onDelete = { deleteTarget = entry.id }, onEdit = { editTarget = entry })
                    }
                }
            }
            if (filteredBySlot.values.all { it.isEmpty() }) {
                item { EmptyListState(Icons.Default.FilterList, stringResource(R.string.diary_filter_empty)) }
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
        val name = s.entries.firstOrNull { it.id == id }?.productName
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = { viewModel.deleteEntry(id); deleteTarget = null },
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

