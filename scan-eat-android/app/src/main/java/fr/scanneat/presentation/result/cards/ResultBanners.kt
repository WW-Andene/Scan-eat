package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.scan.ComparisonResult
import fr.scanneat.domain.engine.scoring.AllergenHit
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.*

// Conditional alert/context banners shown above the pillar breakdown —
// grouped in one file since each is a small, single-purpose Surface.

@Composable
internal fun ComparisonCard(cmp: ComparisonResult) {
    ScanEatCard(
        shape = RoundedCornerShape(12.dp), color = AccentCoral.copy(alpha = 0.1f),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.result_comparison_title), style = MaterialTheme.typography.labelMedium,
            color = AccentCoral, fontWeight = FontWeight.SemiBold)
        val delta = cmp.scoreDelta
        val dColor = if (delta >= 0) FlagGreen else FlagRed
        val dSign  = if (delta >= 0) "+" else ""
        Text("${cmp.prev.name} → ${cmp.next.name}",
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        Text(stringResource(R.string.result_comparison_score, "$dSign$delta"),
            style = MaterialTheme.typography.bodyMedium, color = dColor, fontWeight = FontWeight.Bold)
        if (cmp.addedRedFlags.isNotEmpty())
            Text(stringResource(R.string.result_comparison_new_issues, cmp.addedRedFlags.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = FlagRed)
        if (cmp.removedRedFlags.isNotEmpty())
            Text(stringResource(R.string.result_comparison_resolved_issues, cmp.removedRedFlags.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = FlagGreen)
    }
}

@Composable
internal fun PairingsCard(pairings: List<String>) {
    ScanEatCard(
        shape = RoundedCornerShape(12.dp), color = AccentCoral.copy(alpha = 0.08f),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.result_pairings_title), style = MaterialTheme.typography.labelMedium,
            color = AccentCoral, fontWeight = FontWeight.SemiBold)
        pairings.forEach { pair ->
            Text(stringResource(R.string.result_pairing_item, pair), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
        }
    }
}

// The scanner's own history is the only "database" honest to query here — this
// is not a live nearby-product search, just "you already found something
// better yourself." §X R&D pass: Yuka's most-cited feature was a same-category
// better-score suggestion, which Scan'eat had no equivalent of at all.
@Composable
internal fun AlternativeCard(alternative: ScanResult) {
    ScanEatCard(
        shape = RoundedCornerShape(12.dp), color = AccentCoral.copy(alpha = 0.1f),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.result_alternative_title), style = MaterialTheme.typography.labelMedium,
            color = AccentCoral, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.result_alternative_item, alternative.product.name, alternative.audit.score, alternative.audit.grade.label),
            style = MaterialTheme.typography.bodyMedium, color = OnBackground)
    }
}

@Composable
internal fun DietVetoBanner(reason: String?) {
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(12.dp))) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            color    = FlagRed.copy(alpha = 0.15f),
        ) {
            Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Icon(Icons.Default.Block, null, tint = FlagRed, modifier = Modifier.size(20.dp))
                Text(reason ?: "", style = MaterialTheme.typography.bodySmall,
                    color = OnBackground, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun AllergenWarningsCard(allergens: List<AllergenHit>, language: String = "fr") {
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(12.dp))) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            color    = AmberWarning.copy(alpha = 0.15f),
        ) {
            Column(modifier = Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Icon(Icons.Default.Warning, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.result_allergens_title), style = MaterialTheme.typography.labelMedium,
                        color = AmberWarning, fontWeight = FontWeight.SemiBold)
                }
                allergens.forEach { hit ->
                    val label = if (language == "en") hit.labelEn else hit.labelFr
                    Text(stringResource(R.string.result_allergen_hit, label, hit.triggers.joinToString()),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.85f))
                }
            }
        }
    }
}
