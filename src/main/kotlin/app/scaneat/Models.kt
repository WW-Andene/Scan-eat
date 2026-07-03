package app.scaneat

import kotlinx.serialization.Serializable

@Serializable
data class ImagePayload(val base64: String, val mime: String = "image/jpeg")

@Serializable
data class ScoreRequest(
    val images: List<ImagePayload> = emptyList(),
    val imageBase64: String? = null,
    val mime: String? = null,
    val barcode: String? = null,
    val product: ProductInput? = null,
    val preferences: UserPreferences = UserPreferences(),
)

@Serializable
data class ProductInput(
    val name: String = "Produit inconnu",
    val category: String = "other",
    val nova_class: Int = 4,
    val weight_g: Double? = null,
    val organic: Boolean = false,
    val ingredients: List<Ingredient> = emptyList(),
    val nutrition: NutritionPer100g = NutritionPer100g(),
    val barcode: String? = null,
)

@Serializable
data class Ingredient(
    val name: String,
    val percentage: Double? = null,
    val e_number: String? = null,
    val category: String? = null,
)

@Serializable
data class NutritionPer100g(
    val energy_kcal: Double? = null,
    val fat_g: Double? = null,
    val saturated_fat_g: Double? = null,
    val carbs_g: Double? = null,
    val sugars_g: Double? = null,
    val fiber_g: Double? = null,
    val protein_g: Double? = null,
    val salt_g: Double? = null,
)

@Serializable
data class ScoreAudit(
    val score: Int,
    val grade: String,
    val pillars: Map<String, Int>,
    val positives: List<String>,
    val warnings: List<String>,
    val engineVersion: String = ScoringEngine.ENGINE_VERSION,
    val allergens: List<String> = emptyList(),
    val scoringNote: String = "Kotlin fallback scorer",
    val source: ScoringSource = ScoringSource.HYBRID,
    val confidenceScore: Float = 1.0f,
    val personalScore: PersonalScore = PersonalScore(),
)

@Serializable
data class UserPreferences(
    val diets: List<String> = emptyList(),
    val allergens: List<String> = emptyList(),
)

@Serializable
data class PersonalScore(
    val scoreCap: Int? = null,
    val warnings: List<String> = emptyList(),
    val matchedAllergens: List<String> = emptyList(),
)

@Serializable
enum class ScoringSource {
    OFF_DATABASE,
    LLM_OCR,
    MANUAL_ENTRY,
    HYBRID,
    FALLBACK,
}

@Serializable
data class ScoreResponse(
    val product: ProductInput,
    val audit: ScoreAudit,
    val warnings: List<String> = emptyList(),
    val source: String,
    val barcode: String? = null,
)
