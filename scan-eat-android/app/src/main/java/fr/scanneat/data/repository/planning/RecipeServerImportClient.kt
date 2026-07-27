package fr.scanneat.data.repository.planning

import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.ImagePayload
import fr.scanneat.util.ioCatching
import fr.scanneat.util.serverUrlMissingMessage
import kotlinx.coroutines.flow.first

/**
 * SERVER-mode recipe import calls (URL fetch, photo/menu identification, AI
 * suggestions) extracted verbatim out of RecipeRepository - same cohesive
 * "talk to scan-eat-server for recipe import" concern ScanRepository's own
 * ScanServerClient groups for scanning. RecipeRepository holds one instance
 * of this and forwards its five public methods unchanged, so nothing about
 * RecipeRepository's own public API (or any of its callers, e.g.
 * RecipesImportExt.kt's `repo.fetchRecipeFromUrl(...)`) changes.
 */
internal class RecipeServerImportClient(
    private val prefs: UserPreferences,
    private val serverApiProvider: ServerScanApiProvider,
) {
    /**
     * FetchRecipeRoute.kt (SSRF-guarded HTML fetch + schema.org Recipe JSON-LD
     * extraction) has existed on the server since it was added, with no Android
     * caller — every recipe had to be typed in by hand even when the user was
     * looking at a recipe blog post right in front of them. Server-mode only:
     * the SSRF-safe scraping this needs has no Direct-mode equivalent (there is
     * no safe way to do it from the client, and Groq/Cerebras have no "fetch
     * this URL" tool). Needs no Groq key (see the route's own doc comment), so
     * unlike identifyRecipeFromPhotos/suggestRecipes this never checks apiKey.
     */
    suspend fun fetchRecipeFromUrl(url: String, lang: String): Result<FetchedRecipeResult> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val resp = serverApiProvider.get(serverUrl).fetchRecipe(url)
        FetchedRecipeResult(
            name            = resp.name,
            servings        = resp.servings,
            ingredients     = resp.ingredients,
            steps           = resp.steps,
            cookTimeMinutes = resp.cookTimeMin,
            kcal            = resp.nutrition?.kcal,
            proteinG        = resp.nutrition?.proteinG,
            fatG            = resp.nutrition?.fatG,
            carbsG          = resp.nutrition?.carbsG,
            sourceUrl       = resp.sourceUrl,
        )
    }

    /**
     * IdentifyRecipeRoute.kt (recipe card / cookbook page photo → structured recipe
     * via Groq vision) has existed on the server since it was added, with no Android
     * caller — a user with a paper recipe or a photographed cookbook page had no way
     * to import it short of retyping everything. Server-mode only, same reasoning as
     * fetchRecipeFromUrl: there's no equivalent Direct-mode prompt/parser for this
     * today. Unlike fetchRecipeFromUrl this does need a Groq key (the route calls
     * Groq's vision API, see requireGroqKey() in IdentifyRecipeRoute.kt).
     *
     * Reuses [FetchedRecipeResult] rather than a separate result type - both are
     * "unverified external content pre-filling AddRecipeDialog for review," and the
     * structured ingredient quantity/unit/name here collapses to the exact same kind
     * of free-text ingredient line a schema.org import produces (see that class's own
     * doc comment on why ingredients are text, not RecipeComponents). No nutrition
     * block exists on this route's response, unlike fetch-recipe's optional one.
     */
    suspend fun identifyRecipeFromPhotos(images: List<ImagePayload>, lang: String): Result<FetchedRecipeResult> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        val resp = serverApiProvider.get(serverUrl).identifyRecipe(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        FetchedRecipeResult(
            name            = resp.name,
            servings        = resp.servings?.toString(),
            ingredients     = resp.ingredients.map { i -> listOfNotNull(i.quantity, i.unit, i.name).joinToString(" ") },
            steps           = resp.steps,
            cookTimeMinutes = resp.cookTimeMin,
            kcal            = null,
            proteinG        = null,
            fatG            = null,
            carbsG          = null,
            sourceUrl       = "",
        )
    }

    /**
     * IdentifyMenuRoute.kt (restaurant menu photo -> dishes with estimated macros via
     * Groq vision) has existed on the server since it was added, with no Android caller —
     * a user photographing a restaurant menu had no way to see estimated kcal/protein per
     * dish before ordering. Server-mode only, same reasoning as identifyRecipeFromPhotos:
     * needs a Groq key (vision API), no Direct-mode equivalent. Unlike the recipe-import
     * paths above, [MenuDish] is purely informational - there's nothing to save/log, so it
     * does not reuse [FetchedRecipeResult].
     */
    suspend fun identifyMenuFromPhotos(images: List<ImagePayload>, lang: String): Result<List<MenuDish>> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        val resp = serverApiProvider.get(serverUrl).identifyMenu(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        resp.dishes.map { d -> MenuDish(name = d.name, description = d.description, estimatedKcal = d.estimatedKcal, proteinG = d.proteinG) }
    }

    /**
     * SuggestRoute.kt (single ingredient -> recipe ideas via Groq) has existed on
     * the server since it was added, with no Android caller - Recipes had no "give
     * me ideas" entry point at all, only manual/imported entry. Server-mode only
     * and needs a Groq key, same reasoning as identifyRecipeFromPhotos. Reuses
     * [FetchedRecipeResult] per idea (description folded into a single-entry
     * [FetchedRecipeResult.steps] line, main_ingredients as the ingredient list) so
     * picking a suggestion feeds the exact same AddRecipeDialog prefill path as a
     * URL/photo import - "unverified external content the user reviews before
     * saving" describes an LLM-generated idea just as well as a scraped page.
     */
    suspend fun suggestRecipes(ingredient: String, lang: String): Result<List<FetchedRecipeResult>> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val resp = serverApiProvider.get(serverUrl).suggestRecipes(
            groqKey = apiKey.takeIf { it.isNotBlank() },
            request = ServerSuggestRecipesRequest(ingredient),
        )
        resp.recipes.toFetchedRecipeResults()
    }

    /**
     * SuggestRoute.kt's pantry variant (list of on-hand items -> recipe ideas via Groq) -
     * same route family as [suggestRecipes], same [ServerSuggestedRecipesResponse] shape,
     * so it shares that function's mapping via [toFetchedRecipeResults]. The app has no
     * persisted "pantry inventory" feature (elsewhere "pantry" only names a grocery-aisle
     * category), so this takes free-typed items rather than reading from storage - the
     * caller (SuggestRecipesDialog's pantry mode) is responsible for splitting user text
     * into [items].
     */
    suspend fun suggestFromPantry(items: List<String>, lang: String): Result<List<FetchedRecipeResult>> = ioCatching {
        val serverUrl = prefs.serverUrl.first()
        val apiKey = prefs.groqApiKey.first()
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val resp = serverApiProvider.get(serverUrl).suggestFromPantry(
            groqKey = apiKey.takeIf { it.isNotBlank() },
            request = ServerSuggestFromPantryRequest(pantry = items),
        )
        resp.recipes.toFetchedRecipeResults()
    }

    /** Shared response->domain mapping for [suggestRecipes]/[suggestFromPantry] - both return the identical [ServerSuggestedRecipesResponse] shape. */
    private fun List<ServerSuggestedRecipeDto>.toFetchedRecipeResults(): List<FetchedRecipeResult> = map { s ->
        FetchedRecipeResult(
            name            = s.name,
            servings        = null,
            ingredients     = s.mainIngredients,
            steps           = listOfNotNull(s.description.takeIf { it.isNotBlank() }),
            cookTimeMinutes = s.cookTimeMin,
            kcal            = null,
            proteinG        = null,
            fatG            = null,
            carbsG          = null,
            sourceUrl       = "",
        )
    }
}
