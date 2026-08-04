package fr.scanneat.presentation.diary

import compose.icons.tablericons.Plus
import compose.icons.TablerIcons
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import fr.scanneat.R
import fr.scanneat.presentation.activity.ActivityScreen
import fr.scanneat.presentation.diary.components.AddDiaryEntryDialog
import fr.scanneat.presentation.diary.components.DiaryHeader
import fr.scanneat.presentation.diary.components.DiaryHeaderHeight
import fr.scanneat.presentation.diary.components.DiaryTab
import fr.scanneat.presentation.diary.components.DiaryTabSaver
import fr.scanneat.presentation.diary.components.MealsTab
import fr.scanneat.presentation.expenses.ExpensesScreen
import fr.scanneat.presentation.fasting.FastingScreen
import fr.scanneat.presentation.hydration.HydrationScreen
import fr.scanneat.presentation.medication.MedicationScreen
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.weight.WeightScreen

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

        DiaryHeader(
            hazeState = hazeState,
            isTabRoot = isTabRoot,
            onBack = onBack,
            activeTab = activeTab,
            onTabChange = { activeTab = it },
        )

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
