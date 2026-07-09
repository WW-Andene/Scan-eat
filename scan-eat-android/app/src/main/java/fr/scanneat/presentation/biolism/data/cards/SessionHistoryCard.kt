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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun SessionHistoryCard(sessions: List<BiolismSession>, onDelete: (Long) -> Unit) {
    val prKcal = sessions.maxOf { it.kcalBurned }
    val prDur  = sessions.maxOf { it.elapsedSec }
    val prRate = sessions.maxOf { it.kcalPerMin }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Long?>(null) }
    BioCard("Historique des sessions", defaultOpen = false, badge = { TealBadge("${sessions.size}") }) {
        sessions.forEach { sess ->
            val date = sess.timestamp.take(10)
            val hh   = (sess.elapsedSec / 3600).toInt()
            val mm   = ((sess.elapsedSec % 3600) / 60).toInt()
            val dur  = if (hh > 0) "${hh}h ${mm.toString().padStart(2, '0')}m" else "${mm}m ${(sess.elapsedSec % 60).toInt()}s"
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
                                    Text("KÉTO", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (isPRKcal) Badge("Record kcal", Gold)
                            if (isPRDur) Badge("Record durée", Violet)
                            if (isPRRate) Badge("Record taux", Teal)
                        }
                        Text("${sess.activityLabel} · $dur · ${sess.kcalBurned.toInt()} kcal",
                            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f))
                        Spacer(Modifier.height(3.dp))
                        Row(Modifier.fillMaxWidth(0.6f).height(3.dp).background(OnBackground.copy(0.06f), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(sess.fatFrac.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(Warm, RoundedCornerShape(2.dp)))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.4f kg".format(sess.fatLostKg), style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.SemiBold)
                        Text("graisse perdue", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.3f))
                    }
                }
                AnimatedVisibility(isExpanded) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoRow("Taux moyen", "%.3f kcal/min".format(sess.kcalPerMin), "", TextSecondary)
                        InfoRow("BMR / TDEE", "%.0f / %.0f kcal/j".format(sess.bmrDay, sess.tdeeDay), "", TextSecondary)
                        InfoRow("Poids début → fin", "%.1f → %.1f kg".format(sess.startWeightKg, sess.endWeightKg), "", TextSecondary)
                        InfoRow("Fraction graisse", "%.0f%%".format(sess.fatFrac * 100), "", Warm)
                        if (!isConfirm) {
                            TextButton(onClick = { confirmDeleteId = sess.id }) {
                                Text("Supprimer", color = Danger, style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Confirmer la suppression ?", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                                TextButton(onClick = {
                                    onDelete(sess.id)
                                    confirmDeleteId = null
                                    if (expandedId == sess.id) expandedId = null
                                }) { Text("Oui", color = Danger, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = { confirmDeleteId = null }) {
                                    Text("Annuler", color = OnBackground.copy(0.5f))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = OnBackground.copy(0.06f))
        }
    }
}
