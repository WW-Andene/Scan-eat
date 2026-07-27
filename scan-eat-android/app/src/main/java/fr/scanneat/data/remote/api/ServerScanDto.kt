package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ============================================================================
// SERVER SCAN DTOs — request/response shapes for the ServerScanApi Retrofit
// interface. Mirrors the corresponding models on the Ktor backend exactly.
// ============================================================================

// ---- /api/fetch-recipe ----

@JsonClass(generateAdapter = true)
data class ServerFetchedRecipeResponse(
    val name: String,
    val servings: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    @Json(name = "cook_time_min") val cookTimeMin: Int? = null,
    val nutrition: ServerFetchedNutritionDto? = null,
    @Json(name = "source_url") val sourceUrl: String = "",
)

@JsonClass(generateAdapter = true)
data class ServerFetchedNutritionDto(
    val kcal: Double = 0.0,
    @Json(name = "protein_g") val proteinG: Double = 0.0,
    @Json(name = "fat_g") val fatG: Double = 0.0,
    @Json(name = "carbs_g") val carbsG: Double = 0.0,
)

// ---- /api/identify-recipe ----

@JsonClass(generateAdapter = true)
data class ServerRecipeIngredientDto(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
)

@JsonClass(generateAdapter = true)
data class ServerIdentifiedRecipeResponse(
    val name: String,
    val servings: Int? = null,
    val ingredients: List<ServerRecipeIngredientDto> = emptyList(),
    val steps: List<String> = emptyList(),
    @Json(name = "cook_time_min") val cookTimeMin: Int? = null,
    val warnings: List<String> = emptyList(),
)

// ---- /api/suggest-recipes / suggest-from-pantry ----

@JsonClass(generateAdapter = true)
data class ServerSuggestRecipesRequest(val ingredient: String)

@JsonClass(generateAdapter = true)
data class ServerSuggestFromPantryRequest(val pantry: List<String> = emptyList())

@JsonClass(generateAdapter = true)
data class ServerSuggestedRecipeDto(
    val name: String,
    val description: String,
    @Json(name = "cook_time_min") val cookTimeMin: Int? = null,
    val difficulty: String? = null,
    @Json(name = "main_ingredients") val mainIngredients: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ServerSuggestedRecipesResponse(
    val recipes: List<ServerSuggestedRecipeDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ServerImagesRequest(
    val images: List<ServerImageDto> = emptyList(),
    val lang: String? = null,
)

/** Mirrors IdentifiedFoodResponse from the server - no score/audit, scored locally after mapping. */
@JsonClass(generateAdapter = true)
data class ServerIdentifyResponse(
    val name: String,
    val category: String,
    @Json(name = "nova_class") val novaClass: Int,
    val ingredients: List<ServerIngredientDto> = emptyList(),
    val nutrition: ServerNutritionDto,
    // Previously absent from this DTO - the server started serializing all of these
    // on IdentifiedFoodResponse (see ApiModels.kt), but this client-side shape had
    // nowhere to receive them, so every server-mode photo-identify scan silently
    // rescored with organic/fermented/etc. and the allergen/micronutrient
    // declarations all at their zero-value default. Mirrors ServerProductDto.
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
    @Json(name = "declared_micronutrients") val declaredMicronutrients: List<String> = emptyList(),
    @Json(name = "declared_allergen_tags") val declaredAllergenTags: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Mirrors IdentifiedMultiFoodResponse - one [ServerIdentifyResponse]-shaped entry per detected food on the plate. */
@JsonClass(generateAdapter = true)
data class ServerIdentifyMultiResponse(
    val items: List<ServerIdentifyResponse> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Mirrors MenuDishDto - a restaurant menu photo → one entry per dish, estimates only (no scoring, no ingredients). */
@JsonClass(generateAdapter = true)
data class ServerMenuDishDto(
    val name: String,
    val description: String? = null,
    @Json(name = "estimated_kcal") val estimatedKcal: Int? = null,
    @Json(name = "protein_g") val proteinG: Double? = null,
)

/** Mirrors IdentifiedMenuResponse. */
@JsonClass(generateAdapter = true)
data class ServerIdentifyMenuResponse(
    val dishes: List<ServerMenuDishDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

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
    // Mirrors the server's ScoreResponse.nonFoodCategory/nonFoodBrand - see
    // ScanRepository's NonFoodProductException / scoreViaServer for how this
    // is consumed.
    val nonFoodCategory: String? = null,
    val nonFoodBrand: String? = null,
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
    @Json(name = "sodium_mg")       val sodiumMg: Double? = null,
    @Json(name = "vit_c_mg")        val vitCMg: Double? = null,
    @Json(name = "vit_d_ug")        val vitDUg: Double? = null,
    @Json(name = "vit_a_ug")        val vitAUg: Double? = null,
    @Json(name = "vit_e_mg")        val vitEMg: Double? = null,
    @Json(name = "vit_k_ug")        val vitKUg: Double? = null,
    // b1/b2/b3/b6/b9 - matches the server's NutritionDto (ApiModels.kt), which
    // previously had nowhere to carry these five vitamins across the wire either;
    // both sides needed the matching fields for a Server-mode scan to ever surface
    // the same micronutrients Direct-mode's OffMapper.kt already maps.
    @Json(name = "b1_mg")           val b1Mg: Double? = null,
    @Json(name = "b2_mg")           val b2Mg: Double? = null,
    @Json(name = "b3_mg")           val b3Mg: Double? = null,
    @Json(name = "b6_mg")           val b6Mg: Double? = null,
    @Json(name = "b9_ug")           val b9Ug: Double? = null,
    @Json(name = "b12_ug")          val b12Ug: Double? = null,
    @Json(name = "omega_3_g")       val omega3G: Double? = null,
    @Json(name = "omega_6_g")       val omega6G: Double? = null,
    @Json(name = "cholesterol_mg")  val cholesterolMg: Double? = null,
    @Json(name = "polyunsaturated_fat_g") val polyunsaturatedFatG: Double? = null,
    @Json(name = "monounsaturated_fat_g") val monounsaturatedFatG: Double? = null,
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
