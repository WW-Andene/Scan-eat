package fr.scanneat.presentation.dashboard

import compose.icons.tablericons.History
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.CrossTrackerInsight
import fr.scanneat.domain.engine.dashboard.GapSuggestion
import fr.scanneat.domain.engine.dashboard.InsightAgreement
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.dashboard.cards.*
import fr.scanneat.presentation.result.LogSheet
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.ConfirmDialog
import fr.scanneat.presentation.ui.theme.EmptyListState
import fr.scanneat.presentation.ui.theme.FloatingScreenScaffold
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.ScanEatSnackbarHost
import fr.scanneat.presentation.ui.theme.ambientGloom

// Orchestrator only — each dashboard section lives in cards/*.kt, the
// shared FeatureTile helper in DashboardScreenComponents.kt. Was previously
// a single 453-line file with every section + FeatureTile inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
    isTabRoot: Boolean = false,
    onOpenHistory: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onOpenMealPlan: () -> Unit = {},
    onOpenGrocery: () -> Unit = {},
    onOpenCustomFoods: () -> Unit = {},
    onOpenSeasonalProduce: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenResult: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenFoodSearch: () -> Unit = {},
    // Restructuration audit (§XI): generalized from the previous single-purpose
    // onOpenExpenses (which hardcoded "EXPENSES") - OtherTrackersCard's
    // Water/Fasting/Treatment glance stats now deep-link the same way
    // ExpensesRecapCard already did, just with their own DiaryTab name.
    onOpenDiaryTab: (String) -> Unit = {},
    onOpenScan: () -> Unit = {},
) {
    val state    = viewModel.state.collectAsStateWithLifecycle()
    val s        = state.value
    val language = viewModel.language.collectAsStateWithLifecycle()
    val otherTrackers = viewModel.otherTrackers.collectAsStateWithLifecycle()
    val recentScanWarnings = viewModel.recentScanWarnings.collectAsStateWithLifecycle()
    val weeklyScoreSummary = viewModel.weeklyScoreSummary.collectAsStateWithLifecycle()
    val useImperialWeight = viewModel.useImperialWeight.collectAsStateWithLifecycle()
    val gapLoggedName = viewModel.gapLoggedName.collectAsStateWithLifecycle()
    val weeklyValueScoreCounts = viewModel.weeklyValueScoreCounts.collectAsStateWithLifecycle()
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var loggingScan by remember { mutableStateOf<ScanResult?>(null) }
    // User-reported: tapping a GapCloser/ChronicGap suggestion chip logged it to the
    // diary immediately, with no way to back out of an accidental tap - every other
    // logging action in the app confirms first (LogSheet's own portion/meal-slot
    // step), this was the one exception.
    var pendingGapSuggestion by remember { mutableStateOf<GapSuggestion?>(null) }
    val gapLoggedMessage = gapLoggedName.value?.let { stringResource(R.string.dashboard_gap_logged, it) }
    LaunchedEffect(gapLoggedName.value) {
        gapLoggedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearGapLoggedMessage()
        }
    }
    // logGapSuggestion/logNeverLoggedScan previously failed completely silently -
    // see DashboardViewModel.actionFailed's own comment.
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.dashboard_title), color = OnBackground) },
        navigationIcon = {
            if (!isTabRoot) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                }
            }
        },
        hasNavigationIcon = !isTabRoot,
        showBottomNavClearance = isTabRoot,
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // ---- Caloric balance — the hero card, streak badge overlapping its corner ----
            s.calorieBalance?.let { item { CalorieBalanceCard(it, streak = s.streak, longestStreak = s.longestStreak) } }

            // ---- Weekly nutrition-quality score rollup — R&D roadmap: the app's
            // score/grade system (its differentiator vs. calorie-only trackers)
            // previously only appeared per-item at the very bottom of "Recent scans",
            // buried below ~15 other cards. Placed right under the hero so it's
            // visible without scrolling. Null (no scans this week) renders nothing. ----
            weeklyScoreSummary.value?.let { item { WeeklyScoreCard(it) } }

            // ---- Today's macros as rings ----
            item { TodayMacroCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Water/Fasting/Treatment glance row - Dashboard previously showed
            // nutrition + weight only, with zero signal for the other three trackers
            // Journal already tracks (see DashboardViewModel.otherTrackers) ----
            item {
                OtherTrackersCard(
                    otherTrackers.value,
                    onOpenHydration  = { onOpenDiaryTab("WATER") },
                    onOpenFasting    = { onOpenDiaryTab("FASTING") },
                    onOpenMedication = { onOpenDiaryTab("TREATMENT") },
                )
            }

            // ---- Micronutrient progress (fiber, iron, calcium, vitD, B12) ----
            item { MicronutrientCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Daily "don't exceed" budgets (sat-fat/sugars/salt) - DailyTargets already
            // computes all three but nothing on Dashboard/Diary ever showed them ----
            s.targets?.let { t -> item { NutrientBudgetCard(totals = s.todayTotals, targets = t) } }

            // ---- Weekly bars ----
            s.weekly?.let { item { WeeklyBarsCard(rollup = it, targets = s.targets, language = language.value) } }

            // ---- Best / Worst day of the week ----
            s.weekly?.let { item { BestWorstDayCard(rollup = it, targets = s.targets, language = language.value) } }

            // ---- Monthly trend ----
            s.monthly?.let { item { MonthlyTrendCard(rollup = it, targets = s.targets, language = language.value, delta = s.monthDelta) } }

            // ---- Week-over-week delta ----
            s.weekDelta?.let { delta ->
                if (delta.kcal != 0.0) item { WeekDeltaCard(delta = delta) }
            }

            // ---- Cross-tracker insight: does this week's intake actually agree
            // with the real weight-trend direction? INCONCLUSIVE means neither
            // signal is strong enough yet to say anything useful. ----
            (s.crossInsight as? CrossTrackerInsight.WeightVsIntake)?.let { insight ->
                if (insight.agreement != InsightAgreement.INCONCLUSIVE) {
                    item { WeeklyInsightCard(insight, useImperial = useImperialWeight.value) }
                }
            }

            if (weeklyValueScoreCounts.value.isNotEmpty()) {
                item { WeeklyValueScoreCard(weeklyValueScoreCounts.value) }
            }

            // ---- Weight summary ----
            s.weightSummary?.let { ws ->
                item { WeightCard(summary = ws, forecast = s.weightForecast, useImperial = useImperialWeight.value) }
            }

            // ---- Expenses recap (self-contained, own hiltViewModel - see ExpensesRecapCard's doc comment) ----
            item { fr.scanneat.presentation.dashboard.cards.ExpensesRecapCard(onClick = { onOpenDiaryTab("EXPENSES") }) }

            // ---- Gap-closer suggestions ----
            if (s.gapSuggestions.isNotEmpty()) {
                item { GapCloserCard(gaps = s.gapSuggestions, onSuggestionClick = { pendingGapSuggestion = it }) }
            }

            // ---- Chronic (recurring, multi-day) nutrient gaps ----
            if (s.chronicGaps.isNotEmpty()) {
                item { ChronicGapCard(gaps = s.chronicGaps, onSuggestionClick = { pendingGapSuggestion = it }) }
            }

            // ---- Scanned today but never logged ----
            if (s.neverLoggedScans.isNotEmpty()) {
                item { NeverLoggedScansCard(scans = s.neverLoggedScans, onLogClick = { loggingScan = it }) }
            }

            // ---- Feature tiles — meal-planning tools only; daily logging tasks
            // (weight, fasting, water, activity) live in Journal now, and Profile's
            // canonical entry point is Journal's top bar, not a Dashboard tile. ----
            item {
                // User-reported: this Text added its own extra Modifier.padding(horizontal
                // = Spacing.L) on top of the LazyColumn's own Spacing.L, so "Fonctionnalités"
                // read further right than "Scan récent" below it - the only other bare
                // (non-card) section title on this screen, which correctly inherits just
                // the LazyColumn's single Spacing.L. Removed to match.
                Text(stringResource(R.string.dashboard_features_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                DashboardFeatureTilesGrid(
                    onOpenRecipes = onOpenRecipes,
                    onOpenTemplates = onOpenTemplates,
                    onOpenMealPlan = onOpenMealPlan,
                    onOpenGrocery = onOpenGrocery,
                    onOpenCustomFoods = onOpenCustomFoods,
                    onOpenFavorites = onOpenFavorites,
                    onOpenCalendar = onOpenCalendar,
                    onOpenHistory = onOpenHistory,
                    onOpenFoodSearch = onOpenFoodSearch,
                    onOpenSeasonalProduce = onOpenSeasonalProduce,
                )
            }

            // ---- Recent scans ----
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.dashboard_recent_scans_title),
                        style      = MaterialTheme.typography.titleSmall,
                        color      = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // onOpenHistory had no call site anywhere in the composable -
                    // ScanHistoryScreen (search/sort/favorite/delete) was completely
                    // unreachable from any UI gesture.
                    // Was gated on recentScans.isNotEmpty(), so this section's own
                    // entry into full history appeared/disappeared depending on data
                    // state - a stable tap target is clearer than one that comes and goes.
                    TextButton(onClick = onOpenHistory) {
                        Text(stringResource(R.string.dashboard_view_all), color = AccentCoral)
                    }
                }
            }
            if (s.recentScans.isEmpty()) {
                item {
                    // Previously a dead end for a brand-new user - Scan is only one
                    // tap away via the bottom nav, but this empty state gave no hint
                    // of that, unlike every other first-run empty state in the app.
                    EmptyListState(
                        TablerIcons.History, stringResource(R.string.dashboard_recent_scans_empty),
                        ctaLabel = stringResource(R.string.dashboard_recent_scans_empty_cta), onCta = onOpenScan,
                    )
                }
            } else {
                // User-requested cap - the rest is one tap away via "View all" /
                // onOpenHistory above, this section doesn't need to double as a
                // second full history list on the same screen.
                items(s.recentScans.take(5), key = { it.dbId }) { scan ->
                    ScanHistoryCard(scan, warning = recentScanWarnings.value[scan.dbId], onItemClick = onOpenResult)
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    // Portion/meal-slot picker for NeverLoggedScansCard's "Log it" action - same
    // LogSheet every other log action in the app reuses.
    loggingScan?.let { scan ->
        LogSheet(
            product    = scan.product,
            onConfirm  = { portionG, mealSlot ->
                viewModel.logNeverLoggedScan(scan, portionG, mealSlot)
                loggingScan = null
            },
            onDismiss  = { loggingScan = null },
        )
    }

    pendingGapSuggestion?.let { suggestion ->
        ConfirmDialog(
            title = stringResource(R.string.dashboard_gap_confirm_title),
            body  = stringResource(R.string.dashboard_gap_confirm_body, suggestion.name, suggestion.grams),
            confirmLabel = stringResource(R.string.common_log),
            confirmColor = AccentCoral,
            onConfirm = {
                viewModel.logGapSuggestion(suggestion)
                pendingGapSuggestion = null
            },
            onDismiss = { pendingGapSuggestion = null },
        )
    }
}
