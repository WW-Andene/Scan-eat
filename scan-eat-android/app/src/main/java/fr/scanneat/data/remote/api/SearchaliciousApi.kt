package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Roadmap A8: Open Food Facts' Search-a-licious API (search.openfoodfacts.org),
 * replacing the legacy cgi/search.pl endpoint (slow, ~10 req/min, and found
 * returning "temporarily unavailable" pages during this pass). Lucene-style
 * [q] (free text, or e.g. categories_tags:"en:plain-yogurts"), [sortBy] e.g.
 * "-unique_scans_n" for most scanned first. Verified against the live API:
 * same product fields as v2, except `brands` is a list.
 */
interface SearchaliciousApi {
    @GET("search")
    suspend fun search(
        @Query("q") q: String,
        @Query("page_size") pageSize: Int = 24,
        @Query("fields") fields: String = OFF_SEARCH_FIELDS,
        @Query("langs") langs: String = "fr",
        @Query("sort_by") sortBy: String? = null,
        @Query("page") page: Int? = null,
    ): SearchaliciousResponse
}

@JsonClass(generateAdapter = true)
data class SearchaliciousResponse(val hits: List<SalHitDto>?)

@JsonClass(generateAdapter = true)
data class SalHitDto(
    val code: String? = null,
    @Json(name = "product_name") val productName: String? = null,
    @Json(name = "product_name_fr") val productNameFr: String? = null,
    @Json(name = "generic_name_fr") val genericNameFr: String? = null,
    val brands: List<String>? = null,
    @Json(name = "categories_tags") val categoriesTags: List<String>? = null,
    @Json(name = "ingredients_text_fr") val ingredientsTextFr: String? = null,
    @Json(name = "ingredients_text") val ingredientsText: String? = null,
    @Json(name = "nova_group") val novaGroup: Int? = null,
    val nutriments: Map<String, Any?>? = null,
    @Json(name = "labels_tags") val labelsTags: List<String>? = null,
    val origins: String? = null,
    @Json(name = "countries_tags") val countriesTags: List<String>? = null,
    val quantity: String? = null,
    @Json(name = "ecoscore_grade") val ecoscoreGrade: String? = null,
    @Json(name = "ecoscore_score") val ecoscoreScore: Int? = null,
    @Json(name = "environmental_score_grade") val environmentalScoreGrade: String? = null,
    @Json(name = "environmental_score_score") val environmentalScoreScore: Int? = null,
    @Json(name = "nutrition_grades") val nutritionGrades: String? = null,
    @Json(name = "allergens_tags") val allergensTags: List<String>? = null,
    @Json(name = "traces_tags") val tracesTags: List<String>? = null,
    @Json(name = "additives_tags") val additivesTags: List<String>? = null,
) {
    /** Same shape as a v2 product, so the existing mapper is reused unchanged. */
    fun toOffProductDto() = OffProductDto(
        productName = productName, productNameFr = productNameFr, genericNameFr = genericNameFr,
        brands = brands?.joinToString(", "), categoriesTags = categoriesTags,
        ingredientsTextFr = ingredientsTextFr, ingredientsText = ingredientsText, novaGroup = novaGroup,
        nutriments = nutriments, labelsTags = labelsTags, origins = origins, countriesTags = countriesTags,
        quantity = quantity, ecoscoreGrade = ecoscoreGrade, ecoscoreScore = ecoscoreScore,
        environmentalScoreGrade = environmentalScoreGrade, environmentalScoreScore = environmentalScoreScore,
        nutritionGrades = nutritionGrades, allergensTags = allergensTags, tracesTags = tracesTags,
        additivesTags = additivesTags, code = code,
    )
}

/** Lucene filter on one OFF category tag. */
fun salCategoryQuery(tag: String): String = "categories_tags:\"${tag.replace("\"", "")}\""
