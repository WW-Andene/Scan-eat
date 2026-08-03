package fr.scanneat.presentation.foodsearch

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val filter  = viewModel.filter.collectAsStateWithLifecycle()
    val grouped = viewModel.groupedResults.collectAsStateWithLifecycle()
    var filtersExpanded by remember { mutableStateOf(false) }
    // SCANNED starts expanded - a user's own scanned products are the most
    // personally relevant/immediately useful section; the curated reference
    // categories start folded so the screen opens uncluttered.
    var expandedCategories by remember { mutableStateOf(setOf(FoodSearchCategory.SCANNED)) }
    fun toggleCategory(c: FoodSearchCategory) {
        expandedCategories = if (c in expandedCategories) expandedCategories - c else expandedCategories + c
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.foodsearch_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
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
                FiltersSection(
                    expanded = filtersExpanded,
                    onToggle = { filtersExpanded = !filtersExpanded },
                    filter = filter.value,
                    onFilterChange = viewModel::setFilter,
                )
            }
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
            item { Spacer(Modifier.height(Spacing.XXL)) }
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
        LazyRow(
            contentPadding = PaddingValues(vertical = Spacing.XS),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            items(filterOptions, key = { it.first.name }) { (f, label) ->
                FilterChip(
                    selected = filter == f,
                    onClick  = { onFilterChange(f) },
                    label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.15f), selectedLabelColor = AccentCoral),
                )
            }
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
private fun FoodSearchRow(item: FoodSearchItem, onOpenResult: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val onClick = if (item.scanId != null) ({ onOpenResult(item.scanId) }) else ({ expanded = !expanded })
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
