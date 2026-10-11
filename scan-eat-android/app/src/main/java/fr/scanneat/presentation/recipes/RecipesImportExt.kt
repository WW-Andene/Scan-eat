package fr.scanneat.presentation.recipes

import androidx.lifecycle.viewModelScope
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.planning.FetchedRecipeResult
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

// Recipe import (web link, photo) and recipe ideas - all handled on the device
// by RecipeRepository. Extension functions rather than a delegate class since
// they need viewModelScope + the ViewModel's own StateFlows directly.

fun RecipesViewModel.importRecipeFromUrl(url: String) {
    if (url.isBlank()) return
    viewModelScope.launch {
        _importState.value = RecipesViewModel.ImportUiState.Loading
        val lang = language.value
        repo.fetchRecipeFromUrl(url, lang).fold(
            onSuccess = { _importState.value = RecipesViewModel.ImportUiState.Success(it) },
            onFailure = { e -> _importState.value = RecipesViewModel.ImportUiState.Error(importErrorMessage(e, lang)) },
        )
    }
}

/**
 * Photo counterpart to [importRecipeFromUrl] — a recipe card / cookbook page is
 * read on the device (text recognition) into a structured recipe. Shares the same
 * ImportUiState/AddRecipeDialog prefill flow as the URL import.
 */
fun RecipesViewModel.importRecipeFromPhotos(images: List<ImagePayload>) {
    if (images.isEmpty()) return
    viewModelScope.launch {
        _importState.value = RecipesViewModel.ImportUiState.Loading
        val lang = language.value
        repo.identifyRecipeFromPhotos(images, lang).fold(
            onSuccess = { _importState.value = RecipesViewModel.ImportUiState.Success(it) },
            onFailure = { e -> _importState.value = RecipesViewModel.ImportUiState.Error(importErrorMessage(e, lang)) },
        )
    }
}

/** Single-ingredient recipe ideas (SuggestRoute.kt) - shows a pickable list rather than pre-filling directly, unlike importRecipeFromUrl/Photos. */
fun RecipesViewModel.suggestRecipes(ingredient: String) {
    if (ingredient.isBlank()) return
    viewModelScope.launch {
        _importState.value = RecipesViewModel.ImportUiState.Loading
        val lang = language.value
        repo.suggestRecipes(ingredient, lang).fold(
            onSuccess = { _importState.value = RecipesViewModel.ImportUiState.SuggestSuccess(it) },
            onFailure = { e -> _importState.value = RecipesViewModel.ImportUiState.Error(importErrorMessage(e, lang)) },
        )
    }
}

/** Pantry variant of [suggestRecipes] (SuggestRoute.kt's suggest-from-pantry) - same [ImportUiState.SuggestSuccess] rendering, just a different set of inputs to the same idea-picking flow. */
fun RecipesViewModel.suggestFromPantry(items: List<String>) {
    if (items.isEmpty()) return
    viewModelScope.launch {
        _importState.value = RecipesViewModel.ImportUiState.Loading
        val lang = language.value
        repo.suggestFromPantry(items, lang).fold(
            onSuccess = { _importState.value = RecipesViewModel.ImportUiState.SuggestSuccess(it) },
            onFailure = { e -> _importState.value = RecipesViewModel.ImportUiState.Error(importErrorMessage(e, lang)) },
        )
    }
}

/** Picking one of suggestRecipes()'s ideas feeds the same Success -> AddRecipeDialog prefill path a URL/photo import uses. */
fun RecipesViewModel.pickSuggestion(result: FetchedRecipeResult) { _importState.value = RecipesViewModel.ImportUiState.Success(result) }

fun RecipesViewModel.clearImportState() { _importState.value = RecipesViewModel.ImportUiState.Idle }

/**
 * RecipesScreen's photo-picker launcher calls this when decodeImagePayload()
 * returns null (corrupt file, OOM, unsupported format) - previously that path
 * just did nothing at all, unlike every other import failure here which always
 * lands in ImportUiState.Error.
 */
fun RecipesViewModel.photoDecodeFailed() {
    val lang = language.value
    _importState.value = RecipesViewModel.ImportUiState.Error(
        if (lang == "en") "Couldn't read that photo — try a different one" else "Impossible de lire cette photo — essayez-en une autre",
    )
}

private fun importErrorMessage(e: Throwable, lang: String): String = when {
    e is HttpException && e.code() == 404 ->
        if (lang == "en") "No recipe found on this page" else "Aucune recette trouvée sur cette page"
    e is HttpException && e.code() == 429 ->
        if (lang == "en") "Too many requests — try again in a minute" else "Trop de requêtes — réessayez dans une minute"
    e is HttpException && e.code() == 400 ->
        if (lang == "en") "Invalid or unreachable URL" else "URL invalide ou inaccessible"
    // No connection / DNS failure / timeout while downloading the recipe page -
    // a raw OkHttp string ("Unable to resolve host") would otherwise reach the user.
    e is IOException ->
        if (lang == "en") "Couldn't reach this page — check your connection" else "Impossible de joindre cette page — vérifiez votre connexion"
    else -> e.message ?: (if (lang == "en") "Import failed" else "Échec de l'import")
}
