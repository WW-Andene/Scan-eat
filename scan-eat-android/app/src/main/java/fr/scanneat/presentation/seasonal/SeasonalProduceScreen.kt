package fr.scanneat.presentation.seasonal

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import fr.scanneat.domain.engine.nutrition.SeasonalProduceKind
import fr.scanneat.presentation.seasonal.components.SeasonYearGrid
import fr.scanneat.presentation.seasonal.components.SeasonalGroupCard
import fr.scanneat.presentation.seasonal.components.SeasonalPairingsDialog
import fr.scanneat.presentation.ui.theme.*
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dashboard > "Produits de saison" - a static ADEME/Interfel-style seasonal
 * calendar for French-grown fruits and vegetables (see SeasonalProduceDb.kt's
 * own provenance note). Not tied to Grocery/Calendar (yet) - browsable on its
 * own, defaulting to the current month.
 *
 * User-requested: a real 12-month calendar (not a chevron one-month-at-a-time
 * nav) with season-density indicators per month, and a two-tap product
 * interaction - first tap highlights that product's whole in-season period
 * across the grid, a second tap (same product) opens a pairing-suggestions
 * popup (reusing findPairings(), same source RecipesScreen/Result already
 * draw on).
 */
@Composable
fun SeasonalProduceScreen(viewModel: SeasonalProduceViewModel = hiltViewModel(), onBack: () -> Unit) {
    val language = viewModel.language.collectAsStateWithLifecycle()
    val isFrench = language.value == "fr"
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val locale = Locale.forLanguageTag(language.value)
    val items = viewModel.inSeason(selectedMonth)
    val fruits = items.filter { it.kind == SeasonalProduceKind.FRUIT }
    val vegetables = items.filter { it.kind == SeasonalProduceKind.VEGETABLE }
    // Season-density indicator - how many products are in season each month,
    // computed once for the whole year so the calendar can show it regardless
    // of which single month is currently selected.
    val countByMonth = remember { (1..12).associateWith { m -> viewModel.inSeason(m).size } }

    // First tap on a product: highlight its whole season on the grid. Second
    // tap on the SAME product: open the pairings popup. Tapping a different
    // product or a different month clears this back to browsing.
    var selectedProduct by remember { mutableStateOf<SeasonalProduce?>(null) }
    var pairingsFor by remember { mutableStateOf<SeasonalProduce?>(null) }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.seasonal_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        // User-reported: bottom-of-screen content (year grid, group cards) sat
        // behind the still-visible bottom nav with no clearance reserved for
        // it - fixed by reserving space, not by hiding the nav bar (unlike
        // Favorites/Calendar/Reminders/Food Search, this screen keeps the nav
        // visible; it's a quick reference view, not a deep push destination).
        showBottomNavClearance = true,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            item {
                Text(
                    stringResource(R.string.seasonal_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnBackground.copy(0.6f),
                )
            }

            item {
                SeasonYearGrid(
                    selectedMonth = selectedMonth,
                    currentMonth = currentMonth,
                    highlightMonths = selectedProduct?.months,
                    countByMonth = countByMonth,
                    locale = locale,
                    onMonthClick = { m ->
                        viewModel.selectMonth(m)
                        selectedProduct = null
                        pairingsFor = null
                    },
                )
            }

            item {
                Text(
                    if (selectedMonth == currentMonth) {
                        stringResource(R.string.seasonal_this_month_title, monthLabel(selectedMonth, locale))
                    } else {
                        monthLabel(selectedMonth, locale).replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = OnBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val onProductClick: (SeasonalProduce) -> Unit = { produce ->
                if (selectedProduct == produce) pairingsFor = produce
                else selectedProduct = produce
            }

            if (fruits.isNotEmpty()) {
                item {
                    SeasonalGroupCard(
                        titleRes = R.string.seasonal_fruits_title, items = fruits, isFrench = isFrench,
                        selectedProduct = selectedProduct, onProductClick = onProductClick,
                    )
                }
            }
            if (vegetables.isNotEmpty()) {
                item {
                    SeasonalGroupCard(
                        titleRes = R.string.seasonal_vegetables_title, items = vegetables, isFrench = isFrench,
                        selectedProduct = selectedProduct, onProductClick = onProductClick,
                    )
                }
            }
            if (items.isEmpty()) {
                item {
                    Text(stringResource(R.string.seasonal_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    pairingsFor?.let { produce ->
        SeasonalPairingsDialog(
            produce = produce, isFrench = isFrench,
            onDismiss = { pairingsFor = null },
        )
    }
}

private fun monthLabel(month: Int, locale: Locale): String = Month.of(month).getDisplayName(TextStyle.FULL, locale)
