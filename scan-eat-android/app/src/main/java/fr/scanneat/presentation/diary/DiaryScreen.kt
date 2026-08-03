package fr.scanneat.presentation.diary

import compose.icons.tablericons.Plus
import compose.icons.tablericons.Clock
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Filter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import fr.scanneat.presentation.expenses.ExpensesScreen
import fr.scanneat.presentation.fasting.FastingScreen
import fr.scanneat.presentation.hydration.HydrationScreen
import fr.scanneat.presentation.medication.MedicationScreen
import fr.scanneat.presentation.reminders.MealRemindersCard
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.weight.WeightScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DiaryTab(val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MEALS(R.string.diary_tab_meals, Icons.Rounded.RestaurantMenu),
    WEIGHT(R.string.diary_tab_weight, Icons.Rounded.Scale),
    WATER(R.string.diary_tab_water, Icons.Rounded.Opacity),
    ACTIVITY(R.string.diary_tab_activity, Icons.Rounded.FitnessCenter),
    FASTING(R.string.diary_tab_fasting, TablerIcons.Clock),
    TREATMENT(R.string.diary_tab_treatment, Icons.Rounded.Medication),
    EXPENSES(R.string.diary_tab_expenses, Icons.Rounded.Receipt),
}

/** Bundle doesn't natively round-trip an enum - process death (a low-memory
 *  background kill, the most common reason Android recreates an Activity)
 *  otherwise silently reset whichever Journal sub-tab (Weight/Water/Activity/
 *  Fasting/Treatment) the user was on back to Meals with no indication anything moved. */
private val DiaryTabSaver = Saver<DiaryTab, String>(save = { it.name }, restore = { DiaryTab.valueOf(it) })

// Taller than FloatingTopBarHeight (title row + tab row, not just a single
// title row) - not including the device's own status-bar inset, which is
// added separately via windowInsetsPadding below, same as FloatingTopBar/
// BiolismScreen's own equivalent constant.
private val DiaryHeaderHeight = 124.dp

/**
 * Journal — the single home for every "log something today" task: meals
 * (this screen's original scope), weight, water, activity, and fasting.
 * These used to be scattered across Dashboard's launcher-tile grid, one tap
 * removed from a screen that was supposed to be a glance-and-go overview,
 * not a hub. Internal tab-row pattern mirrors BiolismScreen's: title + tab
 * row merged into one floating glass header (own HazeState/hazeEffect)
 * instead of FloatingScreenScaffold's title-only bar with a second, flat,
 * non-blurred ScanEatCard underneath for the tabs - that arrangement let
 * scrolled content clip hard against the tab card's opaque edge instead of
 * fading/blurring under it like every other floating chrome in the app.
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
    // Dashboard's expenses recap card hands the target sub-tab back the same
    // SavedStateHandle "return a result" way Calendar's pendingSelectedDate
    // does above - opening Journal from there previously always landed on
    // Meals regardless of which card the user actually tapped.
    pendingTab: String? = null,
    onPendingTabConsumed: () -> Unit = {},
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

    LaunchedEffect(pendingTab) {
        val tab = pendingTab?.let { runCatching { DiaryTab.valueOf(it) }.getOrNull() }
        if (tab != null) activeTab = tab
        onPendingTabConsumed()
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
    val hazeState = remember { HazeState() }
    val bottomNavHazeState = LocalBottomNavHazeState.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomClearance = bottomInset + FloatingBottomNavHeight

    Box(
        Modifier
            .fillMaxSize()
            .hazeSource(bottomNavHazeState)
            .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold),
    ) {
        // No Modifier.padding here (previously top/bottom padded this whole Box) -
        // that would shrink this Box's own layout bounds, meaning each tab's
        // LazyColumn could never draw above/below those bounds even while
        // scrolling, permanently leaving nothing for the floating header/nav to
        // blur over. topPadding/bottomClearance below are threaded into each
        // tab's own LazyColumn contentPadding instead, so scrolled items visually
        // start below/above the chrome but can still scroll into those regions.
        val topPadding = topInset + DiaryHeaderHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            when (activeTab) {
                DiaryTab.MEALS    -> MealsTab(viewModel, snackbarHostState, topPadding = topPadding, bottomPadding = bottomClearance)
                DiaryTab.WEIGHT   -> WeightScreen(onBack = {}, embedded = true, embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                DiaryTab.WATER    -> HydrationScreen(onBack = {}, embedded = true, embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                DiaryTab.ACTIVITY -> ActivityScreen(onBack = {}, embedded = true, embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                DiaryTab.FASTING  -> FastingScreen(onBack = {}, embedded = true, embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                DiaryTab.TREATMENT -> MedicationScreen(onBack = {}, embedded = true, embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
                DiaryTab.EXPENSES -> ExpensesScreen(embeddedTopPadding = topPadding, embeddedBottomPadding = bottomClearance, onOpenCalendar = onOpenCalendar)
            }
        }

        // Merged floating glass header - title row + tab row in one card, both
        // registered against the same hazeState the content Box above feeds,
        // matching BiolismScreen's own internal header instead of a separate
        // flat, non-blurred ScanEatCard sitting underneath a title-only bar.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.L, vertical = Spacing.S)
                .glassSheen(edgeAlpha = 0.28f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral),
        ) {
            Surface(
                shape           = RoundedCornerShape(CardRadius.PROMINENT),
                color           = Color.Transparent,
                shadowElevation = 8.dp,
                modifier        = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius.PROMINENT))
                    .hazeEffect(state = hazeState, style = FrostedGlassStyle),
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.L).padding(top = Spacing.M, bottom = Spacing.S)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isTabRoot) {
                            IconButton(onClick = onBack, modifier = Modifier.padding(end = Spacing.XS)) {
                                Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                            }
                        }
                        Text(stringResource(R.string.diary_header), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    // Was a fixed Row with each tab forced to Modifier.weight(1f), then a
                    // horizontally-scrollable icon+label row - both still forced a
                    // horizontal scroll to reach Treatment/Expenses on most phone widths,
                    // one more scroll gesture on top of the day-picker/list scrolling this
                    // screen already asks for, and it turned out inactive icon-only tabs
                    // still didn't reliably fit either. Replaced with a single button
                    // showing the active tab, opening a popup menu (DropdownMenu) listing
                    // all seven - same pattern CollapsibleFilterBar now uses for filters,
                    // so there is no list to scroll or expand at all, on any screen width.
                    var tabMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            onClick = { tabMenuExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = AccentCoral.copy(0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentCoral.copy(0.4f)),
                        ) {
                            Row(
                                Modifier.heightIn(min = 48.dp).padding(horizontal = Spacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(activeTab.icon, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(20.dp))
                                Text(
                                    stringResource(activeTab.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentCoral, fontWeight = FontWeight.Bold,
                                )
                                Icon(TablerIcons.ChevronDown, contentDescription = null, tint = AccentCoral)
                            }
                        }
                        DropdownMenu(expanded = tabMenuExpanded, onDismissRequest = { tabMenuExpanded = false }) {
                            DiaryTab.entries.forEach { tab ->
                                val isActive = tab == activeTab
                                val label = stringResource(tab.labelRes)
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = { Icon(tab.icon, null, tint = if (isActive) AccentCoral else OnBackground.copy(0.6f)) },
                                    trailingIcon = { if (isActive) Icon(TablerIcons.Check, null, tint = AccentCoral) },
                                    onClick = { activeTab = tab; tabMenuExpanded = false },
                                )
                            }
                        }
                    }
                }
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
                Icon(TablerIcons.Plus, stringResource(R.string.diary_add_entry_title), tint = Color.Black)
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomClearance)) {
            ScanEatSnackbarHost(snackbarHostState)
        }
    }

    if (showAddEntry) {
        AddDiaryEntryDialog(viewModel = viewModel, onDismiss = { showAddEntry = false })
    }
}

@Composable
private fun MealsTab(
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
            item { EmptyListState(Icons.Rounded.RestaurantMenu, stringResource(R.string.diary_empty_body)) }
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

