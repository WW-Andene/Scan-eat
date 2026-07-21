package fr.scanneat.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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
    "allergens_tags","additives_tags",
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
    @SerialName("allergens_tags")   val allergensTags: List<String>? = null,
    @SerialName("additives_tags")   val additivesTags: List<String>? = null,
)

// engine defaults to the real CIO engine for every production call site
// (Application.kt's `OffService()`) - the parameter exists purely so tests can
// substitute a MockEngine instead of hitting the real Open Food Facts API over
// the network. See OffServiceTest.kt.
class OffService(engine: HttpClientEngine = CIO.create()) {

    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 4_000
            connectTimeoutMillis = 4_000
        }
    }

    // ------------------------------------------------------------------
    // Short-TTL result cache, keyed on the original (un-normalized) barcode.
    // OFF product data is near-static (packaged goods don't get relabeled
    // mid-day) but /api/score is hit repeatedly for the same barcode - a user
    // rescanning a product they just looked at, or re-opening its result
    // screen - and each hit previously re-fired the full candidate-expansion
    // fan-out (up to ~4 concurrent HTTP requests to openfoodfacts.org) for an
    // answer that hadn't changed. Misses are cached too (as null) so a
    // not-found barcode doesn't keep re-paying the full fan-out either.
    // ------------------------------------------------------------------
    private data class CacheEntry(val product: OffProductRaw?, val expiresAtMs: Long)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTtlMs = 10 * 60 * 1000L // 10 minutes

    // Guards the check-then-clear eviction below - `cache` being a ConcurrentHashMap
    // only makes each individual get/put atomic, not the "if size >= max then clear"
    // sequence itself. Without this lock, many concurrent fetchProduct() calls near
    // the size threshold could each observe size >= maxCacheEntries and all call
    // clear() around the same time, repeatedly wiping entries other coroutines just
    // inserted (a cache-cold stampede) instead of evicting once.
    private val cacheEvictionLock = Any()

    // /api/score accepts any 6-14 digit barcode string from anonymous callers, and a
    // distinct barcode never hits the TTL-based reuse above - only a size cap actually
    // bounds memory, the same way AdditivesDb.kt's lookup cache is capped (this one
    // used to have no size limit at all, only a TTL that never fires for keys that
    // are never looked up again).
    private val maxCacheEntries = 20_000

    // Owns in-flight fetches independently of any individual caller's own request
    // coroutine - a caller cancelling (client disconnect) must not tear down a fetch
    // other concurrent callers for the SAME barcode are still awaiting the result of.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // A burst of simultaneous callers for the same not-yet-cached barcode (e.g. a
    // trending product hit by many clients in the same instant, all arriving before
    // the first one has finished and populated `cache`) previously each independently
    // fired their own ~4-candidate OFF fan-out - the TTL cache above only helps
    // *sequential* repeat lookups, not concurrent ones racing before any result
    // exists yet. This coalesces them into one shared underlying fetch.
    private val inFlight = ConcurrentHashMap<String, Deferred<OffProductRaw?>>()

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
     *
     * Results (hits and misses) are cached for [cacheTtlMs] to avoid refiring
     * the whole candidate fan-out for repeat lookups of the same barcode, and
     * concurrent lookups of the same not-yet-cached barcode share one fetch.
     */
    suspend fun fetchProduct(barcode: String): OffProductRaw? {
        val digits = barcode.filter { it.isDigit() }
        if (digits.length !in 6..14) return null

        val now = System.currentTimeMillis()
        cache[digits]?.let { entry ->
            if (entry.expiresAtMs > now) return entry.product
        }

        val deferred = inFlight.computeIfAbsent(digits) { backgroundScope.async { fetchAndCache(digits, now) } }
        return try {
            deferred.await()
        } finally {
            inFlight.remove(digits, deferred)
        }
    }

    private suspend fun fetchAndCache(digits: String, now: Long): OffProductRaw? = coroutineScope {
        val pending = barcodeCandidates(digits).map { code -> async { fetchExact(code) } }
        var found: OffProductRaw? = null
        for (deferred in pending) {
            val result = deferred.await()
            if (result != null) { found = result; break }
        }
        // coroutineScope only returns once every child job has completed - breaking
        // out of the loop above on the first match still left every later candidate
        // running to completion in the background, so fetchProduct didn't actually
        // return until the slowest candidate finished too. Cancelling whatever's left
        // (a no-op for jobs that already completed) lets a fast match return promptly.
        pending.forEach { it.cancel() }
        synchronized(cacheEvictionLock) {
            if (cache.size >= maxCacheEntries) cache.clear()
        }
        cache[digits] = CacheEntry(found, now + cacheTtlMs)
        found
    }

    private suspend fun fetchExact(code: String): OffProductRaw? = runCatching {
        val resp: OffResponse = client.get("https://world.openfoodfacts.org/api/v2/product/$code.json") {
            parameter("fields", OFF_FIELDS)
            parameter("lc", "fr")
            header("User-Agent", OFF_USER_AGENT)
            header("Accept", "application/json")
        }.body()
        if (resp.status == 1) resp.product else null
    }.onFailure { e ->
        // Cancelling a losing candidate (see the first-match-wins cancel() above, or a
        // client disconnect) must propagate as a real cancellation, not be swallowed
        // into a plain "lookup failed" null - getOrNull() below would otherwise let the
        // enclosing async{} job complete "successfully" with null instead of
        // cooperatively cancelling like the rest of the coroutine tree expects.
        if (e is CancellationException) throw e
        log.warn("OFF lookup failed for $code: ${e.message}")
    }.getOrNull()

    fun close() {
        backgroundScope.cancel()
        client.close()
    }
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
