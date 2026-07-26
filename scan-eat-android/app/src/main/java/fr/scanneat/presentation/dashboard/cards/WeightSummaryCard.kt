package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun WeightCard(summary: fr.scanneat.data.repository.health.WeightSummary, forecast: WeightForecast, useImperial: Boolean = false) {
  ScanEatCard(
    contentPadding = PaddingValues(Spacing.L),
    verticalArrangement = Arrangement.spacedBy(Spacing.S),
  ) {
        Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                // dispWeight(), not the hardcoded-"kg" weight_kg string resource - this
                // headline number previously always showed kg regardless of the same
                // metric/imperial toggle the Weight tab's own headline already respects
                // (WeightScreen's dispWeight()), so the same value read differently
                // depending on which screen showed it.
                Text(dispWeight(summary.latestKg, useImperial), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                val deltaColor = when {
                    summary.deltaKg < 0 -> semanticGreen()
                    summary.deltaKg > 0 -> semanticRed()
                    else -> OnSurface.copy(0.5f)
                }
                val sign = if (summary.deltaKg >= 0) "+" else ""
                Text(stringResource(R.string.weight_delta_kg, "$sign${dispWeight(kotlin.math.abs(summary.deltaKg), useImperial)}"), style = MaterialTheme.typography.labelSmall, color = deltaColor)
            }
            Column(horizontalAlignment = Alignment.End) {
                val trend = summary.trendKgPerWeek
                val trendSign = if (trend >= 0) "+" else ""
                Text(stringResource(R.string.weight_trend_kg_week, "$trendSign${dispWeight(kotlin.math.abs(trend), useImperial)}"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                if (forecast is WeightForecast.Ok) {
                    Text(stringResource(R.string.weight_goal_forecast, forecast.days), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                }
            }
        }
  }
}
