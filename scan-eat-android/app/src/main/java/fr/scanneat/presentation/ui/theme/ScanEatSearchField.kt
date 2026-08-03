package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R

/**
 * Standard search field, previously duplicated near-verbatim across History
 * (HistorySearchBar), Recipes (RecipesSearchField), Templates
 * (TemplatesSearchField), and CustomFood's inline OutlinedTextField - four
 * copies of the exact same OutlinedTextField/leading-search-icon/
 * clear-button shape, differing only in placeholder string and outer
 * padding. Consolidated into one component every search-capable screen
 * (History, Recipes, Templates, CustomFood, Grocery, Recherche) now shares.
 *
 * Compact by design: smaller type/icons and a heightIn(min = 48dp) - Material/
 * WCAG's own minimum touch target, not an arbitrary shrink - instead of
 * OutlinedTextField's default ~56-64dp look-and-feel (large placeholder text,
 * 24dp default icons). A hard-fixed .height() was deliberately avoided here:
 * OutlinedTextField's internal layout already enforces its own minimum
 * height, so forcing an exact height smaller than that risks a
 * min-height-exceeds-max-height layout crash across every screen that uses
 * this shared component - heightIn(min=...) only raises the floor and can
 * never conflict with it.
 */
@Composable
fun ScanEatSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.4f)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            // IconButton kept at its default 48dp touch target (Material/WCAG
            // minimum) even though the field itself is compacted - only the
            // icon glyph inside is shrunk to match the smaller field, same
            // "shrink the glyph, never the tap target" discipline as the
            // Diary tab row above.
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f), modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = scanEatTextFieldColors(),
    )
}
