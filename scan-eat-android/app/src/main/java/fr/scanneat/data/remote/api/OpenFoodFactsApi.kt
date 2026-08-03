package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ============================================================================
// OPEN FOOD FACTS API — v2 product lookup
// Mirrors the fields list from src/off.ts
// ============================================================================

interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = OFF_FIELDS,
        @Query("lc") lang: String = "fr",
    ): OffResponse

    // Legacy search endpoint, not the v2 barcode-lookup one above - OFF's v2 API
    // has no free-text/ingredient search of its own, only this CGI endpoint does
    // (search_terms matches name/brand/ingredients text server-side). Lets
    // "Recherche" query the full Open Food Facts catalog by ingredient, additive,
    // or any other keyword, not just the user's own scan history/curated DB.
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") searchTerms: String,
        @Query("page_size") pageSize: Int = 24,
        @Query("fields") fields: String = OFF_SEARCH_FIELDS,
        @Query("lc") lang: String = "fr",
        @Query("json") json: Int = 1,
    ): OffSearchResponse
}

val OFF_FIELDS = listOf(
    "product_name", "product_name_fr", "generic_name_fr", "brands",
    "categories_tags", "ingredients_text_fr", "ingredients_text",
    "nova_group", "nutriments", "labels_tags", "origins", "countries_tags",
    "quantity", "ecoscore_grade", "ecoscore_score", "nutrition_grades",
    "allergens_tags", "additives_tags",
).joinToString(",")

// Same field set as a single-product lookup, plus "code" (the barcode) - the
// search endpoint returns a list, so each result needs its own barcode to open
// the full Result screen for it, unlike getProduct() where the caller already
// knows the barcode it asked for.
val OFF_SEARCH_FIELDS = "$OFF_FIELDS,code"

const val OFF_USER_AGENT = "ScanEat/0.1 (Android; +https://github.com/scanneat)"

@JsonClass(generateAdapter = true)
data class OffResponse(
    val status: Int,
    val product: OffProductDto?,
)

@JsonClass(generateAdapter = true)
data class OffProductDto(
    @Json(name = "product_name")    val productName: String?,
    @Json(name = "product_name_fr") val productNameFr: String?,
    @Json(name = "generic_name_fr") val genericNameFr: String?,
    val brands: String?,
    @Json(name = "categories_tags") val categoriesTags: List<String>?,
    @Json(name = "ingredients_text_fr") val ingredientsTextFr: String?,
    @Json(name = "ingredients_text") val ingredientsText: String?,
    @Json(name = "nova_group") val novaGroup: Int?,
    // OFF mixes numeric values with string "modifier" keys (e.g. added-sugars_modifier: "~"
    // for approximate/estimated values) in the same object — Any? lets Moshi parse each value
    // per its actual JSON type instead of throwing when a modifier key isn't a Double.
    val nutriments: Map<String, Any?>?,
    @Json(name = "labels_tags") val labelsTags: List<String>?,
    val origins: String?,
    @Json(name = "countries_tags") val countriesTags: List<String>?,
    val quantity: String?,
    @Json(name = "ecoscore_grade") val ecoscoreGrade: String?,
    @Json(name = "ecoscore_score") val ecoscoreScore: Int?,
    @Json(name = "nutrition_grades") val nutritionGrades: String?,
    @Json(name = "allergens_tags") val allergensTags: List<String>?,
    @Json(name = "additives_tags") val additivesTags: List<String>?,
    // Only populated by searchProducts() (see OFF_SEARCH_FIELDS) - null and
    // unused on the single-barcode getProduct() path.
    val code: String? = null,
)

@JsonClass(generateAdapter = true)
data class OffSearchResponse(
    val products: List<OffProductDto>?,
)
