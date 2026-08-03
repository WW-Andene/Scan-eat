package fr.scanneat.presentation.history.components

import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.AlertTriangle
import compose.icons.TablerIcons
import compose.icons.tablericons.X
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ScanHistoryRow(scan: ScanResult, warning: String?, onOpen: () -> Unit, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    val gradeColor = gradeColor(scan.audit.grade)
    val haptics = LocalHapticFeedback.current
    // Appended rather than a new formatted string resource — the warning text
    // itself already comes pre-localized out of checkUserAllergens()/checkDiet().
    // Without this, a TalkBack user would never hear about the same allergen/
    // diet conflict a sighted user now sees on this row (visual-only otherwise).
    val summary = stringResource(R.string.history_item_summary, scan.product.name, scan.audit.grade.label, scan.audit.score) +
        (warning?.let { ", $it" } ?: "")
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(Spacing.M),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // clearAndSetSemantics scoped to just the grade+name/category portion,
            // not the whole row - it previously wrapped the IconButtons below too,
            // wiping their own semantics along with everything else it merges. A
            // TalkBack user could no longer reach the favorite/delete buttons on
            // any scan history row at all, only hear this row's single summary.
            Row(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = summary },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = gradeColor.copy(0.2f)) {
                    Text(
                        scan.audit.grade.label,
                        modifier = Modifier.padding(horizontal = Spacing.SM, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = gradeColor, fontWeight = FontWeight.Bold,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.history_score_category, scan.audit.score, scan.product.category.key.replace('_', ' ')), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                    // Same checkUserAllergens()/checkDiet() warning Diary/Recipes/Grocery/
                    // Templates already show live - previously the grade badge here was the
                    // only thing this row ever showed, with no trace of an allergen/diet
                    // conflict the Result screen flagged the moment this same product was
                    // originally scanned. semanticAmber(), not the brand accent - matches
                    // DiaryEntryCard's identical safety-relevant warning styling.
                    if (warning != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(12.dp))
                            Text(warning, style = MaterialTheme.typography.labelSmall, color = semanticAmber(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // Was a silent Room write with only the icon's color swap as feedback -
            // a quick tap on a scrolling list gave no confirmation the action
            // registered. A short haptic tick matches the confirmation every other
            // toggle-style action in the app already gives (Grocery's check-off, etc.).
            IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggleFavorite() }) {
                Icon(
                    if (scan.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (scan.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                    tint = if (scan.favorite) Gold else OnSurface.copy(0.3f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(TablerIcons.X, stringResource(R.string.common_delete), tint = OnSurface.copy(0.3f), modifier = Modifier.size(18.dp))
            }
            Icon(TablerIcons.ChevronRight, null, tint = OnSurface.copy(0.3f), modifier = Modifier.size(IconSize.Inline))
        }
    }
}
