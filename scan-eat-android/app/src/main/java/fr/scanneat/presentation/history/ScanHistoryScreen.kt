package fr.scanneat.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.history.components.HistoryAvgScoreBanner
import fr.scanneat.presentation.history.components.HistoryFilterChipsRow
import fr.scanneat.presentation.history.components.HistoryGradeDistributionSection
import fr.scanneat.presentation.history.components.HistorySearchBar
import fr.scanneat.presentation.history.components.HistorySortMenu
import fr.scanneat.presentation.history.components.HistoryTopScannedRow
import fr.scanneat.presentation.history.components.ScanHistoryRow
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*



@Composable
fun ScanHistoryScreen(
    viewModel: ScanHistoryViewModel = hiltViewModel(),
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
    startFavoritesOnly: Boolean = false,
) {
    val scans = viewModel.filtered.collectAsStateWithLifecycle()
    val query = viewModel.query.collectAsStateWithLifecycle()
    val favoritesOnly = viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val sort = viewModel.sort.collectAsStateWithLifecycle()
    val canLoadMore = viewModel.canLoadMore.collectAsStateWithLifecycle()
    val gradeFilter = viewModel.gradeFilter.collectAsStateWithLifecycle()
    val topScanned = viewModel.topScanned.collectAsStateWithLifecycle()
    val gradeDistribution = viewModel.gradeDistribution.collectAsStateWithLifecycle()
    val avgScore = viewModel.avgScore.collectAsStateWithLifecycle()
    val historyWarnings = viewModel.historyWarnings.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    // Same pattern as WeightScreen - toggleFavorite()/delete() previously called
    // repo's Room writes completely unguarded; a failed write now surfaces here
    // as a one-shot snackbar instead of going back to silent.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // Grade filter options: null = all, else the exact Grade every row's own
    // badge already uses (Grade.label) - see ScanHistoryViewModel.gradeFilter's
    // doc comment for why this replaced a hand-maintained numeric score range
    // that had drifted out of sync with scoreToGrade's real breakpoints.
    val gradeFilterOptions = listOf(null to stringResource(R.string.history_score_range_all)) +
        Grade.entries.map { grade -> grade to grade.label }

    // Dashboard's "Favoris" shortcut opens History pre-filtered, rather than
    // needing a second favorites-only screen with its own list/delete/sort logic.
    LaunchedEffect(Unit) { if (startFavoritesOnly) viewModel.setFavoritesOnly(true) }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.history_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            HistorySortMenu(
                expanded = sortMenuExpanded,
                onExpandedChange = { sortMenuExpanded = it },
                currentSort = sort.value,
                onSortChange = { viewModel.setSort(it) },
            )
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
            HistorySearchBar(query = query.value, onQueryChange = { viewModel.setQuery(it) })

            // Improvement: score-range filter chips so users can drill into a grade band
            HistoryFilterChipsRow(
                favoritesOnly = favoritesOnly.value,
                onToggleFavoritesOnly = { viewModel.setFavoritesOnly(!favoritesOnly.value) },
                gradeFilterOptions = gradeFilterOptions,
                gradeFilter = gradeFilter.value,
                onGradeFilterChange = { viewModel.setGradeFilter(it) },
            )

            avgScore.value?.let { avg -> HistoryAvgScoreBanner(avg) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                // New: frequently scanned section — top 3 products by scan count
                if (topScanned.value.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.history_top_scanned_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnBackground.copy(0.5f),
                            modifier = Modifier.padding(top = Spacing.S, bottom = Spacing.XS),
                        )
                    }
                    item { HistoryTopScannedRow(topScanned.value, onOpenResult) }
                }

                // Grade distribution — A/B/C/D breakdown across full scan history
                if (gradeDistribution.value.isNotEmpty()) {
                    item { HistoryGradeDistributionSection(gradeDistribution.value) }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S), color = OnBackground.copy(0.08f))
                    }
                }

                items(scans.value, key = { it.dbId }) { scan ->
                    ScanHistoryRow(
                        scan = scan,
                        warning = historyWarnings.value[scan.dbId],
                        onOpen = { if (scan.dbId > 0) onOpenResult(scan.dbId) },
                        onToggleFavorite = { viewModel.toggleFavorite(scan) },
                        onDelete = { deleteTarget = scan.dbId },
                    )
                }
                if (scans.value.isEmpty()) {
                    item {
                        EmptyListState(
                            Icons.Rounded.History,
                            when {
                                query.value.isNotBlank() -> stringResource(R.string.history_empty_query, query.value)
                                favoritesOnly.value       -> stringResource(R.string.history_empty_favorites)
                                gradeFilter.value != null  -> stringResource(R.string.history_empty_grade)
                                else                      -> stringResource(R.string.history_empty)
                            },
                        )
                    }
                }
                if (canLoadMore.value && !favoritesOnly.value) {
                    item {
                        TextButton(onClick = { viewModel.loadMore() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.history_load_more), color = AccentCoral)
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
        }
    }

    deleteTarget?.let { id ->
        val name = scans.value.firstOrNull { it.dbId == id }?.product?.name
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = { viewModel.delete(id); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}


