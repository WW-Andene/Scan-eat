package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ScanHistoryCard(scan: ScanResult, warning: String? = null, onItemClick: ((Long) -> Unit)? = null) {
    val gradeColor = gradeColor(scan.audit.grade)
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        contentPadding = PaddingValues(Spacing.M),
        onClick = if (onItemClick != null && scan.dbId > 0) { { onItemClick(scan.dbId) } } else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = gradeColor.copy(0.2f)) {
                Text(
                    scan.audit.grade.label,
                    modifier   = Modifier.padding(horizontal = Spacing.SM, vertical = 6.dp),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = gradeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.history_score_category, scan.audit.score, scan.product.category.key.replace('_', ' ')),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                // Same checkUserAllergens()/checkDiet() warning Diary/History already show -
                // this card is the very first place a user sees a past scan again after
                // opening the app, and previously showed a bare grade badge with no trace
                // of a conflict the Result screen had already flagged for that product.
                if (warning != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Icon(TablerIcons.AlertTriangle, contentDescription = null, tint = semanticAmber(), modifier = Modifier.size(12.dp))
                        Text(warning, style = MaterialTheme.typography.labelSmall, color = semanticAmber(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // Was a bare number - the subtitle above already spells out "X/100 ·
            // category", but this trailing large bold number (the row's visual
            // focal point) repeated the same score with no unit, readable as a
            // count/calorie value/rank rather than a /100 score at a glance.
            Text("${scan.audit.score}/100", style = MaterialTheme.typography.titleMedium, color = gradeColor, fontWeight = FontWeight.Bold)
        }
    }
}
