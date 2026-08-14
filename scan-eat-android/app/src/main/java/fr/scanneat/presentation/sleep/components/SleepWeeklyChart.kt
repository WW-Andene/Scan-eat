package fr.scanneat.presentation.sleep.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Same shape/structure as HydrationWeeklyChart - see its own doc comment,
 * including [language] being the in-app language (not Locale.getDefault())
 * for the exact reason that comment documents.
 */
@Composable
internal fun SleepWeeklyChart(weeklyDuration: List<Pair<LocalDate, Double>>, goalHours: Double, language: String) {
    val locale = remember(language) { Locale(language) }
    val goalCoerced = goalHours.coerceAtLeast(0.1)
    val peak = weeklyDuration.maxOfOrNull { it.second }?.coerceAtLeast(goalCoerced) ?: goalCoerced
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.sleep_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
                weeklyDuration.forEach { (date, hours) ->
                    val frac = (hours / peak).toFloat().coerceIn(0f, 1f)
                    val isToday = date == LocalDate.now()
                    val goalMet = hours >= goalCoerced
                    val color = when {
                        hours <= 0.0 -> OnBackground.copy(0.08f)
                        goalMet      -> semanticGreen().copy(if (isToday) 1f else 0.7f)
                        else         -> Violet.copy(if (isToday) 1f else 0.6f)
                    }
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
                    val hoursRounded = (hours * 10).roundToInt() / 10.0
                    val barDescription = if (hours <= 0.0) dayName else "$dayName: ${hoursRounded}h"
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(if (frac == 0f) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color)
                            .semantics { contentDescription = barDescription },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (goalMet) {
                            Text("✓", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                weeklyDuration.forEach { (date, _) ->
                    Text(
                        // app-audit §J2: SHORT, not NARROW - same day-label ambiguity fix applied app-wide.
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == LocalDate.now()) Violet else OnBackground.copy(0.35f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                }
            }
        }
    }
}
