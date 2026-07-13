package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun GlobalSummaryCard(allSessions: List<BiolismSession>) {
    val totalKcal = allSessions.sumOf { it.kcalBurned }
    val totalSec  = allSessions.sumOf { it.elapsedSec }
    val avgKcal   = totalKcal / allSessions.size
    val avgSec    = totalSec / allSessions.size
    val spark     = allSessions.takeLast(7)
    val sparkMax  = (spark.maxOfOrNull { it.kcalBurned } ?: 0.001).coerceAtLeast(0.001)
    BioCard(stringResource(R.string.biolism_summary_title), badge = { TealBadge(pluralStringResource(R.plurals.biolism_summary_session_count, allSessions.size, allSessions.size)) }) {
        MetCellGrid(listOf(
            Triple(stringResource(R.string.biolism_summary_total_burned), if (totalKcal >= 1000) "%.2fk".format(Locale.US, totalKcal / 1000) else "%.1f".format(Locale.US, totalKcal), "kcal"),
            Triple(stringResource(R.string.biolism_summary_total_time), formatDuration((totalSec * 1000).toLong()), ""),
            Triple(stringResource(R.string.biolism_summary_avg_session), "%.2f".format(Locale.US, avgKcal), "kcal"),
            Triple(stringResource(R.string.biolism_summary_avg_duration), formatDuration((avgSec * 1000).toLong()), ""),
        ))
        if (spark.size > 1) {
            Spacer(Modifier.height(8.dp))
            Label(stringResource(R.string.biolism_summary_spark_label, spark.size), OnBackground.copy(0.4f))
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
