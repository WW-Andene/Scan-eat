package fr.scanneat.presentation.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onLog: () -> Unit,
) {
    val state       = viewModel.state.collectAsStateWithLifecycle()
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet   by remember { mutableStateOf(false) }

    // Navigate to diary after successful log
    LaunchedEffect(state.value.logState) {
        if (state.value.logState is LogState.Done) {
            showSheet = false
            onLog()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score", color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = OnBackground)
                    }
                },
                actions = {
                    TextButton(onClick = { showSheet = true }) {
                        Text("Log it", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        val s = state.value
        if (s.scanResult == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGreen)
            }
        } else {
            ResultContent(
                scan             = s.scanResult,
                personalScore    = s.personalScore,
                comparisonResult = s.comparisonResult,
                pairings         = s.pairings,
                modifier         = Modifier.padding(padding),
            )
        }
    }

    // Log bottom sheet
    if (showSheet && state.value.scanResult != null) {
        LogSheet(
            product    = state.value.scanResult!!.product,
            sheetState = sheetState,
            onConfirm  = { g, slot -> viewModel.log(g, slot) },
            onDismiss  = { showSheet = false },
        )
    }

}

@Composable
private fun ResultContent(
    scan: ScanResult,
    personalScore: PersonalScoreResult?,
    comparisonResult: fr.scanneat.data.repository.scan.ComparisonResult? = null,
    pairings: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val audit = scan.audit
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Product name + source
        Text(audit.productName, style = MaterialTheme.typography.titleLarge, color = OnBackground, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(scan.product.category.key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
            Text("•", color = OnBackground.copy(0.3f))
            Text(scan.source.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
        }

        // Score ring(s)
        if (personalScore != null && personalScore.applicable) {
            DualScoreRing(
                classicScore  = audit.score,
                classicGrade  = audit.grade,
                personalScore = personalScore.personalScore,
                personalGrade = personalGrade(personalScore.personalScore),
                veto          = personalScore.veto,
            )
        } else {
            ScoreRing(score = audit.score, grade = audit.grade)
        }

        // Verdict
        Text(audit.verdict, style = MaterialTheme.typography.bodyLarge, color = OnBackground,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        // Comparison card (Fix 11)
        comparisonResult?.let { cmp ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                color    = AccentGreen.copy(alpha = 0.1f),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Comparaison A→B", style = MaterialTheme.typography.labelMedium,
                        color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    val delta = cmp.scoreDelta
                    val dColor = if (delta >= 0) FlagGreen else FlagRed
                    val dSign  = if (delta >= 0) "+" else ""
                    Text("${cmp.prev.name} → ${cmp.next.name}",
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                    Text("Score : $dSign$delta pts",
                        style = MaterialTheme.typography.bodyMedium, color = dColor, fontWeight = FontWeight.Bold)
                    if (cmp.addedRedFlags.isNotEmpty())
                        Text("Nouveaux problèmes : ${cmp.addedRedFlags.joinToString()}",
                            style = MaterialTheme.typography.bodySmall, color = FlagRed)
                    if (cmp.removedRedFlags.isNotEmpty())
                        Text("Problèmes résolus : ${cmp.removedRedFlags.joinToString()}",
                            style = MaterialTheme.typography.bodySmall, color = FlagGreen)
                }
            }
        }

        // Pairings card
        if (pairings.isNotEmpty()) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = AccentGreen.copy(alpha = 0.08f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Se marie bien avec", style = MaterialTheme.typography.labelMedium,
                        color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    pairings.forEach { pair ->
                        Text("· $pair", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                    }
                }
            }
        }

        // Diet veto banner
        if (personalScore?.veto == true) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                color    = FlagRed.copy(alpha = 0.15f),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Block, null, tint = FlagRed, modifier = Modifier.size(20.dp))
                    Text(personalScore.dietReason ?: "", style = MaterialTheme.typography.bodySmall,
                        color = OnBackground, modifier = Modifier.weight(1f))
                }
            }
        }

        // Allergen warnings
        val allergens = personalScore?.allergenHits.orEmpty()
        if (allergens.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                color    = AmberWarning.copy(alpha = 0.15f),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                        Text("Allergènes détectés", style = MaterialTheme.typography.labelMedium,
                            color = AmberWarning, fontWeight = FontWeight.SemiBold)
                    }
                    allergens.forEach { hit ->
                        Text("• ${hit.labelFr} (${hit.triggers.joinToString()})",
                            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.85f))
                    }
                }
            }
        }

        // Personal score adjustments
        if (personalScore != null && personalScore.applicable && personalScore.adjustments.isNotEmpty()) {
            AdjustmentsSection(adjustments = personalScore.adjustments)
        }

        // Classic flags
        if (audit.redFlags.isNotEmpty() || audit.greenFlags.isNotEmpty()) {
            FlagsSection(redFlags = audit.redFlags, greenFlags = audit.greenFlags)
        }

        // Pillars
        PillarsSection(pillars = audit.pillars)

        // Nutrition table
        NutritionTable(nutrition = scan.product.nutrition)

        // Warnings
        if (audit.warnings.isNotEmpty()) {
            WarningsSection(warnings = audit.warnings)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ============================================================================
// Composables
// ============================================================================

@Composable
private fun ScoreRing(score: Int, grade: Grade) {
    val color = gradeColor(grade)
    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress    = { score / 100f },
            modifier    = Modifier.size(160.dp),
            color       = color,
            strokeWidth = 12.dp,
            trackColor  = SurfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.label, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color)
            Text("$score / 100", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        }
    }
}

@Composable
private fun DualScoreRing(
    classicScore: Int, classicGrade: Grade,
    personalScore: Int, personalGrade: Grade,
    veto: Boolean,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Score classique", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { classicScore / 100f },
                    modifier    = Modifier.fillMaxSize(),
                    color       = gradeColor(classicGrade),
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(classicGrade.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = gradeColor(classicGrade))
                    Text("$classicScore", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                }
            }
        }
        Icon(Icons.Default.ArrowForward, null, tint = OnBackground.copy(0.3f), modifier = Modifier.size(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Score personnel", style = MaterialTheme.typography.labelSmall,
                color = if (veto) FlagRed else AccentGreen)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress    = { personalScore / 100f },
                    modifier    = Modifier.fillMaxSize(),
                    color       = if (veto) FlagRed else gradeColor(personalGrade),
                    strokeWidth = 8.dp,
                    trackColor  = SurfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (veto) "✗" else personalGrade.label, fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (veto) FlagRed else gradeColor(personalGrade))
                    Text("$personalScore", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                }
            }
        }
    }
}

@Composable
private fun AdjustmentsSection(adjustments: List<PersonalAdjustment>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ajustements personnels", style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        adjustments.filter { !it.veto }.forEach { adj ->
            val color = when {
                adj.points > 0 -> FlagGreen
                adj.points < 0 -> FlagRed
                else           -> OnBackground.copy(0.5f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (adj.points > 0) "+${adj.points.toInt()}" else "${adj.points.toInt()}",
                    style = MaterialTheme.typography.labelMedium, color = color,
                    fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp),
                )
                Text(adj.reason, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f),
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FlagsSection(redFlags: List<String>, greenFlags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        redFlags.forEach { FlagRow(it, true) }
        greenFlags.forEach { FlagRow(it, false) }
    }
}

@Composable
private fun FlagRow(text: String, isRed: Boolean) {
    val color = if (isRed) FlagRed else FlagGreen
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(if (isRed) Icons.Default.Warning else Icons.Default.CheckCircle, null,
            tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = OnBackground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PillarsSection(pillars: ScoreAudit.Pillars) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Détail des piliers", style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        listOf(pillars.processing, pillars.nutritionalDensity, pillars.negativeNutrients,
               pillars.additiveRisk, pillars.ingredientIntegrity).forEach { PillarRow(it) }
    }
}

@Composable
private fun PillarRow(pillar: PillarScore) {
    val ratio = (pillar.score.toFloat() / pillar.max.toFloat()).coerceIn(0f, 1f)
    val color = when { ratio >= 0.7f -> FlagGreen; ratio >= 0.4f -> AmberWarning; else -> FlagRed }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pillar.name, style = MaterialTheme.typography.labelMedium, color = OnBackground)
            Text("${pillar.score.toInt()}/${pillar.max}", style = MaterialTheme.typography.labelMedium,
                color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress   = { ratio },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = SurfaceVariant,
        )
    }
}

@Composable
private fun NutritionTable(nutrition: NutritionPer100g) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Nutrition / 100 g", style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NRow("Énergie", "${nutrition.energyKcal.toInt()} kcal")
        NRow("Lipides", "${nutrition.fatG} g")
        NRow("  dont saturés", "${nutrition.saturatedFatG} g")
        NRow("Glucides", "${nutrition.carbsG} g")
        NRow("  dont sucres", "${nutrition.sugarsG} g")
        NRow("Fibres", "${nutrition.fiberG} g")
        NRow("Protéines", "${nutrition.proteinG} g")
        NRow("Sel", "${nutrition.saltG} g")
        if (expanded) {
            nutrition.transFatG?.let { NRow("Acides gras trans", "${it} g") }
            nutrition.ironMg?.let { NRow("Fer", "${it} mg") }
            nutrition.calciumMg?.let { NRow("Calcium", "${it} mg") }
            nutrition.vitDUg?.let { NRow("Vitamine D", "${it} µg") }
            nutrition.b12Ug?.let { NRow("Vitamine B12", "${it} µg") }
            nutrition.vitCMg?.let { NRow("Vitamine C", "${it} mg") }
        }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Voir moins" else "Voir plus", color = AccentGreen, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.Medium)
    }
    HorizontalDivider(thickness = 0.5.dp, color = OnBackground.copy(0.08f))
}

@Composable
private fun WarningsSection(warnings: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(AmberWarning.copy(0.1f))
        .padding(12.dp)) {
        Text("Notes", style = MaterialTheme.typography.labelMedium,
            color = AmberWarning, fontWeight = FontWeight.SemiBold)
        warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f)) }
    }
}

