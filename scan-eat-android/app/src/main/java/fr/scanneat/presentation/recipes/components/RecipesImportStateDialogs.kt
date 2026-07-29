package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.repository.planning.FetchedRecipeResult
import fr.scanneat.presentation.recipes.RecipesViewModel
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.semanticRed

@Composable
internal fun RecipesImportStateDialogs(
    showImportUrl: Boolean,
    showSuggest: Boolean,
    importState: RecipesViewModel.ImportUiState?,
    historyItems: List<String>,
    onDismissImportUrl: () -> Unit,
    onFetchUrl: (String) -> Unit,
    onDismissSuggest: () -> Unit,
    onSuggest: (String) -> Unit,
    onSuggestFromPantry: (List<String>) -> Unit,
    onPickSuggestion: (FetchedRecipeResult) -> Unit,
    onClearImportState: () -> Unit,
) {
    if (showImportUrl) {
        ImportRecipeUrlDialog(
            isLoading    = importState is RecipesViewModel.ImportUiState.Loading,
            errorMessage = (importState as? RecipesViewModel.ImportUiState.Error)?.message,
            onDismiss    = onDismissImportUrl,
            onFetch      = onFetchUrl,
        )
    } else if (showSuggest) {
        SuggestRecipesDialog(
            isLoading    = importState is RecipesViewModel.ImportUiState.Loading,
            results      = (importState as? RecipesViewModel.ImportUiState.SuggestSuccess)?.results,
            errorMessage = (importState as? RecipesViewModel.ImportUiState.Error)?.message,
            historyItems = historyItems,
            onDismiss    = onDismissSuggest,
            onSuggest    = onSuggest,
            onSuggestFromPantry = onSuggestFromPantry,
            onPick       = onPickSuggestion,
        )
    } else {
        // Photo import has no entry dialog of its own (the system photo picker is
        // the whole "input" step) - loading/error feedback for that path surfaces
        // here instead, distinguished from the URL/suggest flows by both being false.
        // Menu-scan photos share this same no-entry-dialog shape - its MenuSuccess
        // is the one branch here with real content to show, unlike Loading/Error.
        when (importState) {
            is RecipesViewModel.ImportUiState.Loading -> AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceVariant,
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        CircularProgressIndicator(color = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                        Text(stringResource(R.string.recipes_import_photo_loading), color = OnBackground)
                    }
                },
                confirmButton = {},
            )
            is RecipesViewModel.ImportUiState.Error -> AlertDialog(
                onDismissRequest = onClearImportState,
                containerColor = SurfaceVariant,
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                text = { Text(importState.message, color = semanticRed()) },
                confirmButton = { TextButton(onClick = onClearImportState) { Text(stringResource(R.string.common_ok), color = AccentCoral) } },
            )
            is RecipesViewModel.ImportUiState.MenuSuccess -> MenuScanResultDialog(
                dishes    = importState.dishes,
                onDismiss = onClearImportState,
            )
            else -> {}
        }
    }
}
