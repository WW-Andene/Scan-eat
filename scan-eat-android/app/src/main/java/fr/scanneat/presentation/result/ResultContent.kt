package fr.scanneat.presentation.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.PersonalScoreResult
import fr.scanneat.domain.engine.scoring.personalGrade
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.result.cards.*
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

// Assembles the sections that make up a scan result. Each section lives in
// cards/*.kt (one file per independent card/banner). Was previously inlined
// directly in ResultScreen.kt as part of a single 467-line file.
@Composable
internal fun ResultContent(
    scan: ScanResult,
    personalScore: PersonalScoreResult?,
    comparisonResult: fr.scanneat.data.repository.scan.ComparisonResult? = null,
    pairings: List<String> = emptyList(),
    betterAlternative: ScanResult? = null,
    language: String = "fr",
    scoreDelta: Int? = null,
    scoreHistory: List<Int> = emptyList(),
    onOpenResult: (Long) -> Unit = {},
    onOpenProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val audit = scan.audit
    Column(
        modifier = modifier
            .fillMaxSize()
            .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        // Product name + source
        Text(audit.productName, style = MaterialTheme.typography.titleLarge, color = OnBackground, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            Text(scan.product.category.key.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
            Text("•", color = OnBackground.copy(0.3f))
            Text(scan.source.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
        }

        // NutriScore / Eco-Score / NOVA — fully computed by the scoring engine
        // for every scan but previously never surfaced anywhere in this screen.
        ScoreBadgesRow(audit = audit, novaClass = scan.product.novaClass)

        // Score ring(s)
        if (personalScore != null && personalScore.applicable) {
            DualScoreRing(
                classicScore  = audit.score,
                classicGrade  = audit.grade,
                personalScore = personalScore.personalScore,
                personalGrade = personalGrade(personalScore.personalScore),
                veto          = personalScore.veto,
                scoreDelta    = scoreDelta,
            )
        } else {
            ScoreRing(score = audit.score, grade = audit.grade, scoreDelta = scoreDelta)
            PersonalizationPromptCard(onOpenProfile = onOpenProfile)
        }

        // New: product score history mini-sparkline (when ≥ 2 prior scans exist)
        if (scoreHistory.size >= 2) {
            ProductScoreHistoryRow(scores = scoreHistory, currentScore = audit.score)
        }

        // Verdict
        Text(audit.verdict, style = MaterialTheme.typography.bodyLarge, color = OnBackground,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        comparisonResult?.let { ComparisonCard(it) }

        betterAlternative?.let { AlternativeCard(it, onOpen = { onOpenResult(it.dbId) }) }

        if (pairings.isNotEmpty()) {
            PairingsCard(pairings)
        }

        if (personalScore?.veto == true) {
            DietVetoBanner(personalScore.dietReason)
        }

        val allergens = personalScore?.allergenHits.orEmpty()
        if (allergens.isNotEmpty()) {
            AllergenWarningsCard(allergens, language)
        }

        // Personalization active but this particular scan's ingredient panel
        // couldn't be OCR'd - allergenHits/veto are both silently empty in that
        // case (nothing to match against), which otherwise looks identical to a
        // genuine "no allergens found" result for a user with real allergens set.
        // Checked structurally (empty ingredient list) rather than by matching
        // scan.warnings' text, since that text is localized and this condition
        // must hold regardless of the app's display language.
        if (personalScore != null && personalScore.applicable && allergens.isEmpty() && personalScore.veto != true &&
            scan.product.ingredients.isEmpty()
        ) {
            AllergenUnverifiedBanner()
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

        // New: macro contribution row — shows how much of the daily reference values
        // (per 100g serving) this product covers. Previously the nutrition table showed
        // raw grams with no daily-context anchor, so users had no quick intuition of
        // whether 12g of fat is a lot or a little relative to their day.
        MacroContributionCard(nutrition = scan.product.nutrition)

        // Warnings — ScanResult.warnings (OCR-parse issues, and OFF-vs-LLM source-
        // conflict warnings like "Conflict: energy_kcal OFF=120 LLM=340") was
        // populated by ScanRepository but never read anywhere in the UI, so the
        // single most actionable warning class — "the label doesn't match Open
        // Food Facts" — was silently swallowed. Merged with audit.warnings here
        // rather than given its own section, since both are the same "heads up"
        // concept from the user's perspective.
        val allWarnings = (audit.warnings + scan.warnings).distinct()
        if (allWarnings.isNotEmpty()) {
            WarningsSection(warnings = allWarnings)
        }

        Spacer(Modifier.height(Spacing.XXL))
    }
}

/** Compact card showing how much of the EU daily reference values (per 100 g) this product covers. */
@Composable
private fun MacroContributionCard(nutrition: NutritionPer100g) {
    // positiveWhenHigh - protein/fiber are nutrients the rest of this app's own scoring
    // (NutritionalDensityPillar's protein/fiber bonuses) treats as GOOD when high; kcal/
    // carbs/fat are the opposite. This row previously colored all five identically
    // (semanticAmber() "caution" past 50% of the daily reference), so a product covering
    // half a day's protein or fiber in one 100g serving - a genuine positive - got the
    // exact same warning color as one covering half a day's fat or sugar.
    data class MacroRow(val label: String, val value: Double, val ref: Double, val positiveWhenHigh: Boolean)
    val rows = listOf(
        MacroRow(stringResource(R.string.result_macro_kcal),    nutrition.energyKcal, 2000.0, positiveWhenHigh = false),
        MacroRow(stringResource(R.string.result_macro_protein), nutrition.proteinG,     50.0, positiveWhenHigh = true),
        MacroRow(stringResource(R.string.result_macro_carbs),   nutrition.carbsG,      260.0, positiveWhenHigh = false),
        MacroRow(stringResource(R.string.result_macro_fat),     nutrition.fatG,         70.0, positiveWhenHigh = false),
        MacroRow(stringResource(R.string.result_macro_fiber),   nutrition.fiberG,       25.0, positiveWhenHigh = true),
    ).filter { it.value > 0.0 }
    if (rows.isEmpty()) return
    ScanEatCard(
        contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Text(stringResource(R.string.result_macro_contribution_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        rows.forEach { row ->
            val pct = (row.value / row.ref).coerceIn(0.0, 1.0).toFloat()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(row.label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.7f), modifier = Modifier.width(56.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = when {
                        pct >= 0.5f && row.positiveWhenHigh -> semanticGreen()
                        pct >= 0.5f -> semanticAmber()
                        else -> AccentCoral.copy(0.7f)
                    },
                    trackColor = OnSurface.copy(0.08f),
                )
                Text(
                    "${(pct * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurface.copy(0.5f),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
        // Bumped from 0.35f - a UI/UX audit flagged this explanatory note as
        // real informational content rendered too faint against the dark surface.
        Text(stringResource(R.string.result_macro_contribution_note), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}

/** Mini sparkline showing the last N scores for this product + the current score as the rightmost dot. */
@Composable
private fun ProductScoreHistoryRow(scores: List<Int>, currentScore: Int) {
    val allScores = scores + currentScore
    val minScore  = allScores.min().toFloat()
    val maxScore  = allScores.max().toFloat()
    val range     = (maxScore - minScore).coerceAtLeast(1f)
    val lineColor = AccentCoral

    ScanEatCard(
        contentPadding = PaddingValues(Spacing.M),
    ) {
        Text(
            stringResource(R.string.result_score_history_title),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface.copy(0.5f),
        )
        Spacer(Modifier.height(Spacing.XS))
        Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            val pts = allScores.mapIndexed { i, s ->
                val x = if (allScores.size == 1) size.width / 2f
                        else i / (allScores.size - 1f) * size.width
                val y = size.height - (s - minScore) / range * size.height
                Offset(x, y)
            }
            val path = Path().apply {
                pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
            }
            drawPath(path, color = lineColor.copy(0.4f), style = Stroke(width = 2.dp.toPx()))
            pts.forEachIndexed { i, pt ->
                val isLast = i == pts.lastIndex
                drawCircle(
                    color  = if (isLast) lineColor else lineColor.copy(0.5f),
                    radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                    center = pt,
                )
            }
        }
        Spacer(Modifier.height(Spacing.XS))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${scores.first()}", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = OnSurface.copy(0.4f))
            Text("$currentScore", style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = lineColor, fontWeight = FontWeight.Bold)
        }
    }
}
