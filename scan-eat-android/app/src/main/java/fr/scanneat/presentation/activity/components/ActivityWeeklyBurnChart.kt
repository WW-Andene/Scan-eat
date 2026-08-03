package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

@Composable
internal fun ActivityWeeklyBurnChart(weeklyBurn: List<Pair<LocalDate, Int>>, language: String) {
    // In-app language, not Locale.getDefault() - this file's own comment below
    // (line ~48) claimed this chart was already the "locale-aware" example
    // Fasting7DayChart should copy, but it was still device-locale-driven itself.
    val locale = remember(language) { java.util.Locale(language) }
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
        val peak = weeklyBurn.maxOf { it.second }.coerceAtLeast(1)
        val barColor = Warm
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.activity_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                weeklyBurn.forEach { (date, kcal) ->
                    val frac = kcal.toFloat() / peak
                    // Same "Canvas/background box with no text underneath" shape as
                    // Dashboard's WeeklyBarsCard, which needed a contentDescription
                    // because TalkBack otherwise skipped the chart's data entirely -
                    // this chart had the identical gap, never fixed here.
                    val dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, locale)
                    val barDescription = stringResource(R.string.activity_week_bar_cd, dayName, kcal)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(frac.coerceAtLeast(0.02f))
                                .background(barColor.copy(if (date == LocalDate.now()) 1f else 0.4f), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .semantics { contentDescription = barDescription },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weeklyBurn.forEach { (date, _) ->
                    Text(
                        // app-audit §N: was date.dayOfWeek.name.take(1) - the raw English
                        // enum name ("MONDAY" -> "M") regardless of app language. Now uses
                        // the in-app language, not device locale (see comment above).
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = OnSurface.copy(if (date == LocalDate.now()) 0.8f else 0.4f),
                    )
                }
            }
        }
    }
}
