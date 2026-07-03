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
)

@Serializable
data class ScoreResponse(
    val product: ProductInput,
    val audit: ScoreAudit,
    val warnings: List<String> = emptyList(),
    val source: String,
    val barcode: String? = null,
)
