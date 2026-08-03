package fr.scanneat.presentation.templates.components

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
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors

/**
 * Templates previously had no way to find a template by name at all - only the
 * meal-type filter chips - unlike Recipes' identical RecipesSearchField. Same
 * shape as that component (kept as its own small file rather than sharing one,
 * matching this codebase's existing per-screen component convention).
 */
@Composable
internal fun TemplatesSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.templates_search_placeholder), color = OnBackground.copy(0.4f)) },
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
