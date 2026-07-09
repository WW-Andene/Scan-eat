package fr.scanneat.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

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
                items(s.recentScans) { scan -> ScanHistoryCard(scan) }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

}

// ============================================================================
// Sub-composables
// ============================================================================

@Composable
private fun TodayMacroCard(totals: ConsumedNutrition, targets: DailyTargets?) {
    Box(modifier = Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(18.dp))) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dashboard_today_label), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                val kcalPct = targets?.let { (totals.energyKcal / it.kcal).toFloat() }
                val kcalColor = when {
                    kcalPct == null  -> AccentGreen
                    kcalPct > 1.1f   -> FlagRed
                    kcalPct > 0.9f   -> FlagGreen
                    else             -> AccentGreen
                }
                val protPct = targets?.proteinGTarget?.takeIf { it > 0 }?.let { (totals.proteinG / it).toFloat() }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    MacroRing(stringResource(R.string.diary_macro_calories), totals.energyKcal.roundToInt(), "kcal", kcalPct, kcalColor)
                    MacroRing(stringResource(R.string.diary_macro_protein), totals.proteinG.roundToInt(), "g", protPct, AccentGreen)
                    MacroRing(stringResource(R.string.diary_macro_carbs), totals.carbsG.roundToInt(), "g", null, AccentGreen)
                    MacroRing(stringResource(R.string.diary_macro_fat), totals.fatG.roundToInt(), "g", null, AccentGreen)
                }
            }
        }
    }
}

@Composable
private fun MacroRing(label: String, value: Int, unit: String, pct: Float?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress   = { pct?.coerceIn(0f, 1f) ?: 1f },
                modifier   = Modifier.fillMaxSize(),
                color      = if (pct != null) color else OnSurface.copy(alpha = 0.12f),
                strokeWidth = 4.dp,
                trackColor = OnSurface.copy(alpha = 0.12f),
            )
            Text(value.toString(), style = MaterialTheme.typography.labelMedium, color = OnBackground, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}

@Composable
private fun CalorieBalanceCard(balance: CalorieBalance, streak: Int) {
    val isSurplus = balance.net > 200
    val isDeficit = balance.net < -50
    val balColor = if (isSurplus) FlagRed else if (isDeficit) AccentGreen else AmberWarning
    val statusRes = if (isSurplus) R.string.dashboard_calorie_surplus
        else if (isDeficit) R.string.dashboard_calorie_deficit
        else R.string.dashboard_calorie_balanced
    val sourceRes = if (balance.tdeeFromBiolism) R.string.dashboard_calorie_source_biolism else R.string.dashboard_calorie_source_profile

    Box(modifier = Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(20.dp))) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = if (streak > 0) 40.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.dashboard_calorie_balance_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(sourceRes), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                }

                Text(
                    (if (balance.net >= 0) "+" else "") + "${balance.net.roundToInt()} kcal",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp), color = balColor, fontWeight = FontWeight.Black,
                )
                Text(stringResource(statusRes), style = MaterialTheme.typography.labelSmall, color = balColor, fontWeight = FontWeight.SemiBold)

                val pct = (balance.kcalIn / balance.tdee).toFloat().coerceIn(0f, 1.2f)
                LinearProgressIndicator(
                    progress   = { pct.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (isSurplus) FlagRed else AccentGreen,
                    trackColor = SurfaceVariant.copy(alpha = 0.3f),
                )
                Text(
                    stringResource(R.string.dashboard_calorie_in_out, balance.kcalIn.roundToInt(), balance.tdee.roundToInt()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                )
            }
        }

        if (streak > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .size(46.dp),
                shape = RoundedCornerShape(50),
                color = AccentGreen,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$streak", style = MaterialTheme.typography.labelLarge, color = Color.Black, fontWeight = FontWeight.Black)
                        Text(
                            pluralStringResource(R.plurals.dashboard_streak_unit, streak),
                            style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(0.7f), fontSize = 7.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarsCard(rollup: RollupResult, targets: DailyTargets?) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dashboard_week_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.dashboard_week_avg_kcal, rollup.avg.kcal.roundToInt()), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
            }
            val peak = (listOf(targets?.kcal ?: 0.0) + rollup.days.map { it.kcal }).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(
                modifier              = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.Bottom,
            ) {
                rollup.days.forEach { day ->
                    val frac = (day.kcal / peak).toFloat().coerceIn(0f, 1f)
                    val isToday = day.date == java.time.LocalDate.now()
                    val isOver  = targets != null && day.kcal > targets.kcal
                    val color   = when {
                        day.count == 0 -> OnSurface.copy(0.1f)
                        isOver         -> FlagRed.copy(0.7f)
                        else           -> AccentGreen.copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(if (day.count == 0) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            // Day labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rollup.days.forEach { day ->
                    Text(
                        day.date.dayOfWeek.name.take(2),
                        modifier  = Modifier.weight(1f),
                        style     = MaterialTheme.typography.labelSmall,
                        color     = if (day.date == java.time.LocalDate.now()) AccentGreen else OnSurface.copy(0.4f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize  = 9.sp,
                    )
                }
            }
        }
    }
  }
}

@Composable
private fun WeekDeltaCard(delta: WeekOverWeekDelta) {
    val sign = if (delta.kcal >= 0) "+" else ""
    val color = if (delta.kcal <= 0) FlagGreen else AmberWarning
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(0.1f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (delta.kcal >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
            null, tint = color, modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.dashboard_week_delta, "$sign${delta.kcal.roundToInt()}"),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun WeightCard(summary: fr.scanneat.data.repository.health.WeightSummary, forecast: WeightForecast) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.weight_kg, summary.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                    val deltaColor = when {
                        summary.deltaKg < 0 -> FlagGreen
                        summary.deltaKg > 0 -> FlagRed
                        else -> OnSurface.copy(0.5f)
                    }
                    val sign = if (summary.deltaKg >= 0) "+" else ""
                    Text(stringResource(R.string.weight_delta_kg, "$sign${summary.deltaKg}"), style = MaterialTheme.typography.labelSmall, color = deltaColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val trend = summary.trendKgPerWeek
                    val trendSign = if (trend >= 0) "+" else ""
                    Text(stringResource(R.string.weight_trend_kg_week, "$trendSign$trend"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    if (forecast is WeightForecast.Ok) {
                        Text(stringResource(R.string.weight_goal_forecast, forecast.days), style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                    }
                }
            }
        }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GapCloserCard(gaps: List<GapEntry>) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.dashboard_gap_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            gaps.take(3).forEach { gap ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.dashboard_gap_entry, gap.nutrient.replaceFirstChar { it.uppercase() }, gap.deficit.toString()),
                        style = MaterialTheme.typography.labelMedium, color = AmberWarning,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        gap.suggestions.take(3).forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AccentGreen.copy(0.15f),
                            ) {
                                Text(
                                    stringResource(R.string.dashboard_gap_suggestion, s.name, s.grams),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style    = MaterialTheme.typography.labelSmall,
                                    color    = AccentGreen,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
  }
}

@Composable
private fun ScanHistoryCard(scan: ScanResult) {
    val gradeColor = gradeColor(scan.audit.grade)
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(12.dp))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant)
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = gradeColor.copy(0.2f)) {
                Text(
                    scan.audit.grade.label,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = gradeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(stringResource(R.string.history_score_category, scan.audit.score, scan.product.category.key.replace('_', ' ')),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
            Text("${scan.audit.score}", style = MaterialTheme.typography.titleMedium, color = gradeColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FeatureTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, null, tint = AccentGreen, modifier = Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
        }
    }
}
