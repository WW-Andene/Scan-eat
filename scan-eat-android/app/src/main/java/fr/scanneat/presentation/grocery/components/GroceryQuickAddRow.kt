package fr.scanneat.presentation.grocery.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun GroceryQuickAddRow(quickAddText: String, onQuickAddTextChange: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        OutlinedTextField(
            value = quickAddText,
            onValueChange = onQuickAddTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.grocery_quick_add_placeholder), color = OnBackground.copy(0.4f)) },
            singleLine = true,
            shape = RoundedCornerShape(CardRadius.CONTROL),
            colors = scanEatTextFieldColors(),
            // Previously no imeAction/KeyboardActions at all - onAdd could only be
            // triggered by tapping the separate IconButton, not via the keyboard's
            // Done/Go action, on the app's primary "type and submit" entry row.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (quickAddText.isNotBlank()) onAdd() }),
        )
        IconButton(
            onClick = onAdd,
            enabled = quickAddText.isNotBlank(),
            modifier = Modifier.minTouchTarget(), // was a fixed 40dp, below the 48dp WCAG/Material minimum
        ) {
            Icon(TablerIcons.Plus, stringResource(R.string.grocery_quick_add_cd), tint = if (quickAddText.isNotBlank()) AccentCoral else OnBackground.copy(0.3f))
        }
    }
}

/** See GroceryViewModel.frequentSuggestions' own doc comment. One-tap add for
 *  a name bought at least twice before and not already on the current list. */
@Composable
internal fun GroceryFrequentSuggestionsRow(suggestions: List<String>, onAdd: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        items(suggestions, key = { it }) { name ->
            SuggestionChip(
                onClick = { onAdd(name) },
                label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(CardRadius.BADGE),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = dialogContainerColor,
                    labelColor = OnSurface,
                ),
                border = null,
            )
        }
    }
}
