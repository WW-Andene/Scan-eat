package fr.scanneat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// SHARED API MODELS
// Mirrors the request/response shapes from api/_lib.ts and ocr-parser.ts
// ============================================================================

// ---- Common input types ----

@Serializable
data class ImageDto(
    val base64: String,
    val mime: String = "image/jpeg",
)

// ---- /api/score ----

@Serializable
data class ScoreRequest(
    val images: List<ImageDto> = emptyList(),
    @SerialName("imageBase64") val imageBase64: String? = null,  // legacy single-image
    val mime: String? = null,
    val barcode: String? = null,
)

@Serializable
data class ScoreResponse(
    val product: ProductDto,
    val audit: AuditDto,
    val warnings: List<String>,
    val source: String,
    val barcode: String? = null,
)

// ---- /api/identify / identify-multi / identify-menu ----

@Serializable
data class ImagesRequest(
    val images: List<ImageDto> = emptyList(),
)

// ---- /api/suggest-recipes ----

@Serializable
data class SuggestRecipesRequest(
    val ingredient: String,
)

// ---- /api/suggest-from-pantry ----

@Serializable
data class SuggestFromPantryRequest(
    val pantry: List<String> = emptyList(),
)

// ---- Error ----

@Serializable
data class ErrorResponse(val error: String, val detail: String? = null)

// ---- Product / Audit (mirrors domain model, JSON-serializable for HTTP) ----

@Serializable
data class NutritionDto(
    @SerialName("energy_kcal")     val energyKcal: Double,
    @SerialName("fat_g")           val fatG: Double,
    @SerialName("saturated_fat_g") val saturatedFatG: Double,
    @SerialName("carbs_g")         val carbsG: Double,
    @SerialName("sugars_g")        val sugarsG: Double,
    @SerialName("added_sugars_g")  val addedSugarsG: Double? = null,
    @SerialName("fiber_g")         val fiberG: Double,
    @SerialName("protein_g")       val proteinG: Double,
    @SerialName("salt_g")          val saltG: Double,
    @SerialName("trans_fat_g")     val transFatG: Double? = null,
    @SerialName("iron_mg")         val ironMg: Double? = null,
    @SerialName("calcium_mg")      val calciumMg: Double? = null,
    @SerialName("magnesium_mg")    val magnesiumMg: Double? = null,
    @SerialName("potassium_mg")    val potassiumMg: Double? = null,
    @SerialName("zinc_mg")         val zincMg: Double? = null,
    @SerialName("vit_a_ug")        val vitAUg: Double? = null,
    @SerialName("vit_c_mg")        val vitCMg: Double? = null,
    @SerialName("vit_d_ug")        val vitDUg: Double? = null,
    @SerialName("vit_e_mg")        val vitEMg: Double? = null,
    @SerialName("b12_ug")          val b12Ug: Double? = null,
    @SerialName("omega_3_g")       val omega3G: Double? = null,
)

@Serializable
data class IngredientDto(
    val name: String,
    val percentage: Double? = null,
    @SerialName("e_number") val eNumber: String? = null,
    val category: String? = null,
    @SerialName("is_whole_food") val isWholeFood: Boolean? = null,
)

@Serializable
data class ProductDto(
    val name: String,
    val category: String,
    @SerialName("nova_class") val novaClass: Int,
    val ingredients: List<IngredientDto>,
    val nutrition: NutritionDto,
    val organic: Boolean = false,
    @SerialName("whole_grain_primary") val wholeGrainPrimary: Boolean = false,
    val fermented: Boolean = false,
    @SerialName("has_health_claims") val hasHealthClaims: Boolean = false,
    @SerialName("has_misleading_marketing") val hasMisleadingMarketing: Boolean = false,
    @SerialName("named_oils") val namedOils: Boolean? = null,
    val origin: String? = null,
    @SerialName("weight_g") val weightG: Double? = null,
    @SerialName("ecoscore_grade") val ecoscoreGrade: String? = null,
    @SerialName("ecoscore_value") val ecoscoreValue: Double? = null,
    @SerialName("nutriscore_grade") val nutriscoreGrade: String? = null,
)

@Serializable
data class DeductionDto(
    val pillar: String,
    val reason: String,
    val points: Double,
    val severity: String,
    val evidence: String? = null,
)

@Serializable
data class PillarDto(
    val name: String,
    val max: Int,
    val score: Double,
    val deductions: List<DeductionDto>,
    val bonuses: List<DeductionDto>,
)

@Serializable
data class VetoDto(
    val triggered: Boolean,
    val reason: String,
    val cap: Int,
)

@Serializable
data class PillarsDto(
    val processing: PillarDto,
    @SerialName("nutritional_density") val nutritionalDensity: PillarDto,
    @SerialName("negative_nutrients")  val negativeNutrients: PillarDto,
    @SerialName("additive_risk")       val additiveRisk: PillarDto,
    @SerialName("ingredient_integrity") val ingredientIntegrity: PillarDto,
)

@Serializable
data class AuditDto(
    @SerialName("product_name")   val productName: String,
    val category: String,
    val score: Int,
    val grade: String,
    val verdict: String,
    val pillars: PillarsDto,
    @SerialName("global_bonuses")   val globalBonuses: List<DeductionDto>,
    @SerialName("global_penalties") val globalPenalties: List<DeductionDto>,
    val veto: VetoDto,
    @SerialName("red_flags")    val redFlags: List<String>,
    @SerialName("green_flags")  val greenFlags: List<String>,
    @SerialName("engine_version") val engineVersion: String,
    val warnings: List<String>,
    @SerialName("nutriscore_grade") val nutriscoreGrade: String? = null,
)

// ---- /api/identify* responses ----

@Serializable
data class IdentifiedFoodResponse(
    val name: String,
    val category: String,
    @SerialName("nova_class") val novaClass: Int,
    val ingredients: List<IngredientDto> = emptyList(),
    val nutrition: NutritionDto,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class IdentifiedMultiFoodResponse(
    val items: List<IdentifiedFoodResponse>,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class MenuDishDto(
    val name: String,
    val description: String? = null,
    @SerialName("estimated_kcal") val estimatedKcal: Int? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
)

@Serializable
data class IdentifiedMenuResponse(
    val dishes: List<MenuDishDto>,
    val warnings: List<String> = emptyList(),
)

// ---- /api/identify-recipe ----

@Serializable
data class RecipeIngredientDto(
    val name: String,
    val quantity: String? = null,
    val unit: String? = null,
)

@Serializable
data class IdentifiedRecipeResponse(
    val name: String,
    val servings: Int? = null,
    val ingredients: List<RecipeIngredientDto>,
    val steps: List<String>,
    @SerialName("cook_time_min") val cookTimeMin: Int? = null,
    val warnings: List<String> = emptyList(),
)

// ---- /api/suggest-recipes / suggest-from-pantry ----

@Serializable
data class SuggestedRecipeDto(
    val name: String,
    val description: String,
    @SerialName("cook_time_min") val cookTimeMin: Int? = null,
    val difficulty: String? = null,
    @SerialName("main_ingredients") val mainIngredients: List<String> = emptyList(),
)

@Serializable
data class SuggestedRecipesResponse(
    val recipes: List<SuggestedRecipeDto>,
)

// ---- /api/fetch-recipe ----

@Serializable
data class FetchedRecipeResponse(
    val name: String,
    val servings: String? = null,
    val ingredients: List<String>,
    val steps: List<String>,
    @SerialName("cook_time_min") val cookTimeMin: Int? = null,
    val nutrition: FetchedNutritionDto? = null,
    @SerialName("source_url") val sourceUrl: String,
)

@Serializable
data class FetchedNutritionDto(
    val kcal: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("fat_g") val fatG: Double,
    @SerialName("carbs_g") val carbsG: Double,
)
