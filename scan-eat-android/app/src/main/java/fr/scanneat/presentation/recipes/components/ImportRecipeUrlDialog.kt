package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * Entry dialog for RecipesViewModel.importRecipeFromUrl() — wires up the server's
 * fetch-recipe route (SSRF-guarded HTML fetch + schema.org Recipe JSON-LD extraction),
 * which previously had no Android caller at all. [isLoading]/[errorMessage] mirror the
 * ViewModel's ImportUiState so this dialog stays a thin view over that state.
 */
@Composable
internal fun ImportRecipeUrlDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onFetch: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        // A back-press/scrim-tap dismissal while a fetch is in flight previously
        // called onDismiss (clearing the ViewModel's import state) without
        // cancelling the coroutine underneath - it kept running and later set
        // importState to Success/Error regardless, which RecipesScreen's
        // LaunchedEffect(importState.value) reacted to as if the user were still
        // here, popping AddRecipeDialog or an error dialog after they thought
        // they'd cancelled. Only the dismiss button was ever gated by isLoading;
        // this closes the same gap for the implicit dismiss paths.
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor  = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_import_url_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.recipes_import_url_hint), color = OnBackground.copy(0.6f))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.recipes_import_url_placeholder)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !isLoading,
                    colors = scanEatTextFieldColors(),
                )
                if (isLoading) {
                    CircularProgressIndicator(color = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                }
                errorMessage?.let { Text(it, color = semanticRed()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onFetch(url.trim()) }, enabled = !isLoading && url.isNotBlank()) {
                Text(stringResource(R.string.recipes_import_url_fetch_button), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
