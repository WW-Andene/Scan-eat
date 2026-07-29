package fr.scanneat.presentation.foodsearch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.presentation.history.components.HistorySearchBar
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

/**
 * "Recherche" dashboard tile - a real search engine over the app's own food
 * database (FOOD_DB + the user's custom foods), not the narrow 6-10-result Quick
 * Add autocomplete these same foods are otherwise only ever surfaced through.
 * Read-only lookup/browse tool: no diary-logging wiring here, since nothing asked
 * for a portion/meal-slot flow - Quick Add / AddDiaryEntryDialog already own that.
 */
@Composable
fun FoodSearchScreen(viewModel: FoodSearchViewModel = hiltViewModel(), onBack: () -> Unit) {
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
                items(results.value, key = { it.name }) { entry ->
                    Box(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
                        FoodSearchRow(entry)
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}

@Composable
private fun FoodSearchRow(entry: FoodEntry) {
    var expanded by remember { mutableStateOf(false) }
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), onClick = { expanded = !expanded }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.foodsearch_macro_line, entry.kcal.roundToIntSafe(), entry.proteinG.formatDecimal(), entry.carbsG.formatDecimal(), entry.fatG.formatDecimal()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = OnSurface.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_fiber), "${entry.fiberG.formatDecimal()} g")
                DetailStat(stringResource(R.string.dashboard_micro_iron), "${entry.ironMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_calcium), "${entry.calciumMg.formatDecimal()} mg")
            }
            Spacer(Modifier.height(Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_vitd), "${entry.vitDUg.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_b12), "${entry.b12Ug.formatDecimal()} µg")
                DetailStat(stringResource(R.string.result_nutri_salt), "${entry.saltG.formatDecimal()} g")
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
