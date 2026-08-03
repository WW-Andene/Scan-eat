package fr.scanneat.presentation.mealplan

import compose.icons.tablericons.Copy
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.presentation.mealplan.components.AssignSlotDialog
import fr.scanneat.presentation.mealplan.components.MEALS
import fr.scanneat.presentation.mealplan.components.MealPlanDayCard
import fr.scanneat.presentation.mealplan.components.WeeklyKcalBanner
import fr.scanneat.presentation.mealplan.components.mealLabel
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MealPlanScreen(
    viewModel: MealPlanViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    val plan = viewModel.weekPlan.collectAsStateWithLifecycle()
    val weekDates = viewModel.weekDates.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    val templates = viewModel.templates.collectAsStateWithLifecycle()
    val dayCalories = viewModel.dayCalories.collectAsStateWithLifecycle()
    val gapSuggestions = viewModel.gapSuggestions.collectAsStateWithLifecycle()
    val weeklyTotalKcal = viewModel.weeklyTotalKcal.collectAsStateWithLifecycle()
    val slotWarnings = viewModel.slotWarnings.collectAsStateWithLifecycle()
    // In-app language can differ from device locale - ofPattern() alone would
    // default to Locale.getDefault() and could show day names in the wrong language.
    val dayFmt = remember(language.value) { DateTimeFormatter.ofPattern("EEE d", Locale(language.value)) }
    // (date, meal) of the slot currently being assigned a recipe/template, or null.
    var assignTarget by remember { mutableStateOf<Pair<LocalDate, String>?>(null) }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val lastPrunedOrphanCount = viewModel.lastPrunedOrphanCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // "Duplicate week" was gated on weeklyTotalKcal > 0, which only counts assigned
    // Recipe/Template slots - a week planned entirely with free-text notes has
    // weeklyTotalKcal == 0, so the action disappeared even though there's a full
    // week of plan data to duplicate. Per-day duplicate/clear (below) correctly
    // gates on "any slot type"; this applies the same logic at the week level.
    val weekHasAnyPlan = weekDates.value.any { date -> MEALS.any { (plan.value[date] ?: DayPlan(date))[it] != null } }
    // logSlot previously failed completely silently - see MealPlanViewModel
    // .actionFailed's own comment.
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // MealPlanViewModel silently drops any plan slot pointing at a deleted
    // recipe/template (see its own doc comment) - previously invisible, this
    // surfaces the count as a one-shot snackbar instead of the cleanup
    // happening with zero user-visible trace.
    val prunedCount = lastPrunedOrphanCount.value
    val prunedMessage = pluralStringResource(R.plurals.mealplan_orphan_pruned, prunedCount, prunedCount)
    LaunchedEffect(prunedCount) {
        if (prunedCount > 0) {
            snackbarHostState.showSnackbar(prunedMessage)
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.mealplan_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        // Repeating a whole week's plan previously meant re-assigning all 28
        // slots (7 days x 4 meals) by hand - this duplicates the displayed
        // week onto the next 7 days in one tap.
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.MEAL_PLAN, onNavigate = onNavigateToPlanning)
            if (weekHasAnyPlan) {
                IconButton(onClick = { viewModel.duplicateWeek() }) {
                    Icon(TablerIcons.Copy, stringResource(R.string.mealplan_duplicate_week), tint = OnBackground)
                }
            }
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        // Was a plain LazyColumn with no scroll state - the list always opened
        // scrolled to the top (Monday), so on a Friday/Saturday a user had to
        // manually scroll past several already-past days to reach the one card
        // (bold-labeled "today" in MealPlanDayCard) that actually matters.
        val listState = rememberLazyListState()
        LaunchedEffect(weekDates.value) {
            val todayIndex = weekDates.value.indexOf(LocalDate.now())
            if (todayIndex > 0) listState.scrollToItem(todayIndex)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Teal)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }
            if (weeklyTotalKcal.value > 0) {
                item { WeeklyKcalBanner(weeklyTotalKcal.value) }
            }
            items(weekDates.value, key = { it.toEpochDay() }) { date ->
                MealPlanDayCard(
                    date = date,
                    dayPlan = plan.value[date] ?: DayPlan(date),
                    dayFmt = dayFmt,
                    kcal = dayCalories.value[date] ?: 0,
                    suggestion = gapSuggestions.value[date],
                    onDuplicateDay = { viewModel.duplicateDay(date) },
                    onClearDay = { viewModel.clearDay(date) },
                    onEditNote = { meal, text -> viewModel.setNote(date, meal, text) },
                    onClearSlot = { meal -> viewModel.clear(date, meal) },
                    onAssign = { meal -> assignTarget = date to meal },
                    onLogSlot = { meal, slot -> viewModel.logSlot(date, meal, slot) },
                    slotWarnings = slotWarnings.value,
                )
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    assignTarget?.let { (date, meal) ->
        AssignSlotDialog(
            mealLabel = mealLabel(meal),
            recipes   = recipes.value,
            templates = templates.value,
            onPickRecipe   = { viewModel.setRecipe(date, meal, it); assignTarget = null },
            onPickTemplate = { viewModel.setTemplate(date, meal, it); assignTarget = null },
            onDismiss = { assignTarget = null },
        )
    }
}
