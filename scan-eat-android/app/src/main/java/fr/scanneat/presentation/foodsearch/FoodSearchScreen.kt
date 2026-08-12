package fr.scanneat.presentation.foodsearch

import compose.icons.tablericons.Search
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.components.CategoryHeader
import fr.scanneat.presentation.foodsearch.components.DisplayModeButton
import fr.scanneat.presentation.foodsearch.components.FiltersSection
import fr.scanneat.presentation.foodsearch.components.GradeFilterSection
import fr.scanneat.presentation.foodsearch.components.FoodSearchRow
import fr.scanneat.presentation.foodsearch.components.OnlineSearchSection
import fr.scanneat.presentation.foodsearch.components.SourceLinksSection
import fr.scanneat.presentation.result.LogSheet
import fr.scanneat.presentation.ui.theme.*

/**
 * "Recherche" - a full search/browse engine over EVERY product this app knows
 * about (FOOD_DB + custom foods + the user's own scan history, see
 * FoodSearchViewModel's own doc comment), organized as category accordions
 * instead of one long flat list - user-requested rework, since a flat list of
 * 130+ items with no structure was hard to actually browse. Filters live behind
 * their own collapsible section for the same reason: the chip row doesn't need
 * to always occupy screen space above every result.
 */
@Composable
fun FoodSearchScreen(viewModel: FoodSearchViewModel = hiltViewModel(), onBack: () -> Unit, onOpenResult: (Long) -> Unit) {
    val query        = viewModel.query.collectAsStateWithLifecycle()
    val filter       = viewModel.filter.collectAsStateWithLifecycle()
    val gradeFilter  = viewModel.gradeFilter.collectAsStateWithLifecycle()
    val grouped      = viewModel.groupedResults.collectAsStateWithLifecycle()
    val onlineResults = viewModel.onlineResults.collectAsStateWithLifecycle()
    val onlineState   = viewModel.onlineSearchState.collectAsStateWithLifecycle()
    val displayMode   = viewModel.displayMode.collectAsStateWithLifecycle()
    val sourceLinks   = viewModel.sourceLinks.collectAsStateWithLifecycle()
    val neverTriedCategories = viewModel.neverTriedCategories.collectAsStateWithLifecycle()
    var filtersExpanded by remember { mutableStateOf(false) }
    var gradeFilterExpanded by remember { mutableStateOf(false) }
    // SCANNED starts expanded - a user's own scanned products are the most
    // personally relevant/immediately useful section; the curated reference
    // categories start folded so the screen opens uncluttered.
    var expandedCategories by remember { mutableStateOf(setOf(FoodSearchCategory.SCANNED)) }
    fun toggleCategory(c: FoodSearchCategory) {
        expandedCategories = if (c in expandedCategories) expandedCategories - c else expandedCategories + c
    }

    // User-requested "typing cache": FoodSearchViewModel now re-filters
    // onlineResults from its own in-memory cache on every query change
    // itself (instantCacheMatches, in its init block) instead of this screen
    // blanking them out here - removed, since this LaunchedEffect firing on
    // the same query change would otherwise immediately wipe out those
    // instant cache-matched suggestions right after the ViewModel sets them.

    // Was missing entirely - openOnlineItem()'s persist() write had no failure
    // feedback path at all, unlike every sibling screen (see its own doc comment).
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // R&D audit finding: Fasting and the Diary had zero cross-reference - see
    // ConsumptionRepository.log's own doc comment.
    val loggedDuringFast = viewModel.loggedDuringFast.collectAsStateWithLifecycle()
    val loggedDuringFastMessage = stringResource(R.string.result_logged_during_fast)
    LaunchedEffect(loggedDuringFast.value) {
        if (loggedDuringFast.value) {
            snackbarHostState.showSnackbar(loggedDuringFastMessage)
            viewModel.clearLoggedDuringFast()
        }
    }

    // User-requested: favorite/log a result directly from search, without first
    // navigating to the full Result screen - see FoodSearchViewModel.openLogSheet/
    // confirmLog/dismissLogSheet.
    val logProduct = viewModel.logSheetProduct.collectAsStateWithLifecycle()
    logProduct.value?.let { product ->
        LogSheet(
            product = product,
            onConfirm = { portionG, mealSlot -> viewModel.confirmLog(portionG, mealSlot) },
            onDismiss = viewModel::dismissLogSheet,
            showDestinationPicker = true,
            allowPriceLogging = false,
            onConfirmWithDestinations = { g, slot, destinations, _, _ -> viewModel.confirmLogWithDestinations(g, slot, destinations) },
        )
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.foodsearch_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold),
            contentPadding = padding,
        ) {
            item {
                ScanEatSearchField(
                    query = query.value, onQueryChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.history_search_placeholder),
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
                )
            }
            item {
                DisplayModeButton(mode = displayMode.value, onClick = viewModel::cycleDisplayMode)
            }
            // User-requested: "what have I never tried" - only shown with no
            // active search (a discovery prompt, not something that should
            // compete with actual search results).
            if (query.value.isBlank() && neverTriedCategories.value.isNotEmpty()) {
                item { fr.scanneat.presentation.foodsearch.components.NeverTriedBanner(neverTriedCategories.value) }
            }
            val showProducts = displayMode.value != SearchDisplayMode.LINKS
            val showLinks = displayMode.value != SearchDisplayMode.PRODUCTS
            if (showProducts) {
                item {
                    FiltersSection(
                        expanded = filtersExpanded,
                        onToggle = { filtersExpanded = !filtersExpanded },
                        filter = filter.value,
                        onFilterChange = viewModel::setFilter,
                    )
                }
                item {
                    GradeFilterSection(
                        expanded = gradeFilterExpanded,
                        onToggle = { gradeFilterExpanded = !gradeFilterExpanded },
                        gradeFilter = gradeFilter.value,
                        onGradeFilterChange = viewModel::setGradeFilter,
                    )
                }
                if (query.value.isNotBlank()) {
                    item {
                        OnlineSearchSection(
                            query = query.value,
                            state = onlineState.value,
                            results = onlineResults.value,
                            onSearchOnline = viewModel::searchOnline,
                            onOpenItem = { item -> viewModel.openOnlineItem(item, onOpenResult) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            onLog = viewModel::openLogSheet,
                        )
                    }
                }
            }
            if (showLinks && query.value.isNotBlank()) {
                item { SourceLinksSection(query = query.value, links = sourceLinks.value) }
            }
            if (showProducts) {
                if (grouped.value.isEmpty()) {
                    item {
                        EmptyListState(
                            TablerIcons.Search,
                            if (query.value.isBlank()) stringResource(R.string.foodsearch_empty_filtered)
                            // Was a flat "No results for this search." with no echo of what
                            // was actually typed - unlike CustomFood/Recipes' identical empty
                            // state, which both interpolate the query back to the user.
                            else stringResource(R.string.foodsearch_empty_query, query.value),
                        )
                    }
                } else {
                    // Fixed, meaningful order (personal data first, then the curated
                    // reference categories) rather than whatever order groupBy happens
                    // to yield - Map iteration order isn't a UI contract to rely on.
                    val orderedCategories = listOf(
                        FoodSearchCategory.SCANNED, FoodSearchCategory.CUSTOM,
                        FoodSearchCategory.FRUITS, FoodSearchCategory.VEGETABLES,
                        FoodSearchCategory.GRAINS_STARCHES, FoodSearchCategory.PROTEINS,
                        FoodSearchCategory.LEGUMES_NUTS_SEEDS, FoodSearchCategory.DAIRY,
                        FoodSearchCategory.FATS_OILS, FoodSearchCategory.SWEETS_SNACKS,
                        FoodSearchCategory.BEVERAGES, FoodSearchCategory.PREPARED_MEALS,
                        FoodSearchCategory.OTHER,
                    )
                    orderedCategories.forEach { category ->
                        val items = grouped.value[category].orEmpty()
                        if (items.isNotEmpty()) {
                            item(key = "header_$category") {
                                CategoryHeader(
                                    category = category,
                                    count = items.size,
                                    expanded = category in expandedCategories,
                                    onToggle = { toggleCategory(category) },
                                )
                            }
                            if (category in expandedCategories) {
                                items(items, key = { (it.scanId?.toString() ?: "local") + "_" + it.name }) { item ->
                                    Box(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
                                        FoodSearchRow(
                                            item, onOpenResult,
                                            onToggleFavorite = viewModel::toggleFavorite,
                                            onLog = viewModel::openLogSheet,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}
