package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.scoring.PersonalScoreResult
import fr.scanneat.domain.engine.scoring.personalGrade
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.result.cards.*
import fr.scanneat.presentation.ui.theme.OnBackground

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

        comparisonResult?.let { ComparisonCard(it) }

        betterAlternative?.let { AlternativeCard(it) }

        if (pairings.isNotEmpty()) {
            PairingsCard(pairings)
        }

        if (personalScore?.veto == true) {
            DietVetoBanner(personalScore.dietReason)
        }

        val allergens = personalScore?.allergenHits.orEmpty()
        if (allergens.isNotEmpty()) {
            AllergenWarningsCard(allergens)
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
