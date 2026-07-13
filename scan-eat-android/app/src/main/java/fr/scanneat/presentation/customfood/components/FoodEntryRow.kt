package fr.scanneat.presentation.customfood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant

@Composable
internal fun FoodEntryRow(entry: FoodEntry, isCustom: Boolean, onDelete: () -> Unit, onRename: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                    fontWeight = FontWeight.Medium,
                )
                if (isCustom) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AccentCoral.copy(0.15f),
                    ) {
                        Text(
                            stringResource(R.string.customfood_custom_badge),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentCoral,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.customfood_macro_summary, entry.kcal.toInt(), entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurface.copy(0.55f),
            )
        }
        if (isCustom) {
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit, stringResource(R.string.common_rename),
                    tint = OnSurface.copy(0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close, stringResource(R.string.common_delete),
                    tint = OnSurface.copy(0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
