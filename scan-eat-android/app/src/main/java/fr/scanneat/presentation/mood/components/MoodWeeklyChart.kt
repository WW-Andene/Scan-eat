package fr.scanneat.presentation.mood.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Two mini bar rows sharing one set of day labels - mood (higher = better,
 * green-toned) and stress (higher = worse, amber/red-toned) - rather than
 * one dual-axis chart, since the two scales point opposite directions and a
 * shared axis would be misleading. Same "language = in-app language, not
 * Locale.getDefault()" convention as SleepWeeklyChart.
 */
@Composable
internal fun MoodWeeklyChart(weeklyMoodStress: List<Triple<LocalDate, Int, Int>>, language: String) {
    val locale = remember(language) { Locale(language) }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.mood_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            MoodBarRow(weeklyMoodStress, label = stringResource(R.string.mood_chart_mood_label)) { it.second }
            MoodBarRow(weeklyMoodStress, label = stringResource(R.string.mood_chart_stress_label), highIsBad = true) { it.third }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                weeklyMoodStress.forEach { (date, _, _) ->
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == LocalDate.now()) Violet else OnBackground.copy(0.35f),
                        textAlign = TextAlign.Center,
                        fontSize = TextUnit(9f, TextUnitType.Sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodBarRow(
    weeklyMoodStress: List<Triple<LocalDate, Int, Int>>,
    label: String,
    highIsBad: Boolean = false,
    value: (Triple<LocalDate, Int, Int>) -> Int,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = OnBackgroundMuted, fontSize = TextUnit(9f, TextUnitType.Sp))
    Row(modifier = Modifier.fillMaxWidth().height(ChartRowHeight.COMPACT), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
        weeklyMoodStress.forEach { entry ->
            val v = value(entry)
            val frac = (v / 5f).coerceIn(0f, 1f)
            val isToday = entry.first == LocalDate.now()
            val color = when {
                v <= 0 -> OnBackground.copy(0.08f)
                highIsBad && v >= 4 -> semanticRed().copy(if (isToday) 1f else 0.65f)
                highIsBad -> semanticAmber().copy(if (isToday) 1f else 0.6f)
                !highIsBad && v >= 4 -> semanticGreen().copy(if (isToday) 1f else 0.7f)
                else -> Violet.copy(if (isToday) 1f else 0.6f)
            }
            val barDescription = if (v <= 0) "$label: —" else "$label: $v/5"
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (frac == 0f) 0.05f else frac.coerceAtLeast(0.05f))
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(color)
                    .semantics { contentDescription = barDescription },
            )
        }
    }
}
