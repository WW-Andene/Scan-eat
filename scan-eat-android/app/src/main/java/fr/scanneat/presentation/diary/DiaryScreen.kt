package fr.scanneat.presentation.diary

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.activity.ActivityScreen
import fr.scanneat.presentation.fasting.FastingScreen
import fr.scanneat.presentation.hydration.HydrationScreen
import fr.scanneat.presentation.medication.MedicationScreen
import fr.scanneat.presentation.reminders.RemindersCard
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

/**
 * Journal — the single home for every "log something today" task: meals
 * (this screen's original scope), weight, water, activity, and fasting.
 * These used to be scattered across Dashboard's launcher-tile grid, one tap
 * removed from a screen that was supposed to be a glance-and-go overview,
 * not a hub. Internal tab-row pattern mirrors BiolismScreen's.
 */
@Composable
fun DiaryScreen(
    viewModel: DiaryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
) {
    var activeTab by remember { mutableStateOf(DiaryTab.MEALS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diary_header), color = OnBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (!isTabRoot) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L).padding(bottom = Spacing.S),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DiaryTab.entries.forEach { tab ->
                    val isActive = tab == activeTab
                    Surface(
                        onClick = { activeTab = tab },
                        modifier = Modifier.weight(1f).semantics { role = Role.Tab; selected = isActive },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) AccentCoral.copy(0.15f) else OnBackground.copy(0.03f),
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
            ScanEatDivider()

            when (activeTab) {
                DiaryTab.MEALS    -> MealsTab(viewModel)
                DiaryTab.WEIGHT   -> WeightScreen(onBack = {}, embedded = true)
                DiaryTab.WATER    -> HydrationScreen(onBack = {}, embedded = true)
                DiaryTab.ACTIVITY -> ActivityScreen(onBack = {}, embedded = true)
                DiaryTab.FASTING  -> FastingScreen(onBack = {}, embedded = true)
                DiaryTab.TREATMENT -> MedicationScreen(onBack = {}, embedded = true)
            }
        }
    }
}

@Composable
private fun MealsTab(viewModel: DiaryViewModel) {
    val summary      = viewModel.summary.collectAsStateWithLifecycle()
    val selectedDate = viewModel.selectedDate.collectAsStateWithLifecycle()
    val isToday      = viewModel.isToday.collectAsStateWithLifecycle(initialValue = true)
    val dayNote      = viewModel.dayNote.collectAsStateWithLifecycle(initialValue = "")
    val language     = viewModel.language.collectAsStateWithLifecycle()
    // In-app language can differ from device locale - ofPattern() alone would
    // default to Locale.getDefault() and could show the day name in the wrong language.
    val dateFmt = remember(language.value) { DateTimeFormatter.ofPattern("EEE d MMM", Locale(language.value)) }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
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

    val s = summary.value
    // Group by meal slot once per entries change, not re-filtered 4x (once per
    // MealSlot) on every recomposition (e.g. every note keystroke).
    val bySlot = remember(s.entries) { s.entries.groupBy { it.mealSlot } }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        // Day navigation — meal logging is scoped to a single day; the other
        // Journal tabs (weight, water, activity, fasting) manage their own
        // date context internally, so this row is Meals-only.
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.goToPreviousDay() }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.diary_cd_prev_day), tint = OnBackground) }
                    Text(selectedDate.value.format(dateFmt), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    IconButton(onClick = { viewModel.goToNextDay() }, enabled = !isToday.value) {
                        Icon(Icons.Default.ChevronRight, stringResource(R.string.diary_cd_next_day), tint = if (!isToday.value) OnBackground else OnBackground.copy(0.3f))
                    }
                    IconButton(onClick = { showCalendar = !showCalendar }) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.diary_cd_calendar), tint = if (showCalendar) AccentCoral else OnBackground.copy(0.5f))
                    }
                }
                if (!isToday.value) {
                    TextButton(onClick = { viewModel.goToToday() }) { Text(stringResource(R.string.diary_today_button), color = AccentCoral, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }

        if (showCalendar) {
            item {
                Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Spacing.M)) {
                            MonthCalendar(
                                month = calendarMonth,
                                selected = selectedDate.value,
                                locale = Locale(language.value),
                                onMonthChange = { calendarMonth = it },
                                onDayClick = { day -> viewModel.selectDate(day); showCalendar = false },
                            )
                        }
                    }
                }
            }
        }

        item { MacroSummaryCard(totals = s.totals) }
        item {
            // Day note field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text(stringResource(R.string.diary_note_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S, bottom = Spacing.XS),
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    if (noteText != dayNote.value) {
                        IconButton(onClick = { viewModel.saveNote(noteText) }) {
                            Icon(Icons.Default.Check, stringResource(R.string.diary_cd_save_note), tint = AccentCoral)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = scanEatTextFieldColors(),
            )

            Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.S))
        }
        if (s.entries.isEmpty()) {
            item { EmptyListState(Icons.Default.RestaurantMenu, stringResource(R.string.diary_empty_body)) }
        } else {
            MealSlot.values().forEach { slot ->
                val slotEntries = bySlot[slot].orEmpty()
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
                        DiaryEntryCard(entry = entry, onDelete = { deleteTarget = entry.id })
                    }
                }
            }
        }
        // Reminders — meal, hydration, and weigh-in notifications live in Journal,
        // next to the logging they nudge you toward, not buried in Réglages.
        item {
            Text(stringResource(R.string.settings_section_reminders), style = MaterialTheme.typography.titleSmall, color = OnBackground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.S))
        }
        item { RemindersCard() }

        item { Spacer(Modifier.height(32.dp)) }
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
}

@Composable
private fun MacroSummaryCard(totals: ConsumedNutrition) {
    Box(Modifier.fillMaxWidth().glassSheen()) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text(stringResource(R.string.diary_totals_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroItem(stringResource(R.string.diary_macro_calories), "${totals.energyKcal.toInt()}", "kcal")
                    MacroItem(stringResource(R.string.diary_macro_protein), "${totals.proteinG.toInt()}", "g")
                    MacroItem(stringResource(R.string.diary_macro_carbs), "${totals.carbsG.toInt()}", "g")
                    MacroItem(stringResource(R.string.diary_macro_fat), "${totals.fatG.toInt()}", "g")
                }
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
    }
}

@Composable
private fun DiaryEntryCard(entry: DiaryEntry, onDelete: () -> Unit) {
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(12.dp))) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceVariant).padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.diary_entry_summary, entry.portionG.toInt(), entry.consumed.energyKcal.toInt()),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Diary keeps its own (fuller) wording for breakfast — distinct from the abbreviated shared label. */
@Composable
private fun MealSlot.diaryLabel(): String = when (this) {
    MealSlot.BREAKFAST -> stringResource(R.string.diary_meal_breakfast)
    MealSlot.LUNCH     -> stringResource(R.string.meal_lunch)
    MealSlot.SNACK     -> stringResource(R.string.meal_snack)
    MealSlot.DINNER    -> stringResource(R.string.meal_dinner)
}
