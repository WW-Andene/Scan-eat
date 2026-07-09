package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.cards.*
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.OnBackground

// Orchestrator only — each dashboard section lives in cards/*.kt, the
// shared FeatureTile helper in DashboardScreenComponents.kt. Was previously
// a single 453-line file with every section + FeatureTile inline.
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenWeight: () -> Unit = {},
    onOpenFasting: () -> Unit = {},
    onOpenHydration: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenTemplates: () -> Unit = {},
    onOpenMealPlan: () -> Unit = {},
    onOpenGrocery: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenCustomFoods: () -> Unit = {},
) {
    val state = viewModel.state.collectAsStateWithLifecycle()
    val s     = state.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title), color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // ---- Caloric balance — the hero card, streak badge overlapping its corner ----
            s.calorieBalance?.let { item { CalorieBalanceCard(it, streak = s.streak) } }

            // ---- Today's macros as rings ----
            item { TodayMacroCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Weekly bars ----
            s.weekly?.let { item { WeeklyBarsCard(rollup = it, targets = s.targets) } }

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
                item { GapCloserCard(gaps = s.gapSuggestions) }
            }

            // ---- Feature tiles ----
            item {
                Text(stringResource(R.string.dashboard_features_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile(Icons.Default.MonitorWeight, stringResource(R.string.dashboard_tile_weight),    Modifier.weight(1f), onClick = onOpenWeight)
                    FeatureTile(Icons.Default.Timer, stringResource(R.string.dashboard_tile_fasting),    Modifier.weight(1f), onClick = onOpenFasting)
                    FeatureTile(Icons.Default.Opacity, stringResource(R.string.dashboard_tile_water),       Modifier.weight(1f), onClick = onOpenHydration)
                    FeatureTile(Icons.Default.DirectionsRun, stringResource(R.string.dashboard_tile_activity), Modifier.weight(1f), onClick = onOpenActivity)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile(Icons.Default.RestaurantMenu, stringResource(R.string.dashboard_tile_recipes),  Modifier.weight(1f), onClick = onOpenRecipes)
                    FeatureTile(Icons.Default.ListAlt, stringResource(R.string.dashboard_tile_templates),   Modifier.weight(1f), onClick = onOpenTemplates)
                    FeatureTile(Icons.Default.CalendarMonth, stringResource(R.string.dashboard_tile_mealplan),  Modifier.weight(1f), onClick = onOpenMealPlan)
                    FeatureTile(Icons.Default.ShoppingCart, stringResource(R.string.dashboard_tile_grocery),   Modifier.weight(1f), onClick = onOpenGrocery)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile(Icons.Default.Person, stringResource(R.string.dashboard_tile_profile), Modifier.weight(1f), onClick = onOpenProfile)
                    Spacer(Modifier.weight(3f))
                }
            }

            // ---- Recent scans ----
            item {
                Text(
                    stringResource(R.string.dashboard_recent_scans_title),
                    style      = MaterialTheme.typography.titleSmall,
                    color      = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (s.recentScans.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.dashboard_recent_scans_empty), color = OnBackground.copy(0.4f))
                    }
                }
            } else {
                items(s.recentScans, key = { it.dbId }) { scan -> ScanHistoryCard(scan) }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
