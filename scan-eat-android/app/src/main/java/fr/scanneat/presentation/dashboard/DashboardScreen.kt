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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                title = { Text("Dashboard", color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = OnBackground)
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

            // ---- Today's macro ring ----
            item { TodayMacroCard(totals = s.todayTotals, targets = s.targets) }

            // ---- Streak ----
            if (s.streak > 0) {
                item {
                    StatChip(
                        icon  = Icons.Default.LocalFireDepartment,
                        color = Color(0xFFFF6B35),
                        label = "${s.streak} jour${if (s.streak > 1) "s" else ""} de suite",
                    )
                }
            }

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
                Text("Fonctionnalités", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile("⚖️", "Poids",    Modifier.weight(1f), onClick = onOpenWeight)
                    FeatureTile("⏱️", "Jeûne",    Modifier.weight(1f), onClick = onOpenFasting)
                    FeatureTile("💧", "Eau",       Modifier.weight(1f), onClick = onOpenHydration)
                    FeatureTile("🏃", "Activité", Modifier.weight(1f), onClick = onOpenActivity)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile("🍳", "Recettes",  Modifier.weight(1f), onClick = onOpenRecipes)
                    FeatureTile("📋", "Modèles",   Modifier.weight(1f), onClick = onOpenTemplates)
                    FeatureTile("📅", "Planning",  Modifier.weight(1f), onClick = onOpenMealPlan)
                    FeatureTile("🛒", "Courses",   Modifier.weight(1f), onClick = onOpenGrocery)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureTile("👤", "Mon Profil", Modifier.weight(1f), onClick = onOpenProfile)
                    Spacer(Modifier.weight(3f))
                }
            }

            // ---- Recent scans ----
            item {
                Text(
                    "Scans récents",
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
                        Text("Aucun scan pour l'instant.", color = OnBackground.copy(0.4f))
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
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Aujourd'hui", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                MacroItem("Calories", "${totals.energyKcal.roundToInt()}", "kcal",
                    targets?.kcal?.roundToInt())
                MacroItem("Protéines", "${totals.proteinG.roundToInt()}", "g",
                    targets?.proteinGTarget?.roundToInt())
                MacroItem("Glucides", "${totals.carbsG.roundToInt()}", "g", null)
                MacroItem("Lipides", "${totals.fatG.roundToInt()}", "g", null)
            }
            // Kcal progress bar
            targets?.let { t ->
                val pct = (totals.energyKcal / t.kcal).toFloat().coerceIn(0f, 1.2f)
                val color = when {
                    pct > 1.1f -> FlagRed
                    pct > 0.9f -> FlagGreen
                    else       -> AccentGreen
                }
                LinearProgressIndicator(
                    progress   = { pct.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = color,
                    trackColor = SurfaceVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun MacroItem(label: String, value: String, unit: String, target: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f))
        target?.let { Text("/ $it", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f), fontSize = 9.sp) }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(0.15f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WeeklyBarsCard(rollup: RollupResult, targets: DailyTargets?) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cette semaine", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text("${rollup.avg.kcal.roundToInt()} kcal/j moy.", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
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
            "${sign}${delta.kcal.roundToInt()} kcal vs semaine dernière",
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun WeightCard(summary: fr.scanneat.data.repository.health.WeightSummary, forecast: WeightForecast) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Poids", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${summary.latestKg} kg", style = MaterialTheme.typography.titleLarge, color = AccentGreen, fontWeight = FontWeight.Bold)
                    val deltaColor = when {
                        summary.deltaKg < 0 -> FlagGreen
                        summary.deltaKg > 0 -> FlagRed
                        else -> OnSurface.copy(0.5f)
                    }
                    val sign = if (summary.deltaKg >= 0) "+" else ""
                    Text("$sign${summary.deltaKg} kg (30j)", style = MaterialTheme.typography.labelSmall, color = deltaColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val trend = summary.trendKgPerWeek
                    val trendSign = if (trend >= 0) "+" else ""
                    Text("${trendSign}${trend} kg/sem.", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    if (forecast is WeightForecast.Ok) {
                        Text("Objectif dans ${forecast.days}j", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun GapCloserCard(gaps: List<GapEntry>) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Combler les écarts", style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            gaps.take(3).forEach { gap ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${gap.nutrient.replaceFirstChar { it.uppercase() }} : −${gap.deficit} manquant",
                        style = MaterialTheme.typography.labelMedium, color = AmberWarning,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        gap.suggestions.take(3).forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = AccentGreen.copy(0.15f),
                            ) {
                                Text(
                                    "${s.name} · ${s.grams} g",
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

@Composable
private fun ScanHistoryCard(scan: ScanResult) {
    val gradeColor = gradeColor(scan.audit.grade)
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
            Text("${scan.audit.score}/100 · ${scan.product.category.key.replace('_', ' ')}",
                style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
        }
        Text("${scan.audit.score}", style = MaterialTheme.typography.titleMedium, color = gradeColor, fontWeight = FontWeight.Bold)
    }
}

private fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.A_PLUS -> Color(0xFF4CAF50)
    Grade.A      -> Color(0xFF8BC34A)
    Grade.B      -> Color(0xFFCDDC39)
    Grade.C      -> Color(0xFFFF9800)
    Grade.D      -> Color(0xFFFF5722)
    Grade.F      -> Color(0xFFF44336)
}

@Composable
private fun FeatureTile(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
        }
    }
}
