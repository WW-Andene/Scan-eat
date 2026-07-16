package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.ScoreAudit
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed

// NutriScore/Eco-Score/NOVA are fully computed for every scan (ScoringEngine.kt
// populates audit.nutriscoreGrade/audit.eco, OffMapper/OcrParser populate
// product.novaClass) but were never rendered anywhere — the app already reads
// these exact metrics from Open Food Facts and computes them locally, yet a
// user comparing Scan'eat's own result against the label's printed NutriScore
// letter, or OFF's own listing, had no way to see them side by side. Each
// badge omits itself when the underlying value is absent (LLM/photo-sourced
// scans usually carry neither eco-score nor NutriScore).

@Composable
internal fun ScoreBadgesRow(audit: ScoreAudit, novaClass: NovaClass) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        audit.nutriscoreGrade?.let { grade ->
            LetterGradeChip(label = stringResource(R.string.result_badge_nutriscore), grade = grade)
        }
        audit.eco?.grade?.let { grade ->
            LetterGradeChip(label = stringResource(R.string.result_badge_ecoscore), grade = grade)
        }
        // NOVA is always present (defaults to ULTRA_PROCESSED when unknown, not
        // absent, unlike the two letter grades above) — always shown.
        NovaChip(novaClass)
    }
}

@Composable
private fun NovaChip(novaClass: NovaClass) {
    ScoreChip(label = stringResource(R.string.result_badge_nova, novaClass.value), color = tierColorForNova(novaClass))
}

@Composable
private fun LetterGradeChip(label: String, grade: String) {
    ScoreChip(label = "$label ${grade.uppercase()}", color = tierColorForLetter(grade))
}

@Composable
private fun ScoreChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
        )
    }
}

/** NutriScore/Eco-Score both use the same OFF a-e letter convention — a/b good, c mid, d/e poor. */
@Composable
private fun tierColorForLetter(grade: String) = when (grade.lowercase().firstOrNull()) {
    'a', 'b' -> semanticGreen()
    'c'      -> semanticAmber()
    else     -> semanticRed()
}

/** NOVA 1-4: higher = more processed = worse, same tiering convention as the letter grades above. */
@Composable
private fun tierColorForNova(novaClass: NovaClass) = when (novaClass) {
    NovaClass.UNPROCESSED, NovaClass.CULINARY -> semanticGreen()
    NovaClass.PROCESSED                       -> semanticAmber()
    NovaClass.ULTRA_PROCESSED                 -> semanticRed()
}
