package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.ActivityEntry
import fr.scanneat.data.repository.health.ActivityType
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ActivityEntryRow(entry: ActivityEntry, typeLabels: Map<ActivityType, String>, subTypeLabels: Map<String, String>, onDelete: () -> Unit) {
    val e = entry
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val subLabel = e.subType?.let { subTypeLabels[it] ?: it }
                Text(
                    if (subLabel != null) "${typeLabels[e.type] ?: e.type.name} · $subLabel" else typeLabels[e.type] ?: e.type.name,
                    style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium,
                )
                Text(stringResource(R.string.activity_entry_summary, e.minutes, e.kcalBurned), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                val metricsParts = buildList {
                    if (e.sets != null && e.reps != null) add(stringResource(R.string.activity_entry_sets_reps, e.sets, e.reps))
                    e.weightUsedKg?.let { add(stringResource(R.string.activity_entry_weight, it)) }
                    e.distanceKm?.let { add(stringResource(R.string.activity_entry_distance, it)) }
                }
                if (metricsParts.isNotEmpty()) {
                    Text(metricsParts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
