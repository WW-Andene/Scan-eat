package fr.scanneat.data.remote.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

// ============================================================================
// SERVER SCAN API — Retrofit interface for the Ktor backend (Server mode)
// Mirrors the /api/score endpoint of scan-eat-server exactly.
// ============================================================================

interface ServerScanApi {
    @POST("api/score")
    suspend fun score(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerScoreRequest,
    ): ServerScoreResponse

    /**
     * Label-free identification (fresh produce, plated dishes) - the server has
     * carried this route since IdentifyRoute.kt was added, but nothing on the
     * Android side ever called it: ScanRepository.identifyOrScoreFromImages()'s
     * identifyMode flag only ever routed DIRECT mode to OcrParser.identifyFood,
     * SERVER mode always fell through to score()'s label-OCR path regardless.
     */
    @POST("api/identify")
    suspend fun identify(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerImagesRequest,
    ): ServerIdentifyResponse

    /** IdentifyRoute.kt: plate with multiple distinct foods → one entry per food. Also unreachable from the app until now. */
    @POST("api/identify-multi")
    suspend fun identifyMulti(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerImagesRequest,
    ): ServerIdentifyMultiResponse

    /** IdentifyMenuRoute.kt: restaurant menu photo → dishes with estimated macros. Also unreachable from the app until now. */
    @POST("api/identify-menu")
    suspend fun identifyMenu(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerImagesRequest,
    ): ServerIdentifyMenuResponse

    /**
     * FetchRecipeRoute.kt has carried this since it was added — SSRF-guarded HTML
     * fetch + schema.org Recipe JSON-LD extraction — but nothing on the Android
     * side ever called it, so a user could only ever build a recipe by hand.
     * Needs no Groq key (pure HTML fetch/parse, see the route's own doc comment).
     */
    @GET("api/fetch-recipe")
    suspend fun fetchRecipe(@Query("url") url: String): ServerFetchedRecipeResponse

    /** IdentifyRecipeRoute.kt: recipe card / cookbook page photo → structured recipe. Also unreachable from the app until now. */
    @POST("api/identify-recipe")
    suspend fun identifyRecipe(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerImagesRequest,
    ): ServerIdentifiedRecipeResponse

    /** SuggestRoute.kt: single ingredient → recipe ideas. Also unreachable from the app until now. */
    @POST("api/suggest-recipes")
    suspend fun suggestRecipes(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerSuggestRecipesRequest,
    ): ServerSuggestedRecipesResponse

    /** SuggestRoute.kt: pantry items → recipe ideas using what's on hand. Also unreachable from the app until now. */
    @POST("api/suggest-from-pantry")
    suspend fun suggestFromPantry(
        @Header("X-Groq-Key") groqKey: String?,
        @Body request: ServerSuggestFromPantryRequest,
    ): ServerSuggestedRecipesResponse
}
