package fr.scanneat.presentation.history.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HistoryFilterChipsRow(
    favoritesOnly: Boolean,
    onToggleFavoritesOnly: () -> Unit,
    gradeFilterOptions: List<Pair<Grade?, String>>,
    gradeFilter: Grade?,
    onGradeFilterChange: (Grade?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.L, vertical = Spacing.XS),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        item {
            FilterChip(
                selected = favoritesOnly,
                onClick  = onToggleFavoritesOnly,
                label    = { Text(stringResource(R.string.history_favorites_only)) },
                leadingIcon = { Icon(Icons.Default.Star, null, tint = if (favoritesOnly) Gold else OnBackground.copy(0.5f), modifier = Modifier.size(16.dp)) },
                colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
            )
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
