package fr.scanneat.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ============================================================================
// OPEN BEAUTY FACTS API — v2 product lookup, the dedicated cosmetics/
// personal-care sister database OFF and OPF both lack: OFF is food-only
// (correctly), and OPF's own coverage of shampoo/shower gel/toothpaste/
// makeup is thin - most of it lumped into one undifferentiated
// PERSONAL_CARE bucket (see NonConsumableLookupDb.kt's own doc comment).
// This app already has per-category cosmetic scorers built and waiting for
// real data (ShampooQualityScore.kt, ShowerGelQualityScore.kt,
// ToothpasteQualityScore.kt, MakeupQualityScore.kt, IntimateHygieneScore.kt,
// CosmeticActivesScore.kt) but nothing ever queried the one public database
// actually built to have this exact product domain. Same non-profit org,
// same v2 API shape as OpenFoodFactsApi/OpenProductsFactsApi, different host
// - reuses OpfResponse/OpfProductDto/OPF_FIELDS as-is since the JSON shape
// for the fields this app reads (product_name/brands/categories_tags/
// ingredients_text) is identical across all three Open*Facts projects.
// ============================================================================

interface OpenBeautyFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = OPF_FIELDS,
    ): OpfResponse
}
