package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import fr.scanneat.domain.engine.dashboard.WeightForecast
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun WeightCard(summary: fr.scanneat.data.repository.health.WeightSummary, forecast: WeightForecast) {
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.weight_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.weight_kg, summary.latestKg), style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                    val deltaColor = when {
                        summary.deltaKg < 0 -> FlagGreen
                        summary.deltaKg > 0 -> FlagRed
                        else -> OnSurface.copy(0.5f)
                    }
                    val sign = if (summary.deltaKg >= 0) "+" else ""
                    Text(stringResource(R.string.weight_delta_kg, "$sign${summary.deltaKg}"), style = MaterialTheme.typography.labelSmall, color = deltaColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val trend = summary.trendKgPerWeek
                    val trendSign = if (trend >= 0) "+" else ""
                    Text(stringResource(R.string.weight_trend_kg_week, "$trendSign$trend"), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    if (forecast is WeightForecast.Ok) {
                        Text(stringResource(R.string.weight_goal_forecast, forecast.days), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                    }
                }
            }
        }
    }
  }
}
