package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R

/**
 * Standard search field, previously duplicated near-verbatim across History
 * (HistorySearchBar), Recipes (RecipesSearchField), Templates
 * (TemplatesSearchField), and CustomFood's inline OutlinedTextField - four
 * copies of the exact same OutlinedTextField/leading-search-icon/
 * clear-button shape, differing only in placeholder string and outer
 * padding. Consolidated into one component every search-capable screen
 * (History, Recipes, Templates, CustomFood, Grocery, Recherche) now shares.
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
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = OnBackground.copy(0.4f)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = OnBackground.copy(0.5f)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        colors = scanEatTextFieldColors(),
    )
}
