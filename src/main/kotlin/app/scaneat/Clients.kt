package app.scaneat

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ExternalClients(private val http: HttpClient = defaultClient()) {
    suspend fun productFromBarcode(barcode: String): ProductLookup {
        val digits = barcode.filter(Char::isDigit)
        if (digits.isBlank()) return ProductLookup(null, listOf("Code-barres vide"), "invalid_barcode")
        return try {
            retrying("Open Food Facts barcode lookup") {
                val response = http.get("https://world.openfoodfacts.org/api/v2/product/$digits.json") {
                    parameter("fields", "product_name,categories_tags,nova_group,nutriments,ingredients_text,ingredients")
                }
                if (!response.status.isSuccess()) {
                    return@retrying ProductLookup(
                        null,
                        listOf("Open Food Facts a répondu HTTP ${response.status.value}"),
                        "openfoodfacts_error",
                    )
                }
                val off: OffResponse = response.body()
                if (off.status != 1 || off.product == null) {
                    return@retrying ProductLookup(
                        null,
                        listOf("Code-barres non trouvé dans Open Food Facts"),
                        "openfoodfacts_not_found",
                    )
                }
                ProductLookup(mapOFFProduct(off.product, digits), source = "openfoodfacts")
            }
        } catch (e: Throwable) {
            ProductLookup(null, listOf("Recherche Open Food Facts indisponible: ${e.message ?: "erreur réseau"}"), "openfoodfacts_error")
        }
    }

    suspend fun proxyOrUnavailable(route: String, rawJsonBody: String): Map<String, Any?> {
        val upstream = System.getenv("SCANEAT_UPSTREAM_URL")?.trim()?.trimEnd('/')
        if (upstream.isNullOrBlank()) {
            return mapOf(
                "error" to "service_unavailable",
                "warnings" to listOf("LLM recipe/menu features require SCANEAT_UPSTREAM_URL or the TypeScript API runtime"),
            )
        }
        return retrying("proxy /api/$route") {
            val response = http.post("$upstream/api/$route") {
                contentType(ContentType.Application.Json)
                setBody(rawJsonBody)
            }
            mapOf(
                "status" to response.status.value,
                "proxied" to true,
                "body" to response.bodyAsText(),
            )
        }
    }

    private suspend fun <T> retrying(operation: String, block: suspend () -> T): T {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try { return block() } catch (e: Throwable) {
                last = e
                if (attempt < 2) kotlinx.coroutines.delay(500L * (1L shl attempt))
            }
        }
        throw IllegalStateException("$operation failed after retries: ${last?.message}", last)
    }

    fun close() = http.close()

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun defaultClient() = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
    }
}

private fun mapOFFProduct(p: OffProduct, barcode: String) = ProductInput(
    name = p.productName?.takeIf { it.isNotBlank() } ?: "Produit $barcode",
    category = inferCategory(p.categoriesTags),
    nova_class = p.novaGroup?.coerceIn(1, 4) ?: 4,
    ingredients = p.ingredients
        ?.mapNotNull { offIngredient ->
            val name = offIngredient.text ?: offIngredient.id ?: return@mapNotNull null
            val eNumber = offIngredient.id
                ?.takeIf { id -> Regex("^en:e[0-9]{3}[a-z]?$", RegexOption.IGNORE_CASE).matches(id) }
                ?.removePrefix("en:")
                ?.uppercase()
            Ingredient(name, e_number = eNumber, category = if (eNumber != null) "additive" else null)
        }
        ?.takeIf { it.isNotEmpty() }
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
