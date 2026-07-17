package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.CrossTrackerInsight
import fr.scanneat.domain.engine.dashboard.InsightAgreement
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.glassSheen
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen
import kotlin.math.abs

/**
 * Surfaces weeklyCrossTrackerInsight() - the one card on Dashboard that cross-
 * references two different trackers (nutrition intake vs. the weight scale)
 * instead of reporting on a single metric in isolation. Only rendered for
 * CONSISTENT/MISMATCH (see DashboardScreen) - INCONCLUSIVE means neither
 * signal is strong enough this week to say anything useful yet.
 */
@Composable
internal fun WeeklyInsightCard(insight: CrossTrackerInsight.WeightVsIntake) {
    val consistent = insight.agreement == InsightAgreement.CONSISTENT
    val color = if (consistent) semanticGreen() else semanticAmber()
    val isDeficit = insight.avgDailyDeficitKcal > 0
    val messageRes = when {
        isDeficit && consistent  -> R.string.dashboard_insight_deficit_losing
        !isDeficit && consistent -> R.string.dashboard_insight_surplus_gaining
        isDeficit                -> R.string.dashboard_insight_deficit_not_losing
        else                     -> R.string.dashboard_insight_surplus_not_gaining
    }
    Box(Modifier.fillMaxWidth().glassSheen()) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(CardRadius.CARD), color = SurfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (consistent) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                        null, tint = color, modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.dashboard_insight_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    stringResource(messageRes, abs(insight.avgDailyDeficitKcal), insight.weightTrendKgPerWeek),
                    style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.85f),
                )
                if (insight.weeklyActiveMinutes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (insight.weeklyActiveMinutes >= 150) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.dashboard_insight_active_minutes, insight.weeklyActiveMinutes),
                            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                        )
                    }
                }
            }
        }
    }
}
