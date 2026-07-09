package fr.scanneat.presentation.hydration

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HYD_GLASS_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject



@Composable
fun HydrationScreen(
    viewModel: HydrationViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val intake = viewModel.intake.collectAsStateWithLifecycle()
    val goal   = viewModel.goal.collectAsStateWithLifecycle()
    val glasses = intake.value / HYD_GLASS_ML
    val goalGlasses = goal.value / HYD_GLASS_ML
    val pct = (intake.value.toFloat() / goal.value.toFloat()).coerceIn(0f, 1.2f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hydration_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // Big ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                CircularProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = HydrationBlue,
                    trackColor = SurfaceVariant,
                    strokeWidth = 14.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${intake.value}", style = MaterialTheme.typography.headlineLarge, color = HydrationBlue, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.hydration_goal_ml, goal.value), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                    Text(stringResource(R.string.hydration_glasses_count, glasses, goalGlasses), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), fontSize = 11.sp)
                }
            }

            // Glass grid — filled up to current intake, gold accent past goal
            val totalGlassCells = maxOf(goalGlasses, glasses)
            if (totalGlassCells > 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until totalGlassCells).chunked(8).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { i ->
                                val filled = i < glasses
                                val overGoal = i >= goalGlasses
                                Icon(
                                    Icons.Default.Opacity,
                                    contentDescription = null,
                                    tint = when {
                                        !filled -> OnBackground.copy(0.15f)
                                        overGoal -> Gold
                                        else -> HydrationBlue
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (pct >= 1f) {
                Surface(shape = RoundedCornerShape(12.dp), color = FlagGreen.copy(0.15f)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = FlagGreen, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.hydration_goal_reached), style = MaterialTheme.typography.bodyMedium, color = FlagGreen)
                    }
                }
            }

            // Controls
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingActionButton(
                    onClick = { viewModel.removeGlass() },
                    containerColor = SurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Default.Remove, stringResource(R.string.common_remove), tint = OnSurface) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.hydration_glass_ml, HYD_GLASS_ML), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
                    Text(stringResource(R.string.hydration_per_glass_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                }

                FloatingActionButton(
                    onClick = { viewModel.addGlass() },
                    containerColor = HydrationBlue,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
            }

            Text(
                stringResource(R.string.hydration_goal_footer, goal.value),
                style = MaterialTheme.typography.bodySmall,
                color = OnBackground.copy(0.4f),
            )
        }
    }

}
