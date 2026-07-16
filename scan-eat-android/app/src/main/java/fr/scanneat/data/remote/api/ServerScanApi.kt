package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

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
}

@JsonClass(generateAdapter = true)
data class ServerScoreRequest(
    val images: List<ServerImageDto> = emptyList(),
    val barcode: String? = null,
    val lang: String? = null,
    val model: String? = null,
)

@JsonClass(generateAdapter = true)
data class ServerImageDto(
    val base64: String,
    val mime: String = "image/jpeg",
)

/** Minimal response shape — mirrors ScoreResponse from the server. */
@JsonClass(generateAdapter = true)
data class ServerScoreResponse(
    val product: ServerProductDto,
    val audit: ServerAuditDto,
    val warnings: List<String> = emptyList(),
    val source: String,
    val barcode: String? = null,
)

@JsonClass(generateAdapter = true)
data class ServerProductDto(
    val name: String,
    val category: String,
    @Json(name = "nova_class") val novaClass: Int,
    val ingredients: List<ServerIngredientDto> = emptyList(),
    val nutrition: ServerNutritionDto,
    val organic: Boolean = false,
    @Json(name = "whole_grain_primary") val wholeGrainPrimary: Boolean = false,
    val fermented: Boolean = false,
    @Json(name = "has_health_claims") val hasHealthClaims: Boolean = false,
    @Json(name = "has_misleading_marketing") val hasMisleadingMarketing: Boolean = false,
    @Json(name = "named_oils") val namedOils: Boolean? = null,
    val origin: String? = null,
    @Json(name = "weight_g") val weightG: Double? = null,
    @Json(name = "ecoscore_grade") val ecoscoreGrade: String? = null,
    @Json(name = "ecoscore_value") val ecoscoreValue: Double? = null,
    @Json(name = "nutriscore_grade") val nutriscoreGrade: String? = null,
    // Previously absent from this DTO entirely - the server started serializing
    // both (see ApiModels.kt's ProductDto) but this client-side shape never had
    // anywhere to receive them, so a server-mode scan's Product always came back
    // with both fields at their empty-list default regardless of what the server
    // actually knew, silently disabling AllergenDetector's OFF-tag augmentation
    // and the iron-declared SEX personal-score bonus for every server-mode user.
    @Json(name = "declared_micronutrients") val declaredMicronutrients: List<String> = emptyList(),
    @Json(name = "declared_allergen_tags") val declaredAllergenTags: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ServerIngredientDto(
    val name: String,
    val percentage: Double? = null,
    @Json(name = "e_number") val eNumber: String? = null,
    val category: String? = null,
    @Json(name = "is_whole_food") val isWholeFood: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ServerNutritionDto(
    @Json(name = "energy_kcal")     val energyKcal: Double = 0.0,
    @Json(name = "fat_g")           val fatG: Double = 0.0,
    @Json(name = "saturated_fat_g") val saturatedFatG: Double = 0.0,
    @Json(name = "carbs_g")         val carbsG: Double = 0.0,
    @Json(name = "sugars_g")        val sugarsG: Double = 0.0,
    @Json(name = "added_sugars_g")  val addedSugarsG: Double? = null,
    @Json(name = "fiber_g")         val fiberG: Double = 0.0,
    @Json(name = "protein_g")       val proteinG: Double = 0.0,
    @Json(name = "salt_g")          val saltG: Double = 0.0,
    @Json(name = "trans_fat_g")     val transFatG: Double? = null,
    @Json(name = "iron_mg")         val ironMg: Double? = null,
    @Json(name = "calcium_mg")      val calciumMg: Double? = null,
    @Json(name = "magnesium_mg")    val magnesiumMg: Double? = null,
    @Json(name = "potassium_mg")    val potassiumMg: Double? = null,
    @Json(name = "zinc_mg")         val zincMg: Double? = null,
    @Json(name = "vit_c_mg")        val vitCMg: Double? = null,
    @Json(name = "vit_d_ug")        val vitDUg: Double? = null,
    @Json(name = "vit_a_ug")        val vitAUg: Double? = null,
    @Json(name = "vit_e_mg")        val vitEMg: Double? = null,
    @Json(name = "vit_k_ug")        val vitKUg: Double? = null,
    @Json(name = "b12_ug")          val b12Ug: Double? = null,
    @Json(name = "omega_3_g")       val omega3G: Double? = null,
)

@JsonClass(generateAdapter = true)
data class ServerAuditDto(
    @Json(name = "product_name")   val productName: String,
    val category: String,
    val score: Int,
    val grade: String,
    val verdict: String,
    @Json(name = "red_flags")    val redFlags: List<String> = emptyList(),
    @Json(name = "green_flags")  val greenFlags: List<String> = emptyList(),
    @Json(name = "engine_version") val engineVersion: String = "",
    val warnings: List<String> = emptyList(),
    // Pillars present but large — omitted for brevity; add if needed by UI
)
