package fr.scanneat.presentation.seasonal

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

            // Month picker - 12 chips, current month selected by default. Browsing
            // other months is the whole point of a "calendar" over a flat list.
            item {
                MonthPickerRow(selectedMonth = selectedMonth, locale = locale, onSelect = viewModel::selectMonth)
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

@Composable
private fun MonthPickerRow(selectedMonth: Int, locale: Locale, onSelect: (Int) -> Unit) {
    // A horizontally scrollable row of 12 short month chips - a FlowRow would
    // wrap to 3-4 lines for 12 items and bury the current selection; a single
    // scrollable row keeps this compact and lets the user swipe through months
    // the same way a real wall calendar's tabs would.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        items(12) { i ->
            val month = i + 1
            val selected = month == selectedMonth
            val monthName = Month.of(month).getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() }
            Text(
                monthName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) AccentCoral else OnSurface.copy(0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(CardRadius.CONTROL))
                    .let { if (selected) it.background(AccentCoral.copy(alpha = 0.12f)) else it }
                    .minTouchTarget()
                    .clickable(onClickLabel = monthName) { onSelect(month) }
                    .semantics { role = Role.Tab; selected = selected }
                    .wrapContentSize()
                    .padding(horizontal = Spacing.M, vertical = Spacing.S),
            )
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
