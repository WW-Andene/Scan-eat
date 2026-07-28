package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ============================================================================
// OPEN PRODUCTS FACTS API — v2 product lookup, live fallback for the
// household/personal-care/chemical products NonConsumableLookupDb's bundled
// CSV is a frozen 2026-07-13 snapshot of (~3,125 rows). A barcode added to
// OPF after that export — or one that was just outside the category slice
// the export query covered — previously fell straight through OFF (which
// has no reason to know about a mouthwash/detergent/battery) to a dead-end
// "product not found" error, even though the exact same public database this
// app already trusts for the static CSV has a live, queryable record of it.
// Same non-profit org and API shape as OpenFoodFactsApi, different host.
// ============================================================================

interface OpenProductsFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = OPF_FIELDS,
    ): OpfResponse
}

val OPF_FIELDS = listOf("product_name", "brands", "categories_tags").joinToString(",")

@JsonClass(generateAdapter = true)
data class OpfResponse(
    val status: Int,
    val product: OpfProductDto?,
)

@JsonClass(generateAdapter = true)
data class OpfProductDto(
    @Json(name = "product_name") val productName: String?,
    val brands: String?,
    @Json(name = "categories_tags") val categoriesTags: List<String>?,
)
