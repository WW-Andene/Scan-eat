package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.MetabolicResult
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

/**
 * Aggregate metabolic health score (0–100) composed from four sub-indicators:
 *  • BMI score  — 100 at 22.0, decays toward 18.5/25.0 cut-offs
 *  • Body-fat % — uses Navy BF% when available, Deurenberg otherwise
 *  • WHtR        — 100 below 0.4, 0 above 0.6 (Ashwell 2012 < 0.5 = low risk)
 *  • npRQ        — 100 at 0.707 (pure fat oxidation), 0 at 1.0 (pure carb)
 *
 * Sub-indicators with no data (e.g. WHtR when waist circumference missing)
 * are excluded from the average rather than dragging the score to 0.
 */
@Composable
fun MetabolicHealthScoreCard(met: MetabolicResult, profile: BiolismProfile) {
    val subScores = buildList {
        // BMI sub-score: peak at 22, linear decay to 0 at <16 or >35
        val bmiScore = when {
            met.bmi in 18.5..25.0 -> 100f - ((met.bmi - 22.0) / 3.0 * 20).toFloat().coerceIn(0f, 20f)
            met.bmi < 18.5        -> ((met.bmi - 16.0) / 2.5 * 100).toFloat().coerceIn(0f, 100f)
            else                  -> ((35.0 - met.bmi) / 10.0 * 100).toFloat().coerceIn(0f, 100f)
        }
        add("IMC" to bmiScore)

        // BF% sub-score: ideal ranges vary by sex; score 100 at midpoint, 0 at extremes
        val bf = met.navyBfPct ?: met.bfPct
        val isFemale = profile.sex == fr.scanneat.domain.engine.biolism.BiolismSex.FEMALE
        val bfScore = if (isFemale) {
            when {
                bf in 20.0..28.0 -> 100f
                bf < 20.0        -> (bf / 20.0 * 100).toFloat().coerceIn(0f, 100f)
                else             -> ((45.0 - bf) / 17.0 * 100).toFloat().coerceIn(0f, 100f)
            }
        } else {
            when {
                bf in 12.0..20.0 -> 100f
                bf < 12.0        -> (bf / 12.0 * 100).toFloat().coerceIn(0f, 100f)
                else             -> ((38.0 - bf) / 18.0 * 100).toFloat().coerceIn(0f, 100f)
            }
        }
        add("MG%" to bfScore)

        // WHtR sub-score: <0.4 = excellent, >0.6 = high risk (Ashwell 2012)
        met.whtr?.let { w ->
            val whtrScore = when {
                w <= 0.40 -> 100f
                w <= 0.50 -> ((0.60 - w) / 0.20 * 100).toFloat().coerceIn(0f, 100f)
                else      -> ((0.60 - w) / 0.10 * 100).toFloat().coerceIn(0f, 100f)
            }
            add("T/T" to whtrScore)
        }

        // npRQ sub-score: 0.707 = pure fat oxidation (ideal in fasted/keto state); 1.0 = pure carb
        val rqScore = ((1.0 - met.sub.npRq) / (1.0 - 0.707) * 100).toFloat().coerceIn(0f, 100f)
        add("QR" to rqScore)
    }

    val overallScore = subScores.map { it.second }.average().roundToInt()
    val scoreColor = when {
        overallScore >= 75 -> semanticGreen()
        overallScore >= 50 -> Gold
        else               -> semanticRed()
    }
    val scoreLabel = when {
        overallScore >= 75 -> "Excellent"
        overallScore >= 60 -> "Bon"
        overallScore >= 45 -> "Moyen"
        else               -> "À améliorer"
    }

    BioCard("Score santé métabolique", defaultOpen = true, badge = {
        Surface(shape = RoundedCornerShape(12.dp), color = scoreColor.copy(0.15f)) {
            Text(
                "$overallScore / 100",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scoreColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }) {
        LinearProgressIndicator(
            progress = { overallScore / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = scoreColor,
            trackColor = scoreColor.copy(0.12f),
        )
        Text(scoreLabel, style = MaterialTheme.typography.labelSmall, color = scoreColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.S))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            subScores.forEach { (label, score) ->
                SubScoreChip(label, score, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Spacing.XS))
        Text(
            "Indicateurs: IMC · masse grasse · tour de taille/taille · quotient respiratoire",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = OnSurface.copy(0.4f),
        )
    }
}

@Composable
private fun SubScoreChip(label: String, score: Float, modifier: Modifier = Modifier) {
    val color = when {
        score >= 75 -> semanticGreen()
        score >= 50 -> Gold
        else        -> semanticRed()
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.1f), modifier = modifier) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = OnSurface.copy(0.6f))
            Text("${score.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
