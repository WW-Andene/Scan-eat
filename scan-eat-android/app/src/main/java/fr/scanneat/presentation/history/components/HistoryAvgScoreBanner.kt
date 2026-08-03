package fr.scanneat.presentation.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HistoryAvgScoreBanner(avgScore: Int) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f), shadowElevation = 0.dp, modifier = Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint).clip(RoundedCornerShape(CardRadius.CONTROL))) {
            Text(
                stringResource(R.string.history_avg_score, avgScore),
                style = MaterialTheme.typography.labelSmall,
                color = AccentCoral,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
            )
        }
    }
}
