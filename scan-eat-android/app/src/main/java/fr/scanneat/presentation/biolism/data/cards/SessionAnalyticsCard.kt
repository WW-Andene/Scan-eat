package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun SessionAnalyticsCard(sessions: List<BiolismSession>, currentWeightKg: Double) {
    val withRate = sessions.filter { it.elapsedSec > 0 }
    val avgBurnRate = if (withRate.isNotEmpty())
        withRate.sumOf { it.kcalBurned / (it.elapsedSec / 60.0) } / withRate.size
    else 0.0
    val totalFatLostKg = sessions.sumOf { it.fatLostKg }

    val effScores = sessions.takeLast(8).map { it.kcalBurned / (it.elapsedSec / 60.0).coerceAtLeast(0.0001) }
    val effMax = (effScores.maxOrNull() ?: 0.0).coerceAtLeast(0.001)

    var rolling = 0.0
    val compHistory = sessions.map { sess -> rolling += sess.fatLostKg; rolling }
    val latestFatLost = compHistory.lastOrNull() ?: 0.0
    val latestWeight = currentWeightKg - latestFatLost
    val prevFatLost = if (compHistory.size >= 2) compHistory[compHistory.size - 2] else null
    val sessionDelta = if (prevFatLost != null) latestFatLost - prevFatLost else latestFatLost
    val deltaColor = if (sessionDelta > 0.0005) Teal else if (sessionDelta < -0.0005) Severe else TextSecondary
    val trendLabel = if (sessionDelta > 0.0005) stringResource(R.string.biolism_sessan_trend_loss)
        else if (sessionDelta < -0.0005) stringResource(R.string.biolism_sessan_trend_gain)
        else stringResource(R.string.biolism_sessan_trend_stable)
    val weightDelta = latestWeight - currentWeightKg

    BioCard(stringResource(R.string.biolism_sessan_title), defaultOpen = false, badge = { TealBadge(stringResource(R.string.biolism_sessan_badge, sessions.size)) }) {
        MetCellGrid(
            listOf(
                Triple(stringResource(R.string.biolism_sessan_avg_rate), "%.3f kcal/min".format(Locale.US, avgBurnRate), ""),
                Triple(stringResource(R.string.biolism_sessan_fat_lost), if (totalFatLostKg >= 0.01) "%.3f kg".format(Locale.US, totalFatLostKg) else "%.2f g".format(Locale.US, totalFatLostKg * 1000), ""),
                Triple(stringResource(R.string.biolism_sessan_weight_delta), "%s%.1f g".format(Locale.US, if (weightDelta > 0) "+" else "", weightDelta * 1000), trendLabel),
                Triple(stringResource(R.string.biolism_sessan_best_efficiency), "%.3f kcal/min".format(Locale.US, effMax), ""),
            ),
            accents = listOf(TextSecondary, Teal, deltaColor, Gold),
        )

        if (effScores.size > 1) {
            Spacer(Modifier.height(10.dp))
            Label(stringResource(R.string.biolism_sessan_efficiency_chart, effScores.size), OnBackground.copy(0.4f))
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                effScores.forEachIndexed { i, score ->
                    val isLast = i == effScores.size - 1
                    val h = (score / effMax * 44.0).coerceAtLeast(4.0).toInt()
                    val alpha = 0.35f + 0.65f * (i / (effScores.size - 1).coerceAtLeast(1).toFloat())
                    Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Violet else Violet.copy(alpha = alpha), RoundedCornerShape(2.dp)))
                }
            }
        }

        if (compHistory.size > 1) {
            Spacer(Modifier.height(10.dp))
            TintedPanel(Violet) {
                Label(stringResource(R.string.biolism_sessan_body_comp_trend), Violet)
                val last8 = compHistory.takeLast(8)
                val maxFat = (last8.maxOrNull() ?: 0.001).coerceAtLeast(0.001)
                Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                    last8.forEachIndexed { i, fatLost ->
                        val isLast = i == last8.size - 1
                        val h = (fatLost / maxFat * 32.0).coerceAtLeast(3.0).toInt()
                        Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Teal else Teal.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                InfoRow(stringResource(R.string.biolism_sessan_fat_oxidised_cum), "%.1f g".format(Locale.US, totalFatLostKg * 1000), "", Teal)
                InfoRow(stringResource(R.string.biolism_sessan_est_weight), "%.3f kg".format(Locale.US, latestWeight), "", deltaColor)
            }
        }
    }
}
