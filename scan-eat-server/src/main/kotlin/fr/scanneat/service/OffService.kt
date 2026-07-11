package fr.scanneat.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
     * Fetch a product from OFF by barcode, retrying against every plausible
     * alternate encoding of the same GTIN on a miss. Scanners hand back the
     * code as printed — 12-digit UPC-A, 13-digit EAN-13, compressed UPC-E on
     * small packaging, GTIN-14 case codes — but OFF only indexes the expanded
     * UPC-A/EAN-13 form. Mirrors ScanRepository's Direct-mode candidate logic
     * on Android; without this, Server mode still had the original
     * cans-scan-as-not-found bug that Direct mode fixed.
     *
     * Candidates are fired concurrently but awaited in priority order, so a
     * lower-priority candidate that happens to respond faster never wins over
     * an earlier, more-likely one. Returns null on miss or network error.
     */
    suspend fun fetchProduct(barcode: String): OffProductRaw? = coroutineScope {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length !in 6..14) return@coroutineScope null

        val pending = barcodeCandidates(digits).map { code -> async { fetchExact(code) } }
        for (deferred in pending) {
            deferred.await()?.let { return@coroutineScope it }
        }
        null
    }

    private suspend fun fetchExact(code: String): OffProductRaw? = runCatching {
        val resp: OffResponse = client.get("https://world.openfoodfacts.org/api/v2/product/$code.json") {
            parameter("fields", OFF_FIELDS)
            parameter("lc", "fr")
            header("User-Agent", OFF_USER_AGENT)
            header("Accept", "application/json")
        }.body()
        if (resp.status == 1) resp.product else null
    }.onFailure { log.warn("OFF lookup failed for $code: ${it.message}") }.getOrNull()

    fun close() = client.close()
}

// ============================================================================
// GTIN candidate expansion — manual sync of ScanRepository's Android logic
// ============================================================================

/** Every plausible GTIN encoding for [barcode], most-likely-first, deduplicated. */
internal fun barcodeCandidates(barcode: String): List<String> {
    val candidates = LinkedHashSet<String>()
    candidates += barcode

    upcEToUpcA(barcode)?.let { upcA ->
        candidates += upcA
        candidates += "0$upcA" // EAN-13 form of the expanded UPC-A
    }

    gtin14ToEan13(barcode)?.let { ean13 ->
        candidates += ean13
        if (ean13.startsWith("0")) candidates += ean13.substring(1) // UPC-A form
    }

    when {
        barcode.length == 12 -> candidates += "0$barcode"
        barcode.length == 13 && barcode.startsWith("0") -> candidates += barcode.substring(1)
    }

    return candidates.toList()
}

/**
 * Strips a GTIN-14 case/pallet code's leading packaging-indicator digit down
 * to the consumer-unit EAN-13, recomputing the check digit over the remaining
 * payload — case codes aren't indexed in OFF under their 14-digit form.
 */
private fun gtin14ToEan13(code: String): String? {
    if (code.length != 14 || !code.all { it.isDigit() }) return null
    val payload12 = code.substring(1, 13)
    return "0$payload12" + ean13CheckDigit(payload12)
}

/** Standard mod-10 EAN-13 check digit for a 12-digit payload. */
private fun ean13CheckDigit(payload12: String): Int {
    val sum = payload12.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 1 else 3 }.sum()
    return (10 - (sum % 10)) % 10
}

/**
 * Expands a compressed UPC-E code (6 core digits, optionally with a leading
 * number-system digit and/or trailing check digit) to its 12-digit UPC-A form,
 * per the standard GS1 expansion rules keyed on the last core digit. Only used
 * as a fallback after the code as printed already missed, so a false-positive
 * guess just fails its own lookup too.
 */
private fun upcEToUpcA(code: String): String? {
    if (!code.all { it.isDigit() }) return null
    val (numberSystem, core) = when (code.length) {
        6 -> '0' to code
        7 -> '0' to code.take(6)
        8 -> code[0] to code.substring(1, 7)
        else -> return null
    }
    if (numberSystem != '0' && numberSystem != '1') return null

    val d = core.map { it - '0' }
    val (manufacturer, product) = when (d[5]) {
        0, 1, 2 -> "${d[0]}${d[1]}${d[5]}00" to "00${d[2]}${d[3]}${d[4]}"
        3       -> "${d[0]}${d[1]}${d[2]}00" to "000${d[3]}${d[4]}"
        4       -> "${d[0]}${d[1]}${d[2]}${d[3]}0" to "0000${d[4]}"
        else    -> "${d[0]}${d[1]}${d[2]}${d[3]}${d[4]}" to "0000${d[5]}"
    }
    val upcA11 = "$numberSystem$manufacturer$product"
    return upcA11 + upcCheckDigit(upcA11)
}

/** Standard mod-10 UPC/EAN check digit for an 11-digit UPC-A payload. */
private fun upcCheckDigit(payload11: String): Int {
    val sum = payload11.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
    return (10 - (sum % 10)) % 10
}
