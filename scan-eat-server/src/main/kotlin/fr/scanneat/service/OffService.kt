package fr.scanneat.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

// ============================================================================
// OFF SERVICE — Open Food Facts product lookup
// Mirrors fetchFromOFF() from src/off.ts
// ============================================================================

private val log = LoggerFactory.getLogger("OffService")

private val OFF_FIELDS = listOf(
    "product_name","product_name_fr","generic_name_fr","brands",
    "categories_tags","ingredients_text_fr","ingredients_text",
    "nova_group","nutriments","labels_tags","origins","countries_tags",
    "quantity","ecoscore_grade","ecoscore_score","nutrition_grades",
).joinToString(",")

private const val OFF_USER_AGENT = "ScanEat/0.1 (Ktor; +https://github.com/scanneat)"

@Serializable
data class OffResponse(
    val status: Int,
    val product: OffProductRaw? = null,
)

@Serializable
data class OffProductRaw(
    @SerialName("product_name")     val productName: String? = null,
    @SerialName("product_name_fr")  val productNameFr: String? = null,
    @SerialName("generic_name_fr")  val genericNameFr: String? = null,
    val brands: String? = null,
    @SerialName("categories_tags")  val categoriesTags: List<String>? = null,
    @SerialName("ingredients_text_fr") val ingredientsTextFr: String? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    @SerialName("nova_group")       val novaGroup: Int? = null,
    val nutriments: JsonObject? = null,
    @SerialName("labels_tags")      val labelsTags: List<String>? = null,
    val origins: String? = null,
    @SerialName("countries_tags")   val countriesTags: List<String>? = null,
    val quantity: String? = null,
    @SerialName("ecoscore_grade")   val ecoscoreGrade: String? = null,
    @SerialName("ecoscore_score")   val ecoscoreScore: Int? = null,
    @SerialName("nutrition_grades") val nutritionGrades: String? = null,
)

class OffService {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 4_000
        }
    }

    /**
     * Fetch a product from OFF by barcode. Returns null on miss or network error.
     * Response is the raw OFF JSON object — mapping to domain types happens in
     * the shared OffMapper logic (same as on Android).
     */
    suspend fun fetchProduct(barcode: String): OffProductRaw? {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length !in 8..14) return null

        return runCatching {
            val resp: OffResponse = client.get("https://world.openfoodfacts.org/api/v2/product/$digits.json") {
                parameter("fields", OFF_FIELDS)
                parameter("lc", "fr")
                header("User-Agent", OFF_USER_AGENT)
                header("Accept", "application/json")
            }.body()
            if (resp.status == 1) resp.product else null
        }.onFailure { log.warn("OFF lookup failed for $barcode: ${it.message}") }.getOrNull()
    }

    fun close() = client.close()
}
