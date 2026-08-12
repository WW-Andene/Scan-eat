package fr.scanneat.presentation.history

import compose.icons.tablericons.History
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertCircle
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Bottle
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.expenses.components.displayLabel
import fr.scanneat.presentation.history.components.HistoryAvgScoreBanner
import fr.scanneat.presentation.history.components.HistoryFilterChipsRow
import fr.scanneat.presentation.history.components.HistoryGradeDistributionSection
import fr.scanneat.presentation.history.components.HistorySortMenu
import fr.scanneat.presentation.history.components.HistoryTopScannedRow
import fr.scanneat.presentation.history.components.ScanHistoryRow
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch



@Composable
fun ScanHistoryScreen(
    viewModel: ScanHistoryViewModel = hiltViewModel(),
    onOpenResult: (Long) -> Unit,
    onBack: () -> Unit,
    startFavoritesOnly: Boolean = false,
    onOpenNonFoodHistory: () -> Unit = {},
) {
    val scans = viewModel.filtered.collectAsStateWithLifecycle()
    val query = viewModel.query.collectAsStateWithLifecycle()
    val favoritesOnly = viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val sort = viewModel.sort.collectAsStateWithLifecycle()
    val canLoadMore = viewModel.canLoadMore.collectAsStateWithLifecycle()
    val gradeFilter = viewModel.gradeFilter.collectAsStateWithLifecycle()
    val categoryFilter = viewModel.categoryFilter.collectAsStateWithLifecycle()
    val recalledDbIds = viewModel.recalledDbIds.collectAsStateWithLifecycle()
    val checkingRecalls = viewModel.checkingRecalls.collectAsStateWithLifecycle()
    val topScanned = viewModel.topScanned.collectAsStateWithLifecycle()
    val gradeDistribution = viewModel.gradeDistribution.collectAsStateWithLifecycle()
    val avgScore = viewModel.avgScore.collectAsStateWithLifecycle()
    val historyWarnings = viewModel.historyWarnings.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }
    // Same pattern as WeightScreen - toggleFavorite()/delete() previously called
    // repo's Room writes completely unguarded; a failed write now surfaces here
    // as a one-shot snackbar instead of going back to silent.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.history_deleted_message)
    val undoLabel = stringResource(R.string.history_undo)
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

    // Same reasoning as gradeFilterOptions above, reusing the same localized
    // label ExpensesSummaryCard.kt's ProductCategory.displayLabel() already
    // maintains rather than a second hand-written string table drifting from it.
    val categoryFilterOptions = listOf(null to stringResource(R.string.history_category_all)) +
        ProductCategory.entries.map { cat -> cat to cat.displayLabel() }

    // Dashboard's "Favoris" shortcut opens History pre-filtered, rather than
    // needing a second favorites-only screen with its own list/delete/sort logic.
    LaunchedEffect(Unit) { if (startFavoritesOnly) viewModel.setFavoritesOnly(true) }

    FloatingScreenScaffold(
        // Was hardcoded to history_title even when reached via the dedicated
        // Favorites tile, so the app bar read "Historique" for a screen the
        // user tapped expecting "Favoris" — mismatched destination naming.
        title = { Text(stringResource(if (startFavoritesOnly) R.string.favorites_title else R.string.history_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            // User-requested: manual, favorites-scoped recall check - see
            // ScanHistoryViewModel.checkFavoritesForRecalls()'s own doc
            // comment for why this is manual/bounded rather than automatic.
            // Only shown on the dedicated Favorites screen, where "check all
            // of these for recalls" is both meaningful (a small, curated
            // list) and discoverable (not buried behind the favorites-only
            // filter chip on the full History screen).
            if (startFavoritesOnly) {
                IconButton(onClick = { viewModel.checkFavoritesForRecalls() }, enabled = !checkingRecalls.value) {
                    if (checkingRecalls.value) {
                        ScanEatLoadingIndicator(size = IconSize.Inline, strokeWidth = 2.dp, color = OnBackground)
                    } else {
                        Icon(TablerIcons.AlertCircle, stringResource(R.string.favorites_check_recalls), tint = OnBackground)
                    }
                }
            }
            // User-requested: non-food products (shampoo/gel douche/cosmétiques...)
            // get their own small history/favorites screen (NonFoodHistoryScreen) -
            // only shown on the main History screen, not the dedicated Favorites
            // one, same "keep the Favorites app bar minimal" reasoning the recall
            // check button above already follows for its own placement.
            if (!startFavoritesOnly) {
                IconButton(onClick = onOpenNonFoodHistory) {
                    Icon(TablerIcons.Bottle, stringResource(R.string.nonfood_history_title), tint = OnBackground)
                }
            }
            HistorySortMenu(
                expanded = sortMenuExpanded,
                onExpandedChange = { sortMenuExpanded = it },
                currentSort = sort.value,
                onSortChange = { viewModel.setSort(it) },
            )
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        // User-reported (on Favorites, which reuses this screen): ambientGloom()
        // after padding(padding) clipped the gradient to a hard-edged panel
        // starting below the header instead of fading behind it - see
        // GroceryScreen's identical fix for the full explanation.
        Column(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(padding)) {
            ScanEatSearchField(
                query = query.value, onQueryChange = { viewModel.setQuery(it) },
                placeholder = stringResource(R.string.history_search_placeholder),
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
            )

            // Improvement: score-range filter chips so users can drill into a grade band
            HistoryFilterChipsRow(
                expanded = filtersExpanded, onToggle = { filtersExpanded = !filtersExpanded },
                favoritesOnly = favoritesOnly.value,
                onToggleFavoritesOnly = { viewModel.setFavoritesOnly(!favoritesOnly.value) },
                gradeFilterOptions = gradeFilterOptions,
                gradeFilter = gradeFilter.value,
                onGradeFilterChange = { viewModel.setGradeFilter(it) },
                categoryFilterOptions = categoryFilterOptions,
                categoryFilter = categoryFilter.value,
                onCategoryFilterChange = { viewModel.setCategoryFilter(it) },
                showFavoritesChip = !startFavoritesOnly,
            )

            avgScore.value?.let { avg -> HistoryAvgScoreBanner(avg) }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                // New: frequently scanned section — top 3 products by scan count.
                // Both this and the grade distribution below are computed from the
                // user's ENTIRE scan history, not favorites - showing them on the
                // dedicated Favorites screen (startFavoritesOnly) surfaced stats
                // about products the user never favorited, with nothing indicating
                // these sections were history-wide rather than favorites-scoped.
                if (!startFavoritesOnly && topScanned.value.isNotEmpty()) {
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
                if (!startFavoritesOnly && gradeDistribution.value.isNotEmpty()) {
                    item { HistoryGradeDistributionSection(gradeDistribution.value) }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S), color = OnBackground.copy(0.08f))
                    }
                }

                items(scans.value, key = { it.dbId }) { scan ->
                    ScanHistoryRow(
                        scan = scan,
                        warning = historyWarnings.value[scan.dbId],
                        recalled = scan.dbId in recalledDbIds.value,
                        onOpen = { if (scan.dbId > 0) onOpenResult(scan.dbId) },
                        onToggleFavorite = { viewModel.toggleFavorite(scan) },
                        onDelete = { deleteTarget = scan.dbId },
                    )
                }
                if (scans.value.isEmpty()) {
                    item {
                        EmptyListState(
                            TablerIcons.History,
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
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}


