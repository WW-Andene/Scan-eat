package fr.scanneat.presentation.result

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Background
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

// Orchestrator only — content composition lives in ResultContent.kt, each
// section/banner in cards/*.kt. Was previously a single 467-line file with
// everything inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onLog: () -> Unit,
    onOpenResult: (Long) -> Unit = {},
) {
    val state       = viewModel.state.collectAsStateWithLifecycle()
    val language    = viewModel.language.collectAsStateWithLifecycle()
    val profile     = viewModel.profile.collectAsStateWithLifecycle()
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet   by remember { mutableStateOf(false) }
    var showSaveMenu by remember { mutableStateOf(false) }
    val context      = LocalContext.current
    val shareTemplate = stringResource(R.string.result_share_text)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Navigate to diary after successful log. ResultViewModel.log() already computed
    // a LogState.Error on failure (Room write failure, disk full, etc.) but nothing
    // here ever read it - a failed save previously just left the sheet sitting open
    // with no feedback, so the user couldn't tell whether their tap had registered.
    LaunchedEffect(state.value.logState) {
        when (val logState = state.value.logState) {
            is LogState.Done -> { showSheet = false; onLog() }
            is LogState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(logState.message) }
                viewModel.clearLogState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        IconButton(onClick = {
                            val text = String.format(shareTemplate, scan.product.name, scan.audit.score, scan.audit.grade.label)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.result_cd_share), tint = OnBackground)
                        }
                        HintIconButton(hints = generateProductHints(scan.product, profile.value, language.value))
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
                scan              = s.scanResult,
                personalScore     = s.personalScore,
                comparisonResult  = s.comparisonResult,
                pairings          = s.pairings,
                betterAlternative = s.betterAlternative,
                language          = language.value,
                scoreDelta        = s.scoreDelta,
                scoreHistory      = s.scoreHistory,
                onOpenResult      = onOpenResult,
                modifier          = Modifier.padding(padding),
            )
        }
    }

    // Log bottom sheet
    // Each block reads state.value.scanResult once into a local val instead of
    // null-checking then re-reading + force-unwrapping a second snapshot-state read -
    // harmless today only because Compose composition is single-threaded/synchronous,
    // but a local val can't go null out from under it on any future refactor that
    // moves the access across a suspension point or into a remembered callback.
    state.value.scanResult?.let { scan ->
        if (showSheet) {
            LogSheet(
                product    = scan.product,
                sheetState = sheetState,
                onConfirm  = { g, slot -> viewModel.log(g, slot) },
                onDismiss  = { showSheet = false },
            )
        }

        if (showSaveMenu) {
            SaveDestinationsPopup(
                alreadyFavorite = scan.favorite,
                onSave = { destinations ->
                    viewModel.saveToDestinations(destinations)
                    showSaveMenu = false
                },
                onDismiss = { showSaveMenu = false },
            )
        }
    }
}
