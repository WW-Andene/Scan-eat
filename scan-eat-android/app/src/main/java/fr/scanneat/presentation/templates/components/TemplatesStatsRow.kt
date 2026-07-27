package fr.scanneat.presentation.templates.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun TemplatesStatsRow(count: Int, totalKcal: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = OnBackground.copy(0.06f)) {
            Text(
                stringResource(R.string.templates_stats_count, count),
                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
            )
        }
        if (totalKcal > 0) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f)) {
                Text(
                    stringResource(R.string.templates_stats_total_kcal, totalKcal),
                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                    style = MaterialTheme.typography.labelSmall, color = AccentCoral,
                )
            }
        }
    }
}
