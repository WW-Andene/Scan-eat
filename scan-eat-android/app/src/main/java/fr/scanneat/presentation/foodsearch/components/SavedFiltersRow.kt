package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import compose.icons.tablericons.X
import fr.scanneat.R
import fr.scanneat.data.repository.foodsearch.SavedSearchFilter
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * User-requested: "filtres sauvegardés dans Recherche (ex: 'sans sulfates'
 * en un tap)" - one row of tap-to-apply chips for named presets
 * (query + nutrient filter + grade filter, see SavedSearchFilter's own doc
 * comment), plus a "+" chip to save whatever's currently set. Deleting a
 * chip removes the preset outright - there's nothing to "undo" back to,
 * unlike a logged entry, so no confirm dialog (same as removing a Recipes
 * favorite star).
 */
@Composable
internal fun SavedFiltersRow(
    savedFilters: List<SavedSearchFilter>,
    hasActiveFilter: Boolean,
    onApply: (SavedSearchFilter) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    if (savedFilters.isEmpty() && !hasActiveFilter) return

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            items(savedFilters, key = { it.id }) { saved ->
                FilterChip(
                    selected = false,
                    onClick = { onApply(saved) },
                    label = { Text(saved.label, maxLines = 1) },
                    trailingIcon = {
                        IconButton(onClick = { onDelete(saved.id) }, modifier = Modifier.size(IconSize.Compact)) {
                            Icon(TablerIcons.X, stringResource(R.string.common_delete), modifier = Modifier.size(IconSize.Tiny))
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(labelColor = OnBackground.copy(0.8f)),
                )
            }
        }
        if (hasActiveFilter) {
            IconButton(onClick = { showSaveDialog = true }) {
                Icon(TablerIcons.Plus, stringResource(R.string.foodsearch_save_filter_action), tint = AccentCoral)
            }
        }
    }

    if (showSaveDialog) {
        SaveFilterDialog(onConfirm = { label -> onSave(label); showSaveDialog = false }, onDismiss = { showSaveDialog = false })
    }
}

@Composable
private fun SaveFilterDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.foodsearch_save_filter_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    placeholder = { Text(stringResource(R.string.foodsearch_save_filter_placeholder)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label) }, enabled = label.isNotBlank()) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
