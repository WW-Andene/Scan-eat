package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*
import java.util.Locale

@Composable
fun SessionHistoryCard(sessions: List<BiolismSession>, onDelete: (Long) -> Unit) {
    val prKcal = sessions.maxOf { it.kcalBurned }
    val prDur  = sessions.maxOf { it.elapsedSec }
    val prRate = sessions.maxOf { it.kcalPerMin }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    BioCard(stringResource(R.string.biolism_sesshist_title), defaultOpen = false, badge = { TealBadge("${sessions.size}") }) {
        sessions.forEach { sess ->
            val date = sess.timestamp.take(10)
            val dur  = formatDuration(sess.elapsedSec.toLong() * 1000)
            val isExpanded = expandedId == sess.id
            val isConfirm  = confirmDeleteId == sess.id
            val isPRKcal = sess.kcalBurned >= prKcal && sessions.size > 1
            val isPRDur  = sess.elapsedSec >= prDur && sessions.size > 1
            val isPRRate = sess.kcalPerMin >= prRate && sessions.size > 1

            Column(Modifier.fillMaxWidth().clickable { expandedId = if (isExpanded) null else sess.id }.padding(vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                            if (sess.ketosis) {
                                Surface(shape = RoundedCornerShape(3.dp), color = TealHaze, border = BorderStroke(1.dp, TealBorder)) {
                                    Text(stringResource(R.string.biolism_sesshist_keto_badge), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isPRKcal) Badge(stringResource(R.string.biolism_sesshist_record_kcal), Gold)
                            if (isPRDur) Badge(stringResource(R.string.biolism_sesshist_record_duration), Violet)
                            if (isPRRate) Badge(stringResource(R.string.biolism_sesshist_record_rate), Teal)
                        }
                        Text("${sess.activityLabel} · $dur · ${sess.kcalBurned.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                        Spacer(Modifier.height(3.dp))
                        Row(Modifier.fillMaxWidth(0.6f).height(3.dp).background(OnBackground.copy(0.06f), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(sess.fatFrac.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Warm, RoundedCornerShape(2.dp)))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.4f kg".format(Locale.US, sess.fatLostKg), style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.biolism_sesshist_fat_lost_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                    }
                }
                AnimatedVisibility(isExpanded) {
                    Column(Modifier.padding(top = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        InfoRow(stringResource(R.string.biolism_sesshist_avg_rate), "%.3f kcal/min".format(Locale.US, sess.kcalPerMin), "", TextSecondary)
                        InfoRow(stringResource(R.string.biolism_sesshist_bmr_tdee), stringResource(R.string.biolism_sesshist_bmr_tdee_value, sess.bmrDay, sess.tdeeDay), "", TextSecondary)
                        InfoRow(stringResource(R.string.biolism_sesshist_weight_start_end), "%.1f → %.1f kg".format(Locale.US, sess.startWeightKg, sess.endWeightKg), "", TextSecondary)
                        InfoRow(stringResource(R.string.biolism_sesshist_fat_fraction), "%.0f%%".format(Locale.US, sess.fatFrac * 100), "", Warm)
                        TextButton(onClick = { confirmDeleteId = sess.id }) {
                            // semanticRed(), not the raw Danger literal - matches the
                            // DeleteConfirmDialog this button opens (ConfirmDialog's own
                            // confirmColor already defaults to semanticRed()), so the
                            // trigger and the confirmation agree under colorblind mode.
                            Text(stringResource(R.string.common_delete), color = semanticRed(), style = MaterialTheme.typography.labelSmall)
                        }
                        if (isConfirm) {
                            DeleteConfirmDialog(
                                onConfirm = {
                                    onDelete(sess.id)
                                    confirmDeleteId = null
                                    if (expandedId == sess.id) expandedId = null
                                },
                                onDismiss = { confirmDeleteId = null },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = OnBackground.copy(0.06f))
        }
    }
}
