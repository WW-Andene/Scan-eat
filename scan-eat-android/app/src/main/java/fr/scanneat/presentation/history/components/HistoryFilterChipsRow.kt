package fr.scanneat.presentation.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*

/**
 * Was a permanently-visible LazyRow - now hidden behind a "Filtres: <current>"
 * toggle, same collapsible pattern Recherche/FoodSearch/Recipes/Templates use
 * (see CollapsibleFilterBar). Two independent filter dimensions here
 * (favorites-only + grade), so the summary label combines both when active.
 */
@Composable
internal fun HistoryFilterChipsRow(
    expanded: Boolean,
    onToggle: () -> Unit,
    favoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    gradeFilterOptions: List<Pair<Grade?, String>>,
    gradeFilter: Grade?,
    onGradeFilterChange: (Grade?) -> Unit,
    // False when this screen instance was opened as the dedicated Favorites tile
    // (startFavoritesOnly) - showing an interactive chip there let users toggle
    // off the very filter the "Favoris" app-bar title promises, with no way back
    // to a favorites-only view except re-entering from Dashboard.
    showFavoritesChip: Boolean = true,
) {
    val gradeLabel = gradeFilterOptions.first { it.first == gradeFilter }.second
    val favoritesLabel = stringResource(R.string.history_favorites_only)
    val summary = if (favoritesOnly && showFavoritesChip) "$favoritesLabel · $gradeLabel" else gradeLabel

    CollapsibleFilterBar(
        expanded = expanded, onToggle = onToggle,
        summaryLabel = stringResource(R.string.foodsearch_filters_label, summary),
    ) {
        LazyRow(
            contentPadding = PaddingValues(vertical = Spacing.XS),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            if (showFavoritesChip) {
                item {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick  = onToggleFavoritesOnly,
                        label    = { Text(favoritesLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Star, null, tint = if (favoritesOnly) Gold else OnBackground.copy(0.5f), modifier = Modifier.size(16.dp)) },
                        colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                    )
                }
            }
            items(gradeFilterOptions, key = { it.first?.name ?: "all" }) { (grade, label) ->
                val isSelected = gradeFilter == grade
                FilterChip(
                    selected = isSelected,
                    onClick  = { onGradeFilterChange(if (isSelected) null else grade) },
                    label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.15f),
                        selectedLabelColor     = AccentCoral,
                    ),
                )
            }
        }
    }
}
