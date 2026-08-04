package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowDownRight
import compose.icons.tablericons.ArrowUpRight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.WeekOverWeekDelta
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.IconSize
import kotlin.math.roundToInt

@Composable
internal fun WeekDeltaCard(delta: WeekOverWeekDelta) {
    val sign = if (delta.kcal >= 0) "+" else ""
    val color = if (delta.kcal <= 0) semanticGreen() else semanticAmber()
    Row(
        modifier = Modifier
            .glassSheen(edgeAlpha = 0.10f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0f, reliefAlpha = 0f)
            .background(color.copy(0.1f))
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Icon(
            if (delta.kcal >= 0) TablerIcons.ArrowUpRight else TablerIcons.ArrowDownRight,
            null, tint = color, modifier = Modifier.size(IconSize.Small),
        )
        Text(
            stringResource(R.string.dashboard_week_delta, "$sign${delta.kcal.roundToInt()}"),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
