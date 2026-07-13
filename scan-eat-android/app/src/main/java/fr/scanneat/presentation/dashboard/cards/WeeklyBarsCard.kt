package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.RollupResult
import fr.scanneat.domain.engine.scoring.DailyTargets
import fr.scanneat.presentation.ui.theme.*
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun WeeklyBarsCard(rollup: RollupResult, targets: DailyTargets?, language: String) {
    // In-app language can differ from the device locale - dayOfWeek.name is always
    // the English enum constant (MONDAY, TUESDAY, ...) regardless of it, so every
    // other screen with day/date labels (DiaryScreen, WeightScreen, MealPlanScreen)
    // explicitly formats with Locale(language) instead. This card previously didn't,
    // so its day labels stayed in English even when the user picked another language.
    val locale = remember(language) { Locale(language) }
  Box(Modifier.fillMaxWidth().glassSheen()) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = SurfaceVariant) {
        Column(modifier = Modifier.padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dashboard_week_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.dashboard_week_avg_kcal, rollup.avg.kcal.roundToInt()), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
            }
            val peak = (listOf(targets?.kcal ?: 0.0) + rollup.days.map { it.kcal }).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            Row(
                modifier              = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                verticalAlignment     = Alignment.Bottom,
            ) {
                rollup.days.forEach { day ->
                    val frac = (day.kcal / peak).toFloat().coerceIn(0f, 1f)
                    val isToday = day.date == java.time.LocalDate.now()
                    val isOver  = targets != null && day.kcal > targets.kcal
                    val color   = when {
                        day.count == 0 -> OnSurface.copy(0.1f)
                        isOver         -> FlagRed.copy(0.7f)
                        else           -> AccentCoral.copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(if (day.count == 0) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            // Day labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                rollup.days.forEach { day ->
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                            .replaceFirstChar { it.uppercaseChar() },
                        modifier  = Modifier.weight(1f),
                        style     = MaterialTheme.typography.labelSmall,
                        color     = if (day.date == java.time.LocalDate.now()) AccentCoral else OnSurface.copy(0.4f),
                        textAlign = TextAlign.Center,
                        fontSize  = 9.sp,
                    )
                }
            }
        }
    }
  }
}
