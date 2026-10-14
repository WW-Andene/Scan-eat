package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Check
import compose.icons.tablericons.QuestionMark
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.FamilyMemberVerdict
import fr.scanneat.domain.engine.scoring.FamilyStatus
import fr.scanneat.presentation.ui.theme.*

/**
 * Roadmap D10: one line per household profile (✓ / ⚠ / ? / ✕) so a parent
 * shopping for a child with an allergy sees everyone's compatibility in one
 * scan. Blocked profiles are listed first.
 */
@Composable
internal fun FamilyCheckCard(verdicts: List<FamilyMemberVerdict>, activeProfileId: String) {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(Spacing.L),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.result_family_title), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, color = OnBackground)
            verdicts.sortedByDescending { it.status.ordinal }.forEach { v -> FamilyMemberRow(v, v.profileId == activeProfileId) }
        }
    }
}

@Composable
private fun FamilyMemberRow(v: FamilyMemberVerdict, isActive: Boolean) {
    val (icon, tint) = when (v.status) {
        FamilyStatus.OK         -> TablerIcons.Check to semanticGreen()
        FamilyStatus.CAUTION    -> TablerIcons.AlertTriangle to semanticAmber()
        FamilyStatus.UNVERIFIED -> TablerIcons.QuestionMark to semanticAmber()
        FamilyStatus.BLOCKED    -> TablerIcons.Ban to semanticRed()
    }
    val detail = when (v.status) {
        FamilyStatus.OK         -> stringResource(R.string.result_family_ok)
        FamilyStatus.CAUTION    -> stringResource(R.string.result_family_traces, v.reason.orEmpty())
        FamilyStatus.UNVERIFIED -> stringResource(R.string.result_family_unverified)
        FamilyStatus.BLOCKED    -> v.reason ?: stringResource(R.string.result_family_blocked)
    }
    val name = v.profileName.ifBlank { stringResource(R.string.profile_switcher_unnamed) } +
        if (isActive) " " + stringResource(R.string.result_family_active_suffix) else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$name : $detail" },
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(IconSize.Inline))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = OnBackground)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.7f))
        }
    }
}
