package fr.scanneat.presentation.seasonal

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.SeasonalProduce
import fr.scanneat.domain.engine.nutrition.SeasonalProduceKind
import fr.scanneat.presentation.ui.theme.*
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Dashboard > "Produits de saison" - a static ADEME/Interfel-style seasonal
 * calendar for French-grown fruits and vegetables (see SeasonalProduceDb.kt's
 * own provenance note). Not tied to Grocery/Calendar (yet) - browsable on its
 * own, defaulting to the current month.
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

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.seasonal_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
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

            // User-reported: 12 small always-visible chips replaced with the same
            // chevron-nav month header the app's real Calendar (CalendarScreen /
            // MultiMarkerMonthGrid) already uses, instead of a bespoke picker
            // unique to this one screen.
            item {
                SeasonalMonthNavHeader(
                    selectedMonth = selectedMonth, currentMonth = currentMonth,
                    onSelect = viewModel::selectMonth,
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

            if (fruits.isNotEmpty()) {
                item { SeasonalGroupCard(titleRes = R.string.seasonal_fruits_title, items = fruits, isFrench = isFrench) }
            }
            if (vegetables.isNotEmpty()) {
                item { SeasonalGroupCard(titleRes = R.string.seasonal_vegetables_title, items = vegetables, isFrench = isFrench) }
            }
            if (items.isEmpty()) {
                item {
                    Text(stringResource(R.string.seasonal_empty), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}

/**
 * Seasonal produce is month-granularity, year-agnostic data (it repeats every
 * year) - so unlike MultiMarkerMonthGrid's real day grid, this only needs the
 * same chevron-nav "‹ Month ›" header + jump-to-current-month affordance that
 * component already establishes, cycling 1..12 instead of walking a real
 * YearMonth. Kept in this file rather than generalizing MultiMarkerMonthGrid
 * itself, which is built around a real day grid this screen has no use for.
 */
@Composable
private fun SeasonalMonthNavHeader(selectedMonth: Int, currentMonth: Int, onSelect: (Int) -> Unit) {
    // No month-name label in this row itself - the LazyColumn item right below
    // already shows it (with the "this month" phrasing when applicable), and
    // duplicating it here would just show the same month name twice.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(if (selectedMonth == 1) 12 else selectedMonth - 1) }) {
            Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.calendar_cd_prev_month), tint = OnBackground)
        }
        if (selectedMonth != currentMonth) {
            TextButton(onClick = { onSelect(currentMonth) }) {
                Text(stringResource(R.string.calendar_today), style = MaterialTheme.typography.labelMedium, color = AccentCoral)
            }
        }
        IconButton(onClick = { onSelect(if (selectedMonth == 12) 1 else selectedMonth + 1) }) {
            Icon(TablerIcons.ChevronRight, stringResource(R.string.calendar_cd_next_month), tint = OnBackground)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonalGroupCard(titleRes: Int, items: List<SeasonalProduce>, isFrench: Boolean) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            items.forEach { produce ->
                Text(
                    if (isFrench) produce.nameFr else produce.nameEn,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnBackground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CardRadius.BADGE))
                        .background(SurfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = Spacing.S, vertical = Spacing.T2),
                )
            }
        }
    }
}

private fun monthLabel(month: Int, locale: Locale): String = Month.of(month).getDisplayName(TextStyle.FULL, locale)
