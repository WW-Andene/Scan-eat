package fr.scanneat.presentation.fasting

import kotlinx.coroutines.flow.StateFlow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.presentation.ui.theme.*

@Composable
fun FastingScreen(
    viewModel: FastingViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state   = viewModel.fastingState.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsStateWithLifecycle()
    val streak  = viewModel.streak.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle() // force recomposition every second

    var targetHours by remember { mutableIntStateOf(16) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jeûne intermittent", color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Retour", tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Streak
            if (streak.value > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = CalorieOrange)
                        Text("${streak.value} jour${if (streak.value > 1) "s" else ""} consécutifs", style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Timer
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val fs = state.value
                        if (fs != null && fs.elapsedMs >= 0) {
                            // Active fast
                            val pct = fs.progressFraction.coerceIn(0f, 1f)
                            CircularProgressIndicator(
                                progress = { pct }, modifier = Modifier.size(140.dp),
                                color = if (pct >= 1f) AccentGreen else AccentGreen.copy(0.6f),
                                trackColor = SurfaceVariant, strokeWidth = 10.dp,
                            )
                            val h = (fs.elapsedMs / 3_600_000L).toInt()
                            val m = ((fs.elapsedMs % 3_600_000L) / 60_000L).toInt()
                            Text("${h}h ${m.toString().padStart(2, '0')}m", style = MaterialTheme.typography.headlineMedium, color = AccentGreen, fontWeight = FontWeight.Bold)
                            Text("/ ${fs.targetHours}h objectif", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { viewModel.cancel() }, border = ButtonDefaults.outlinedButtonBorder) {
                                    Text("Annuler", color = OnBackground.copy(0.7f))
                                }
                                Button(onClick = { viewModel.stop() }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                                    Text("Terminer", color = Color.Black, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            // Not active
                            Text("Commencer un jeûne", style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
                            Text("Durée cible", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(12, 16, 18, 20, 24).forEach { h ->
                                    FilterChip(
                                        selected = targetHours == h, onClick = { targetHours = h },
                                        label = { Text("${h}h", fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AccentGreen.copy(0.2f), selectedLabelColor = AccentGreen,
                                            labelColor = OnBackground.copy(0.7f),
                                        ),
                                    )
                                }
                            }
                            Button(onClick = { viewModel.start(targetHours) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen), modifier = Modifier.fillMaxWidth()) {
                                Text("Démarrer ${targetHours}h", color = Color.Black, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // History
            if (history.value.isNotEmpty()) {
                item { Text("Historique", style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold) }
                items(history.value.take(20)) { c ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(c.date, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                        Text("${c.achievedHours}h / ${c.targetHours}h", style = MaterialTheme.typography.bodySmall, color = if (c.reached) FlagGreen else AmberWarning)
                        Icon(if (c.reached) Icons.Default.CheckCircle else Icons.Default.Close, null, tint = if (c.reached) FlagGreen else OnSurface.copy(0.3f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

}
