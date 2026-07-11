package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentGreen
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground

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
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet   by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.common_back), tint = OnBackground)
                    }
                },
                actions = {
                    state.value.scanResult?.let { scan ->
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (scan.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                stringResource(if (scan.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                                tint = if (scan.favorite) Gold else OnBackground,
                            )
                        }
                    }
                    TextButton(onClick = { showSheet = true }) {
                        Text(stringResource(R.string.result_log_it), color = AccentGreen, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        val s = state.value
        if (s.scanResult == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGreen)
            }
        } else {
            ResultContent(
                scan             = s.scanResult,
                personalScore    = s.personalScore,
                comparisonResult = s.comparisonResult,
                pairings         = s.pairings,
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
}
