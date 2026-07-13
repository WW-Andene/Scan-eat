package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.PillarScore
import fr.scanneat.domain.model.ScoreAudit
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.semanticRed
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun PillarsSection(pillars: ScoreAudit.Pillars) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.result_pillars_title), style = MaterialTheme.typography.titleSmall,
            color = OnBackground, fontWeight = FontWeight.SemiBold)
        listOf(pillars.processing, pillars.nutritionalDensity, pillars.negativeNutrients,
               pillars.additiveRisk, pillars.ingredientIntegrity).forEach { PillarRow(it) }
    }
}

@Composable
private fun PillarRow(pillar: PillarScore) {
    val ratio = (pillar.score.toFloat() / pillar.max.toFloat()).coerceIn(0f, 1f)
    val color = when { ratio >= 0.7f -> semanticGreen(); ratio >= 0.4f -> semanticAmber(); else -> semanticRed() }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pillar.name, style = MaterialTheme.typography.labelMedium, color = OnBackground)
            Text(stringResource(R.string.result_pillar_score, pillar.score.toInt(), pillar.max), style = MaterialTheme.typography.labelMedium,
                color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(Spacing.XS))
        LinearProgressIndicator(
            progress   = { ratio },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color      = color,
            trackColor = SurfaceVariant,
        )
    }
}
