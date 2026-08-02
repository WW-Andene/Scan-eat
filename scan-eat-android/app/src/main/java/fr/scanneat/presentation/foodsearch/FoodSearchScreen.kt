package fr.scanneat.presentation.foodsearch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import fr.scanneat.presentation.history.components.HistorySearchBar
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

/**
 * "Recherche" dashboard tile - a real search/browse engine over EVERY product this
 * app actually knows about: FOOD_DB's ~130 curated CIQUAL references, the user's
 * own custom foods, AND (the bulk of real coverage for an active user) every
 * product they've ever scanned - previously only searchable from ScanHistoryScreen
 * itself, not from here. A name collision shows the scanned item (real data, real
 * score) rather than the generic curated one - see FoodSearchViewModel's own doc
 * comment. Tapping a scanned result opens the full Result screen; a generic
 * FOOD_DB/custom entry (no score to show) expands an inline macro accordion instead.
 */
@Composable
fun FoodSearchScreen(viewModel: FoodSearchViewModel = hiltViewModel(), onBack: () -> Unit, onOpenResult: (Long) -> Unit) {
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val filter  = viewModel.filter.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsStateWithLifecycle()

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.foodsearch_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Teal),
            contentPadding = padding,
        ) {
            item { HistorySearchBar(query = query.value, onQueryChange = viewModel::setQuery) }
            item {
                val filterOptions = listOf(
                    FoodSearchFilter.ALL            to stringResource(R.string.foodsearch_filter_all),
                    FoodSearchFilter.HIGH_PROTEIN    to stringResource(R.string.foodsearch_filter_protein),
                    FoodSearchFilter.LOW_CARB        to stringResource(R.string.foodsearch_filter_low_carb),
                    FoodSearchFilter.HIGH_FIBER      to stringResource(R.string.foodsearch_filter_fiber),
                    FoodSearchFilter.IRON_SOURCE     to stringResource(R.string.foodsearch_filter_iron),
                    FoodSearchFilter.CALCIUM_SOURCE  to stringResource(R.string.foodsearch_filter_calcium),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    items(filterOptions, key = { it.first.name }) { (f, label) ->
                        FilterChip(
                            selected = filter.value == f,
                            onClick  = { viewModel.setFilter(f) },
                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.15f), selectedLabelColor = AccentCoral),
                        )
                    }
                }
            }
            if (results.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Rounded.Search,
                        stringResource(if (query.value.isBlank()) R.string.foodsearch_empty_filtered else R.string.foodsearch_empty_query),
                    )
                }
            } else {
                items(results.value, key = { (it.scanId?.toString() ?: "local") + "_" + it.name }) { item ->
                    Box(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
                        FoodSearchRow(item, onOpenResult)
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
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
