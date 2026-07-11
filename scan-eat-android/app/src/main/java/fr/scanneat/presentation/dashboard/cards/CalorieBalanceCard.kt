package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.scanneat.R
import fr.scanneat.presentation.dashboard.CalorieBalance
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun CalorieBalanceCard(balance: CalorieBalance, streak: Int) {
    val isSurplus = balance.net > 200
    val isDeficit = balance.net < -50
    val balColor = if (isSurplus) FlagRed else if (isDeficit) AccentGreen else AmberWarning
    val statusRes = if (isSurplus) R.string.dashboard_calorie_surplus
        else if (isDeficit) R.string.dashboard_calorie_deficit
        else R.string.dashboard_calorie_balanced
    val sourceRes = if (balance.tdeeFromBiolism) R.string.dashboard_calorie_source_biolism else R.string.dashboard_calorie_source_profile

    Box(modifier = Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(20.dp))) {
        // This is the Dashboard's one focal metric — the Part B6 atmosphere
        // fix: a soft radial light-pool in the balance color, at Haze-level
        // intensity (~10% alpha), rendered on top of the flat surface fill
        // rather than left flat. Reserved for this card alone, not every card.
        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
            Column(
                modifier = Modifier
                    .background(SurfaceVariant)
                    .background(Brush.radialGradient(listOf(balColor.copy(alpha = 0.14f), Color.Transparent)))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = if (streak > 0) 40.dp else 0.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.dashboard_calorie_balance_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(sourceRes), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                }

                Text(
                    (if (balance.net >= 0) "+" else "") + "${balance.net.roundToInt()} kcal",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp), color = balColor, fontWeight = FontWeight.Black,
                )
                Text(stringResource(statusRes), style = MaterialTheme.typography.labelSmall, color = balColor, fontWeight = FontWeight.SemiBold)

                val pct = (balance.kcalIn / balance.tdee).toFloat().coerceIn(0f, 1.2f)
                LinearProgressIndicator(
                    progress   = { pct.coerceIn(0f, 1f) },
                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color      = if (isSurplus) FlagRed else AccentGreen,
                    trackColor = SurfaceVariant.copy(alpha = 0.3f),
                )
                Text(
                    stringResource(R.string.dashboard_calorie_in_out, balance.kcalIn.roundToInt(), balance.tdee.roundToInt()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f),
                )
            }
        }

        if (streak > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .size(46.dp),
                shape = RoundedCornerShape(50),
                color = AccentGreen,
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$streak", style = MaterialTheme.typography.labelLarge, color = Color.Black, fontWeight = FontWeight.Black)
                        Text(
                            pluralStringResource(R.plurals.dashboard_streak_unit, streak),
                            style = MaterialTheme.typography.labelSmall, color = Color.Black.copy(0.7f), fontSize = 7.sp,
                        )
                    }
                }
            }
        }
    }
}
