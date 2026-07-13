package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.AmberWarning
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant

// Orchestrator only — content composition lives in ResultContent.kt, each
// section/banner in cards/*.kt. Was previously a single 467-line file with
// everything inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onLog: () -> Unit,
) {
    val state       = viewModel.state.collectAsStateWithLifecycle()
    val language    = viewModel.language.collectAsStateWithLifecycle()
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet   by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    var showHints    by remember { mutableStateOf(false) }

    // Navigate to diary after successful log
    LaunchedEffect(state.value.logState) {
        if (state.value.logState is LogState.Done) {
            showSheet = false
            onLog()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title), color = OnBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
                    }
                },
                actions = {
                    state.value.scanResult?.let { scan ->
                        IconButton(onClick = { showHints = true }) {
                            Icon(Icons.Default.Lightbulb, stringResource(R.string.hint_cd_open), tint = AmberWarning)
                        }
                        IconButton(onClick = { showSaveMenu = true }) {
                            Icon(
                                if (scan.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                stringResource(if (scan.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                                tint = if (scan.favorite) Gold else OnBackground,
                            )
                        }
                    }
                    TextButton(onClick = { showSheet = true }) {
                        Text(stringResource(R.string.result_log_it), color = AccentCoral, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        val s = state.value
        if (s.scanResult == null) {
            // Matches ScoreRing's own size/stroke/track exactly, so the loading
            // state visually sets up the score reveal instead of being a generic
            // spinner unrelated to what's about to appear.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCoral, strokeWidth = 14.dp, trackColor = SurfaceVariant, modifier = Modifier.size(178.dp))
            }
        } else {
            ResultContent(
                scan             = s.scanResult,
                personalScore    = s.personalScore,
                comparisonResult = s.comparisonResult,
                pairings         = s.pairings,
                betterAlternative = s.betterAlternative,
                language         = language.value,
                modifier         = Modifier.padding(padding),
            )
        }
    }

    // Log bottom sheet
    if (showSheet && state.value.scanResult != null) {
        LogSheet(
            product    = state.value.scanResult!!.product,
            sheetState = sheetState,
            onConfirm  = { g, slot -> viewModel.log(g, slot) },
            onDismiss  = { showSheet = false },
        )
    }

    if (showHints && state.value.scanResult != null) {
        HintPanel(
            hints = generateProductHints(state.value.scanResult!!.product, language.value),
            onDismiss = { showHints = false },
        )
    }

    if (showSaveMenu && state.value.scanResult != null) {
        SaveDestinationsPopup(
            alreadyFavorite = state.value.scanResult!!.favorite,
            onSave = { destinations ->
                viewModel.saveToDestinations(destinations)
                showSaveMenu = false
            },
            onDismiss = { showSaveMenu = false },
        )
    }
}
