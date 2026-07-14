package fr.scanneat.presentation.dashboard

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.cards.*
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.EmptyListState
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

// Orchestrator only — each dashboard section lives in cards/*.kt, the
// shared FeatureTile helper in DashboardScreenComponents.kt. Was previously
// a single 453-line file with every section + FeatureTile inline.
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onOpenMealPlan: () -> Unit = {},
    onOpenGrocery: () -> Unit = {},
    onOpenCustomFoods: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenResult: (Long) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
) {
    val state    = viewModel.state.collectAsStateWithLifecycle()
    val s        = state.value
    val language = viewModel.language.collectAsStateWithLifecycle()
    val gapLoggedName = viewModel.gapLoggedName.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val gapLoggedMessage = gapLoggedName.value?.let { stringResource(R.string.dashboard_gap_logged, it) }
    LaunchedEffect(gapLoggedName.value) {
        gapLoggedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearGapLoggedMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // ---- Caloric balance — the hero card, streak badge overlapping its corner ----
            s.calorieBalance?.let { item { CalorieBalanceCard(it, streak = s.streak, longestStreak = s.longestStreak) } }

            // ---- Today's macros as rings ----
            item { TodayMacroCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Micronutrient progress (fiber, iron, calcium, vitD, B12) ----
            item { MicronutrientCard(totals = s.todayTotals) }

            // ---- Weekly bars ----
            s.weekly?.let { item { WeeklyBarsCard(rollup = it, targets = s.targets, language = language.value) } }

            // ---- Best / Worst day of the week ----
            s.weekly?.let { item { BestWorstDayCard(rollup = it, targets = s.targets, language = language.value) } }

            // ---- Monthly trend ----
            s.monthly?.let { item { MonthlyTrendCard(rollup = it, targets = s.targets) } }

            // ---- Week-over-week delta ----
            s.weekDelta?.let { delta ->
                if (delta.kcal != 0.0) item { WeekDeltaCard(delta = delta) }
            }

            // ---- Weight summary ----
            s.weightSummary?.let { ws ->
                item { WeightCard(summary = ws, forecast = s.weightForecast) }
            }

            // ---- Gap-closer suggestions ----
            if (s.gapSuggestions.isNotEmpty()) {
                item { GapCloserCard(gaps = s.gapSuggestions, onSuggestionClick = viewModel::logGapSuggestion) }
            }

            // ---- Chronic (recurring, multi-day) nutrient gaps ----
            if (s.chronicGaps.isNotEmpty()) {
                item { ChronicGapCard(gaps = s.chronicGaps, onSuggestionClick = viewModel::logGapSuggestion) }
            }

            // ---- Feature tiles — meal-planning tools only; daily logging tasks
            // (weight, fasting, water, activity) live in Journal now, and Profile's
            // canonical entry point is Journal's top bar, not a Dashboard tile. ----
            item {
                Text(stringResource(R.string.dashboard_features_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FeatureTile(Icons.Default.RestaurantMenu, stringResource(R.string.dashboard_tile_recipes),  Modifier.weight(1f), onClick = onOpenRecipes)
                    FeatureTile(Icons.AutoMirrored.Filled.ListAlt, stringResource(R.string.dashboard_tile_templates),   Modifier.weight(1f), onClick = onOpenTemplates)
                    FeatureTile(Icons.Default.CalendarMonth, stringResource(R.string.dashboard_tile_mealplan),  Modifier.weight(1f), onClick = onOpenMealPlan)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    FeatureTile(Icons.Default.ShoppingCart, stringResource(R.string.dashboard_tile_grocery),   Modifier.weight(1f), onClick = onOpenGrocery)
                    // onOpenCustomFoods had no call site anywhere in the composable -
                    // CustomFoodScreen was completely unreachable from any UI gesture.
                    FeatureTile(Icons.Default.Fastfood, stringResource(R.string.dashboard_tile_customfoods), Modifier.weight(1f), onClick = onOpenCustomFoods)
                    FeatureTile(Icons.Default.Star, stringResource(R.string.dashboard_tile_favorites), Modifier.weight(1f), onClick = onOpenFavorites)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Previously no single place showed everything logged on a given
                    // day - Diary/Weight/Activity/Hydration each embedded their own
                    // siloed single-domain mini-calendar with no cross-tracker view.
                    FeatureTile(Icons.Default.CalendarMonth, stringResource(R.string.dashboard_tile_calendar), Modifier.weight(1f), onClick = onOpenCalendar)
                    Spacer(Modifier.weight(2f))
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
                    if (s.recentScans.isNotEmpty()) {
                        TextButton(onClick = onOpenHistory) {
                            Text(stringResource(R.string.dashboard_view_all), color = AccentCoral)
                        }
                    }
                }
            }
            if (s.recentScans.isEmpty()) {
                item {
                    EmptyListState(Icons.Default.History, stringResource(R.string.dashboard_recent_scans_empty))
                }
            } else {
                items(s.recentScans, key = { it.dbId }) { scan -> ScanHistoryCard(scan, onItemClick = onOpenResult) }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
