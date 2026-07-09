package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun GlobalSummaryCard(allSessions: List<BiolismSession>) {
    val totalKcal = allSessions.sumOf { it.kcalBurned }
    val totalSec  = allSessions.sumOf { it.elapsedSec }
    val avgKcal   = totalKcal / allSessions.size
    val avgSec    = totalSec / allSessions.size
    val spark     = allSessions.takeLast(7)
    val sparkMax  = (spark.maxOfOrNull { it.kcalBurned } ?: 0.001).coerceAtLeast(0.001)
    BioCard("Résumé global", badge = { TealBadge("${allSessions.size} SESSION${if (allSessions.size > 1) "S" else ""}") }) {
        MetCellGrid(listOf(
            Triple("Total brûlé", if (totalKcal >= 1000) "%.2fk".format(totalKcal / 1000) else "%.1f".format(totalKcal), "kcal"),
            Triple("Temps total", formatDuration((totalSec * 1000).toLong()), ""),
            Triple("Moyenne/session", "%.2f".format(avgKcal), "kcal"),
            Triple("Durée moyenne", formatDuration((avgSec * 1000).toLong()), ""),
        ))
        if (spark.size > 1) {
            Spacer(Modifier.height(8.dp))
            Label("${spark.size} dernières sessions — kcal brûlées", OnBackground.copy(0.4f))
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                spark.forEachIndexed { i, sess ->
                    val isLast = i == spark.size - 1
                    val h = (sess.kcalBurned / sparkMax * 48.0).coerceAtLeast(6.0).toInt()
                    val alpha = 0.35f + 0.65f * (i / (spark.size - 1).coerceAtLeast(1).toFloat())
                    Box(Modifier.weight(1f).height(h.dp).background(if (isLast) Gold else Gold.copy(alpha = alpha), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
