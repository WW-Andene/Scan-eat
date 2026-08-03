package fr.scanneat.presentation.foodsearch

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Check
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

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
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Teal),
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
                            Icons.Rounded.Search,
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

private fun modeLabel(mode: SearchDisplayMode): Int = when (mode) {
    SearchDisplayMode.PRODUCTS -> R.string.foodsearch_mode_products
    SearchDisplayMode.LINKS    -> R.string.foodsearch_mode_links
    SearchDisplayMode.BOTH     -> R.string.foodsearch_mode_both
}

/**
 * Single button, three states - one tap cycles Produits -> Liens -> Produits
 * et liens -> Produits, rather than three separate toggle chips (fewer taps
 * to reach any of the three, and no extra row of controls competing with the
 * filter chips already on this screen).
 */
@Composable
private fun DisplayModeButton(mode: SearchDisplayMode, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
    ) {
        Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.XS))
        Text(stringResource(R.string.foodsearch_mode_button, stringResource(modeLabel(mode))), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Fixed, query-parameterized reference links (Wikipédia, Open Food Facts,
 * PubChem, ANSES/CIQUAL) - not a scraped result set, just the same manual
 * "search on X" a user could type into their own browser, surfaced up front
 * for whatever they typed (a food name, an additive code, a molecule). Opens
 * in the device's default browser via ACTION_VIEW - never fetched in-app.
 */
@Composable
private fun SourceLinksSection(query: String, links: List<SourceLink>) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
        Text(
            stringResource(R.string.foodsearch_links_section_header, query),
            style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.XS))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            links.forEach { link ->
                ScanEatCard(
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) },
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(link.label, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                        Icon(Icons.Rounded.OpenInNew, null, tint = OnSurface.copy(0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * "Rechercher en ligne" - explicit-only (never auto-fires on typing, see
 * FoodSearchViewModel.searchOnline's own doc comment), so it's a button + its
 * own result state rather than folding into the always-live local search
 * above. Shown whenever the query box isn't empty, regardless of whether the
 * three local sources already found something - a name/ingredient/additive
 * this user has never scanned or added is still a legitimate reason to look
 * further even if a same-named local item exists.
 */
@Composable
private fun OnlineSearchSection(
    query: String,
    state: OnlineSearchState,
    results: List<FoodSearchItem>,
    onSearchOnline: () -> Unit,
    onOpenItem: (FoodSearchItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
        if (state == OnlineSearchState.IDLE) {
            Text(
                stringResource(R.string.foodsearch_online_hint),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
            )
            Spacer(Modifier.height(Spacing.XS))
        }
        when (state) {
            OnlineSearchState.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentCoral)
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.foodsearch_online_loading), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
            }
            // F21 (docs/design-audit-step8-components-shape.md): was a bare Text in
            // MaterialTheme.colorScheme.error — this app's default Material error color,
            // not ErrorBanner's shared semanticRed()/icon/surface language every other
            // persistent (non-transient) error in the app now uses. The retry button
            // is already rendered right below regardless of state, so no actionLabel here.
            OnlineSearchState.ERROR -> ErrorBanner(message = stringResource(R.string.foodsearch_online_error))
            OnlineSearchState.EMPTY -> Text(
                stringResource(R.string.foodsearch_online_empty, query),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
            )
            else -> {}
        }
        if (state == OnlineSearchState.IDLE || state == OnlineSearchState.ERROR || state == OnlineSearchState.EMPTY) {
            Spacer(Modifier.height(Spacing.XS))
            OutlinedButton(onClick = onSearchOnline, shape = RoundedCornerShape(CardRadius.CONTROL)) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.XS))
                Text(stringResource(R.string.foodsearch_online_search_button), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (state == OnlineSearchState.SUCCESS && results.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.S))
            Text(
                stringResource(R.string.foodsearch_online_section_header, results.size),
                style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.XS))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                results.forEach { item -> FoodSearchRow(item, onOpenResult = {}, onOpenOnline = onOpenItem) }
            }
        }
    }
}

@Composable
private fun FiltersSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    filter: FoodSearchFilter,
    onFilterChange: (FoodSearchFilter) -> Unit,
) {
    val filterOptions = listOf(
        FoodSearchFilter.ALL            to stringResource(R.string.foodsearch_filter_all),
        FoodSearchFilter.HIGH_PROTEIN    to stringResource(R.string.foodsearch_filter_protein),
        FoodSearchFilter.LOW_CARB        to stringResource(R.string.foodsearch_filter_low_carb),
        FoodSearchFilter.HIGH_FIBER      to stringResource(R.string.foodsearch_filter_fiber),
        FoodSearchFilter.IRON_SOURCE     to stringResource(R.string.foodsearch_filter_iron),
        FoodSearchFilter.CALCIUM_SOURCE  to stringResource(R.string.foodsearch_filter_calcium),
    )
    CollapsibleFilterBar(
        expanded = expanded, onToggle = onToggle,
        summaryLabel = stringResource(R.string.foodsearch_filters_label, filterOptions.first { it.first == filter }.second),
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
    ) {
        filterOptions.forEach { (f, label) ->
            val isSelected = filter == f
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (isSelected) Icon(TablerIcons.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp)) },
                onClick = { onFilterChange(f); onToggle() },
            )
        }
    }
}

@Composable
private fun categoryLabel(category: FoodSearchCategory): String = stringResource(
    when (category) {
        FoodSearchCategory.SCANNED             -> R.string.foodsearch_category_scanned
        FoodSearchCategory.CUSTOM              -> R.string.foodsearch_category_custom
        FoodSearchCategory.FRUITS              -> R.string.foodsearch_category_fruits
        FoodSearchCategory.VEGETABLES          -> R.string.foodsearch_category_vegetables
        FoodSearchCategory.GRAINS_STARCHES     -> R.string.foodsearch_category_grains
        FoodSearchCategory.PROTEINS            -> R.string.foodsearch_category_proteins
        FoodSearchCategory.LEGUMES_NUTS_SEEDS  -> R.string.foodsearch_category_legumes_nuts
        FoodSearchCategory.DAIRY               -> R.string.foodsearch_category_dairy
        FoodSearchCategory.FATS_OILS           -> R.string.foodsearch_category_fats_oils
        FoodSearchCategory.SWEETS_SNACKS       -> R.string.foodsearch_category_sweets
        FoodSearchCategory.BEVERAGES           -> R.string.foodsearch_category_beverages
        FoodSearchCategory.PREPARED_MEALS      -> R.string.foodsearch_category_prepared_meals
        FoodSearchCategory.OTHER               -> R.string.foodsearch_category_other
    },
)

@Composable
private fun CategoryHeader(category: FoodSearchCategory, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).clickable(onClick = onToggle)
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.foodsearch_category_header, categoryLabel(category), count),
            style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
        )
        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = OnBackground.copy(0.5f))
    }
}

@Composable
private fun FoodSearchRow(
    item: FoodSearchItem,
    onOpenResult: (Long) -> Unit,
    onOpenOnline: ((FoodSearchItem) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val onClick = when {
        item.scanId != null -> ({ onOpenResult(item.scanId) })
        // Online (Open Food Facts search) result, never scanned/saved by this
        // user yet - tapping persists it first (see
        // FoodSearchViewModel.openOnlineItem) then opens the real Result
        // screen, same as item.scanId != null above.
        item.barcode != null && onOpenOnline != null -> ({ onOpenOnline(item) })
        else -> ({ expanded = !expanded })
    }
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.foodsearch_macro_line, item.kcal.roundToIntSafe(), item.proteinG.formatDecimal(), item.carbsG.formatDecimal(), item.fatG.formatDecimal()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
                )
            }
            // Only ever set for a scanned product (see FoodSearchItem's own doc) -
            // the one visual cue distinguishing "your real scanned product, tap for
            // its full score" from "a generic curated reference, tap to expand macros."
            item.grade?.let { grade ->
                val gColor = gradeColor(grade)
                Surface(shape = RoundedCornerShape(4.dp), color = gColor.copy(0.15f)) {
                    Text(
                        grade.label, modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = gColor, fontWeight = FontWeight.Bold,
                    )
                }
            }
            // Rows with no grade (i.e. not a scanned product) expand in place instead
            // of navigating away - nothing signaled that distinction before, so a tap
            // on one row type could unexpectedly navigate while the same tap on
            // another type expanded a detail panel, with no visible cue why.
            if (item.grade == null) {
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null, tint = OnSurface.copy(0.4f),
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = OnSurface.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_fiber), "${item.fiberG.formatDecimal()} g")
                DetailStat(stringResource(R.string.dashboard_micro_iron), "${item.ironMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_calcium), "${item.calciumMg.formatDecimal()} mg")
            }
            Spacer(Modifier.height(Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_vitd), "${item.vitDUg.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_b12), "${item.b12Ug.formatDecimal()} µg")
                DetailStat(stringResource(R.string.result_nutri_salt), "${item.saltG.formatDecimal()} g")
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
