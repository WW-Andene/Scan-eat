package app.scaneat

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ExternalClients(private val http: HttpClient = defaultClient()) {
    suspend fun productFromBarcode(barcode: String): ProductInput? {
        val response: OffResponse = http.get("https://world.openfoodfacts.org/api/v2/product/$barcode.json") {
            parameter("fields", "product_name,categories_tags,nova_group,nutriments,ingredients_text,ingredients")
        }.body()
        if (response.status != 1 || response.product == null) return null
        val p = response.product
        return ProductInput(
            name = p.productName ?: "Produit $barcode",
            category = inferCategory(p.categoriesTags),
            nova_class = p.novaGroup ?: 4,
            ingredients = p.ingredients?.map { Ingredient(it.text ?: it.id ?: "ingrédient", e_number = it.id?.takeIf { id -> id.startsWith("en:e") }?.removePrefix("en:")?.uppercase()) }
                ?: parseIngredients(p.ingredientsText),
            nutrition = NutritionPer100g(
                energy_kcal = p.nutriments?.energyKcal100g,
                fat_g = p.nutriments?.fat100g,
                saturated_fat_g = p.nutriments?.saturatedFat100g,
                carbs_g = p.nutriments?.carbohydrates100g,
                sugars_g = p.nutriments?.sugars100g,
                fiber_g = p.nutriments?.fiber100g,
                protein_g = p.nutriments?.proteins100g,
                salt_g = p.nutriments?.salt100g,
            ),
            barcode = barcode,
        )
    }

    fun close() = http.close()

    companion object {
        fun defaultClient() = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
    }
}

fun normalizeImages(req: ScoreRequest): List<ImagePayload> = when {
    req.images.isNotEmpty() -> req.images.filter { it.base64.isNotBlank() }
    !req.imageBase64.isNullOrBlank() -> listOf(ImagePayload(req.imageBase64, req.mime ?: "image/jpeg"))
    else -> emptyList()
}

fun parseIngredients(text: String?): List<Ingredient> = text.orEmpty().split(',', ';').mapNotNull { raw ->
    val name = raw.trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
    val eNumber = Regex("\\bE\\s?([0-9]{3}[a-z]?)\\b", RegexOption.IGNORE_CASE).find(name)?.let { "E${it.groupValues[1].uppercase()}" }
    Ingredient(name = name, e_number = eNumber, category = if (eNumber != null) "additive" else null)
}

private fun inferCategory(tags: List<String>?): String {
    val joined = tags.orEmpty().joinToString(" ")
    return when {
        "yogurt" in joined || "yaourts" in joined -> "yogurt"
        "cheese" in joined || "fromages" in joined -> "cheese"
        "bread" in joined || "pains" in joined -> "bread"
        "beverage" in joined || "boissons" in joined -> "beverage_soft"
        "meat" in joined || "viandes" in joined -> "processed_meat"
        else -> "other"
    }
}

@Serializable private data class OffResponse(val status: Int = 0, val product: OffProduct? = null)
@Serializable private data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
    @SerialName("nova_group") val novaGroup: Int? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    val ingredients: List<OffIngredient>? = null,
    val nutriments: OffNutriments? = null,
)
@Serializable private data class OffIngredient(val id: String? = null, val text: String? = null)
@Serializable private data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("saturated-fat_100g") val saturatedFat100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null,
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("salt_100g") val salt100g: Double? = null,
)
