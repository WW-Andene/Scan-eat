package fr.scanneat.presentation.dashboard

import compose.icons.tablericons.Star
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.History
import compose.icons.tablericons.Search
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Calendar
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
    onOpenExpenses: () -> Unit = {},
    onOpenScan: () -> Unit = {},
) {
    val state    = viewModel.state.collectAsStateWithLifecycle()
    val s        = state.value
    val language = viewModel.language.collectAsStateWithLifecycle()
    val otherTrackers = viewModel.otherTrackers.collectAsStateWithLifecycle()
    val recentScanWarnings = viewModel.recentScanWarnings.collectAsStateWithLifecycle()
    val useImperialWeight = viewModel.useImperialWeight.collectAsStateWithLifecycle()
    val gapLoggedName = viewModel.gapLoggedName.collectAsStateWithLifecycle()
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

            // ---- Today's macros as rings ----
            item { TodayMacroCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Water/Fasting/Treatment glance row - Dashboard previously showed
            // nutrition + weight only, with zero signal for the other three trackers
            // Journal already tracks (see DashboardViewModel.otherTrackers) ----
            item { OtherTrackersCard(otherTrackers.value) }

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

            // ---- Weight summary ----
            s.weightSummary?.let { ws ->
                item { WeightCard(summary = ws, forecast = s.weightForecast, useImperial = useImperialWeight.value) }
            }

            // ---- Expenses recap (self-contained, own hiltViewModel - see ExpensesRecapCard's doc comment) ----
            item { fr.scanneat.presentation.dashboard.cards.ExpensesRecapCard(onClick = onOpenExpenses) }

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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FeatureTile(TablerIcons.ClipboardList, stringResource(R.string.dashboard_tile_recipes),  Modifier.weight(1f), onClick = onOpenRecipes)
                    FeatureTile(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.dashboard_tile_templates),   Modifier.weight(1f), onClick = onOpenTemplates)
                    FeatureTile(TablerIcons.Calendar, stringResource(R.string.dashboard_tile_mealplan),  Modifier.weight(1f), onClick = onOpenMealPlan)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FeatureTile(TablerIcons.ShoppingCart, stringResource(R.string.dashboard_tile_grocery),   Modifier.weight(1f), onClick = onOpenGrocery)
                    // onOpenCustomFoods had no call site anywhere in the composable -
                    // CustomFoodScreen was completely unreachable from any UI gesture.
                    FeatureTile(Icons.Rounded.Fastfood, stringResource(R.string.dashboard_tile_customfoods), Modifier.weight(1f), onClick = onOpenCustomFoods)
                    FeatureTile(TablerIcons.Star, stringResource(R.string.dashboard_tile_favorites), Modifier.weight(1f), onClick = onOpenFavorites)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Previously no single place showed everything logged on a given
                    // day - Diary/Weight/Activity/Hydration each embedded their own
                    // siloed single-domain mini-calendar with no cross-tracker view.
                    // Was sharing TablerIcons.Calendar with Meal Plan's tile above -
                    // same icon, two different destinations in the same grid, so users
                    // couldn't tell them apart at a glance.
                    FeatureTile(Icons.Rounded.EventNote, stringResource(R.string.dashboard_tile_calendar), Modifier.weight(1f), onClick = onOpenCalendar)
                    // A UI/UX audit found ScanHistoryScreen (search/sort/favorite/
                    // delete) was reachable ONLY via the "View all" link below, itself
                    // gated on recentScans.isNotEmpty() - a brand-new user with zero
                    // scans had no way to open it at all. This tile is unconditional.
                    FeatureTile(TablerIcons.History, stringResource(R.string.dashboard_tile_history), Modifier.weight(1f), onClick = onOpenHistory)
                    // Previously an unused spacer slot - FOOD_DB's ~130 curated foods
                    // (plus the user's own custom foods) were only ever reachable
                    // through a 6-10-result Quick Add autocomplete dropdown, never as
                    // a real browsable/filterable search tool in its own right.
                    FeatureTile(TablerIcons.Search, stringResource(R.string.dashboard_tile_search), Modifier.weight(1f), onClick = onOpenFoodSearch)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FeatureTile(Icons.Rounded.Eco, stringResource(R.string.dashboard_tile_seasonal), Modifier.weight(1f), onClick = onOpenSeasonalProduce)
                    // Two empty weighted slots keep this tile the same size as every
                    // other 3-per-row tile above instead of stretching to full width.
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
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
