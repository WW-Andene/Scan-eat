package fr.scanneat.presentation.recipes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.scan.FetchedRecipeResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.scanEatTextFieldColors
import fr.scanneat.presentation.ui.theme.semanticRed

/**
 * Entry dialog for RecipesViewModel.suggestRecipes() — wires up the server's
 * suggest-recipes route (single ingredient -> Groq-generated ideas), previously
 * unreachable from the app. Unlike URL/photo import, this shows a pickable list
 * of ideas rather than pre-filling directly — tapping one calls [onPick], which
 * feeds the exact same AddRecipeDialog prefill path the other imports use.
 */
@Composable
internal fun SuggestRecipesDialog(
    isLoading: Boolean,
    results: List<FetchedRecipeResult>?,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSuggest: (String) -> Unit,
    onPick: (FetchedRecipeResult) -> Unit,
) {
    var ingredient by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor  = SurfaceVariant,
        title = { Text(stringResource(R.string.recipes_suggest_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.recipes_suggest_hint), color = OnBackground.copy(0.6f))
                OutlinedTextField(
                    value = ingredient, onValueChange = { ingredient = it },
                    label = { Text(stringResource(R.string.recipes_suggest_placeholder)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    colors = scanEatTextFieldColors(),
                )
                if (isLoading) {
                    CircularProgressIndicator(color = AccentCoral, modifier = Modifier.size(20.dp))
                }
                errorMessage?.let { Text(it, color = semanticRed()) }
                if (!results.isNullOrEmpty()) {
                    HorizontalDivider(color = OnBackground.copy(0.1f))
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results) { idea ->
                            Surface(
                                shape = RoundedCornerShape(10.dp), color = OnBackground.copy(0.05f),
                                onClick = { onPick(idea) }, modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(idea.name, style = MaterialTheme.typography.bodyMedium, color = AccentCoral)
                                    idea.steps.firstOrNull()?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), maxLines = 3)
                                    }
                                    if (idea.ingredients.isNotEmpty()) {
                                        Text(idea.ingredients.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f), maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSuggest(ingredient.trim()) }, enabled = !isLoading && ingredient.isNotBlank()) {
                Text(stringResource(R.string.recipes_suggest_button), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoading) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
