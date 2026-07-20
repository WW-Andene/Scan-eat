package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
        shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(alpha = 0.1f),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.result_comparison_title), style = MaterialTheme.typography.labelMedium,
            color = AccentCoral, fontWeight = FontWeight.SemiBold)
        val delta = cmp.scoreDelta
        // Matches ScoreDeltaChip's three-way neutral/positive/negative split - delta == 0
        // previously fell into the >= 0 branch here and rendered as green "+0" ("improved"),
        // contradicting ScoreDeltaChip's gray "=" for the identical zero-change case shown
        // on the same screen.
        val dColor = when {
            delta > 0  -> semanticGreen()
            delta < 0  -> semanticRed()
            else       -> OnBackground.copy(0.5f)
        }
        val dSign  = if (delta > 0) "+" else ""
        Text("${cmp.prev.name} → ${cmp.next.name}",
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
        Text(stringResource(R.string.result_comparison_score, "$dSign$delta"),
            style = MaterialTheme.typography.bodyMedium, color = dColor, fontWeight = FontWeight.Bold)
        if (cmp.addedRedFlags.isNotEmpty())
            Text(stringResource(R.string.result_comparison_new_issues, cmp.addedRedFlags.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = semanticRed())
        if (cmp.removedRedFlags.isNotEmpty())
            Text(stringResource(R.string.result_comparison_resolved_issues, cmp.removedRedFlags.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = semanticGreen())
    }
}

@Composable
internal fun PairingsCard(pairings: List<String>) {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(alpha = 0.08f),
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
internal fun AlternativeCard(alternative: ScanResult, onOpen: (() -> Unit)? = null) {
    // findBetterAlternative only ever returns an already-persisted scan_history
    // row (see ScanRepository.findBetterAlternative), so dbId is always valid
    // here - but the card is reused defensively with onOpen omitted rather than
    // assuming every future caller can guarantee that.
    ScanEatCard(
        onClick = if (onOpen != null && alternative.dbId > 0) onOpen else null,
        shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(alpha = 0.1f),
        contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.result_alternative_title), style = MaterialTheme.typography.labelMedium,
            color = AccentCoral, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.result_alternative_item, alternative.product.name, alternative.audit.score, alternative.audit.grade.label),
            style = MaterialTheme.typography.bodyMedium, color = OnBackground)
    }
}

// A user who skipped onboarding's profile setup (or never opened Profile
// since) got the plain classic ScoreRing with zero indication anywhere that
// completing their profile would unlock a personalized score - and if they
// later did fill it in, the dual-score ring would just start appearing on
// their next visit with no explanation either. Shown whenever
// PersonalScoreResult.applicable is false, tappable straight to Profile.
@Composable
internal fun PersonalizationPromptCard(onOpenProfile: () -> Unit) {
    ScanEatCard(
        onClick = onOpenProfile,
        shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(alpha = 0.1f),
        contentPadding = PaddingValues(Spacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Default.Person, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Inline))
            Text(stringResource(R.string.result_personalization_prompt), style = MaterialTheme.typography.bodySmall,
                color = OnBackground, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun DietVetoBanner(reason: String?) {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(alpha = 0.15f),
        contentPadding = PaddingValues(Spacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Default.Block, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Inline))
            Text(reason ?: "", style = MaterialTheme.typography.bodySmall,
                color = OnBackground, modifier = Modifier.weight(1f))
        }
    }
}

// OCR/photo scans that fail to read the ingredient panel leave AllergenHit/veto
// checks with nothing to match against - AllergenWarningsCard/DietVetoBanner then
// simply don't render, which looks identical to "no allergens found" rather than
// "we couldn't check." Shown whenever the scan carries OcrParser's own
// "ingredients unreadable" warning, so the safety-relevant gap is explicit near
// the top of the screen instead of buried in the generic warnings section below.
@Composable
internal fun AllergenUnverifiedBanner() {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticAmber().copy(alpha = 0.15f),
        contentPadding = PaddingValues(Spacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Default.Warning, null, tint = semanticAmber(), modifier = Modifier.size(IconSize.Inline))
            Text(stringResource(R.string.result_allergens_unverified), style = MaterialTheme.typography.bodySmall,
                color = OnBackground, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun AllergenWarningsCard(allergens: List<AllergenHit>, language: String = "fr") {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticAmber().copy(alpha = 0.15f),
        contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Default.Warning, null, tint = semanticAmber(), modifier = Modifier.size(IconSize.Inline))
            Text(stringResource(R.string.result_allergens_title), style = MaterialTheme.typography.labelMedium,
                color = semanticAmber(), fontWeight = FontWeight.SemiBold)
        }
        allergens.forEach { hit ->
            val label = if (language == "en") hit.labelEn else hit.labelFr
            Text(stringResource(R.string.result_allergen_hit, label, hit.triggers.joinToString()),
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.85f))
        }
    }
}
