package fr.scanneat.presentation.seasonal

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import fr.scanneat.domain.engine.nutrition.SeasonalProduceKind
import fr.scanneat.domain.engine.planning.findPairings
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

/**
 * 4x3 real month grid (Jan-Dec) replacing the previous chevron one-month
 * nav - each cell is itself the month selector (tap to browse that month),
 * shaded by how many products are in season that month (season density),
 * and overridden with a distinct highlight color for [highlightMonths] when
 * a specific product is selected below.
 */
@Composable
private fun SeasonYearGrid(
    selectedMonth: Int,
    currentMonth: Int,
    highlightMonths: Set<Int>?,
    countByMonth: Map<Int, Int>,
    locale: Locale,
    onMonthClick: (Int) -> Unit,
) {
    val maxCount = (countByMonth.values.maxOrNull() ?: 1).coerceAtLeast(1)
    ScanEatCard(contentPadding = PaddingValues(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        (0 until 4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                (1..3).forEach { col ->
                    val month = row * 3 + col
                    SeasonMonthCell(
                        month = month,
                        isSelected = month == selectedMonth,
                        isCurrent = month == currentMonth,
                        isHighlighted = highlightMonths?.contains(month) == true,
                        density = (countByMonth[month] ?: 0).toFloat() / maxCount,
                        locale = locale,
                        modifier = Modifier.weight(1f),
                        onClick = { onMonthClick(month) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonMonthCell(
    month: Int,
    isSelected: Boolean,
    isCurrent: Boolean,
    isHighlighted: Boolean,
    density: Float,
    locale: Locale,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Season-density fill (how many products are in season this month) stays
    // visible underneath the selection/highlight ring rather than being
    // replaced by it, so the indicator this whole grid exists for is never
    // hidden by browsing/selecting.
    val densityFill = Teal.copy(alpha = 0.06f + density * 0.22f)
    val fill = if (isHighlighted) semanticGreen().copy(alpha = 0.28f) else densityFill
    val border = when {
        isHighlighted -> BorderStroke(1.5.dp, semanticGreen())
        isSelected -> BorderStroke(1.5.dp, AccentCoral)
        else -> null
    }
    Box(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(CardRadius.CONTROL))
            .background(fill)
            .let { if (border != null) it.border(border, RoundedCornerShape(CardRadius.CONTROL)) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.T2)) {
            Text(
                Month.of(month).getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) AccentCoral else OnBackground,
                fontWeight = if (isSelected || isHighlighted) FontWeight.Bold else FontWeight.Normal,
            )
            if (isCurrent) {
                Box(Modifier.size(4.dp).clip(RoundedCornerShape(50)).background(Gold))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonalGroupCard(
    titleRes: Int,
    items: List<SeasonalProduce>,
    isFrench: Boolean,
    selectedProduct: SeasonalProduce?,
    onProductClick: (SeasonalProduce) -> Unit,
) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            items.forEach { produce ->
                val selected = produce == selectedProduct
                Text(
                    if (isFrench) produce.nameFr else produce.nameEn,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) AccentCoral else OnBackground,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CardRadius.BADGE))
                        .background(if (selected) AccentCoral.copy(alpha = 0.15f) else SurfaceVariant.copy(alpha = 0.6f))
                        .clickable { onProductClick(produce) }
                        .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                )
            }
        }
        if (selectedProduct != null && selectedProduct in items) {
            Text(
                stringResource(R.string.seasonal_pairings_hint),
                style = MaterialTheme.typography.labelSmall,
                color = OnBackground.copy(0.4f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonalPairingsDialog(produce: SeasonalProduce, isFrench: Boolean, onDismiss: () -> Unit) {
    val name = if (isFrench) produce.nameFr else produce.nameEn
    val pairs = remember(produce, isFrench) { findPairings(produce.nameFr, limit = 8, preferFrench = isFrench) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(name, color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.seasonal_pairings_title), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.6f), fontWeight = FontWeight.SemiBold)
                if (pairs.isEmpty()) {
                    Text(stringResource(R.string.seasonal_pairings_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        pairs.forEach { pair ->
                            Text(
                                pair,
                                style = MaterialTheme.typography.labelMedium,
                                color = OnBackground,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(CardRadius.BADGE))
                                    .background(Teal.copy(alpha = 0.15f))
                                    .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}

private fun monthLabel(month: Int, locale: Locale): String = Month.of(month).getDisplayName(TextStyle.FULL, locale)
