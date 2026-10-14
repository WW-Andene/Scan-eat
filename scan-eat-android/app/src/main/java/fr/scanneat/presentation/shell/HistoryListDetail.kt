package fr.scanneat.presentation.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.hilt.navigation.HiltViewModelFactory
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import fr.scanneat.R
import fr.scanneat.presentation.history.ScanHistoryScreen
import fr.scanneat.presentation.result.ResultScreen
import fr.scanneat.presentation.result.ResultViewModel
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing

/** Width at which history switches to list + detail side by side: 840dp = 512 + 256 + 64 + 8 (Material "expanded"). */
internal val ListDetailMinWidth = 512.dp + 256.dp + 64.dp + 8.dp

/**
 * Scan history as a list/detail pair on expanded widths (F29 adaptive): the
 * list stays on the left and the selected scan's result opens on the right,
 * instead of a full-screen push that throws away the list position. Below
 * [ListDetailMinWidth] it's the unchanged single-pane flow ([onOpenResult]).
 */
@Composable
internal fun HistoryListDetail(
    entry: NavBackStackEntry,
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenNonFoodHistory: () -> Unit,
    onOpenRecalls: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onLog: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < ListDetailMinWidth) {
            ScanHistoryScreen(onOpenResult = onOpenResult, onBack = onBack, onOpenNonFoodHistory = onOpenNonFoodHistory, onOpenRecalls = onOpenRecalls)
            return@BoxWithConstraints
        }
        var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
        Row(Modifier.fillMaxSize()) {
            // List pane at a fixed readable width (384 + 64 = 448dp); the detail takes the rest.
            Box(Modifier.width(384.dp + 64.dp).fillMaxHeight()) {
                ScanHistoryScreen(onOpenResult = { selectedId = it }, onBack = onBack, onOpenNonFoodHistory = onOpenNonFoodHistory, onOpenRecalls = onOpenRecalls)
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val id = selectedId
                if (id == null) {
                    Text(
                        stringResource(R.string.history_pane_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface.copy(0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(Spacing.XL),
                    )
                } else {
                    ResultScreen(
                        viewModel = paneResultViewModel(entry, id),
                        onBack = { selectedId = null },
                        onOpenResult = { selectedId = it },
                        onOpenProfile = onOpenProfile,
                        onLog = onLog,
                    )
                }
            }
        }
    }
}

/**
 * ResultViewModel reads `scanId` from its SavedStateHandle, normally filled
 * from the result route's nav arguments. In the pane there's no such route, so
 * the id is passed as DEFAULT_ARGS and the instance is keyed per scan - each
 * selection gets its own correctly initialised ViewModel.
 */
@Composable
private fun paneResultViewModel(entry: NavBackStackEntry, scanId: Long): ResultViewModel {
    val extras = MutableCreationExtras(entry.defaultViewModelCreationExtras).apply {
        set(DEFAULT_ARGS_KEY, bundleOf("scanId" to scanId, "fresh" to false))
    }
    return viewModel(
        viewModelStoreOwner = entry,
        key = "history-pane-result-$scanId",
        factory = HiltViewModelFactory(LocalContext.current, entry),
        extras = extras,
    )
}
