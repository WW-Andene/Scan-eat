package fr.scanneat.presentation.foodsearch

import compose.icons.tablericons.Search
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.components.CategoryHeader
import fr.scanneat.presentation.foodsearch.components.DisplayModeButton
import fr.scanneat.presentation.foodsearch.components.FiltersSection
import fr.scanneat.presentation.foodsearch.components.FoodSearchRow
import fr.scanneat.presentation.foodsearch.components.OnlineSearchSection
import fr.scanneat.presentation.foodsearch.components.SourceLinksSection
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
    val grouped      = viewModel.groupedResults.collectAsStateWithLifecycle()
    val onlineResults = viewModel.onlineResults.collectAsStateWithLifecycle()
    val onlineState   = viewModel.onlineSearchState.collectAsStateWithLifecycle()
    val displayMode   = viewModel.displayMode.collectAsStateWithLifecycle()
    val sourceLinks   = viewModel.sourceLinks.collectAsStateWithLifecycle()
    var filtersExpanded by remember { mutableStateOf(false) }
    // SCANNED starts expanded - a user's own scanned products are the most
    // personally relevant/immediately useful section; the curated reference
    // categories start folded so the screen opens uncluttered.
    var expandedCategories by remember { mutableStateOf(setOf(FoodSearchCategory.SCANNED)) }
    fun toggleCategory(c: FoodSearchCategory) {
        expandedCategories = if (c in expandedCategories) expandedCategories - c else expandedCategories + c
    }

    // A new typed query invalidates whatever the last "Rechercher en ligne" tap
    // fetched - without this, changing the search box left a prior query's
    // online results (and its barcodes) visible under an unrelated new query.
    LaunchedEffect(query.value) { viewModel.clearOnlineResults() }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.foodsearch_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
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
                if (query.value.isNotBlank()) {
                    item {
                        OnlineSearchSection(
                            query = query.value,
                            state = onlineState.value,
                            results = onlineResults.value,
                            onSearchOnline = viewModel::searchOnline,
                            onOpenItem = { item -> viewModel.openOnlineItem(item, onOpenResult) },
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
                                        FoodSearchRow(item, onOpenResult)
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
