package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.WeekOverWeekDelta
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.CardRadius
import kotlin.math.roundToInt

@Composable
internal fun WeekDeltaCard(delta: WeekOverWeekDelta) {
    val sign = if (delta.kcal >= 0) "+" else ""
    val color = if (delta.kcal <= 0) semanticGreen() else semanticAmber()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CardRadius.CONTROL))
            .background(color.copy(0.1f))
            .padding(horizontal = 14.dp, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            if (delta.kcal >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            null, tint = color, modifier = Modifier.size(16.dp),
        )
        Text(
            stringResource(R.string.dashboard_week_delta, "$sign${delta.kcal.roundToInt()}"),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
