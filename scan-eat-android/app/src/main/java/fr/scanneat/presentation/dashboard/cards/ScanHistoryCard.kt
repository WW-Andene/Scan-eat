package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ScanHistoryCard(scan: ScanResult) {
    val gradeColor = gradeColor(scan.audit.grade)
    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(12.dp))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceVariant)
                .padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(8.dp), color = gradeColor.copy(0.2f)) {
                Text(
                    scan.audit.grade.label,
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = gradeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(stringResource(R.string.history_score_category, scan.audit.score, scan.product.category.key.replace('_', ' ')),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
            }
            Text("${scan.audit.score}", style = MaterialTheme.typography.titleMedium, color = gradeColor, fontWeight = FontWeight.Bold)
        }
    }
}
