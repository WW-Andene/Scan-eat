package fr.scanneat.presentation.history.components

import compose.icons.tablericons.Star
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import androidx.compose.foundation.layout.*
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
 * Popup menu behind a "Filtres : <current>" button (see CollapsibleFilterBar's
 * own doc comment for why this is a popup, not an inline expandable list).
 * Two independent filter dimensions here (favorites-only + grade), so the
 * summary label combines both when active.
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
        if (showFavoritesChip) {
            DropdownMenuItem(
                text = { Text(favoritesLabel) },
                leadingIcon = { Icon(TablerIcons.Star, null, tint = if (favoritesOnly) Gold else OnBackground.copy(0.5f), modifier = Modifier.size(18.dp)) },
                trailingIcon = { if (favoritesOnly) Icon(TablerIcons.Check, null, tint = Gold, modifier = Modifier.size(18.dp)) },
                onClick = { onToggleFavoritesOnly(); onToggle() },
            )
            HorizontalDivider(color = OnSurface.copy(0.08f))
        }
        gradeFilterOptions.forEach { (grade, label) ->
            val isSelected = gradeFilter == grade
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (isSelected) Icon(TablerIcons.Check, null, tint = AccentCoral, modifier = Modifier.size(18.dp)) },
                onClick = { onGradeFilterChange(if (isSelected) null else grade); onToggle() },
            )
        }
    }
}
