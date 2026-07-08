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
}

const val OFF_FIELDS = listOf(
    "product_name", "product_name_fr", "generic_name_fr", "brands",
    "categories_tags", "ingredients_text_fr", "ingredients_text",
    "nova_group", "nutriments", "labels_tags", "origins", "countries_tags",
    "quantity", "ecoscore_grade", "ecoscore_score", "nutrition_grades",
).joinToString(",")

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
    val nutriments: Map<String, Double?>?,
    @Json(name = "labels_tags") val labelsTags: List<String>?,
    val origins: String?,
    @Json(name = "countries_tags") val countriesTags: List<String>?,
    val quantity: String?,
    @Json(name = "ecoscore_grade") val ecoscoreGrade: String?,
    @Json(name = "ecoscore_score") val ecoscoreScore: Int?,
    @Json(name = "nutrition_grades") val nutritionGrades: String?,
)
