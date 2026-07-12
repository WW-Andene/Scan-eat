package fr.scanneat.routing

import fr.scanneat.model.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("FetchRecipeRoute")

// ============================================================================
// PER-IP RATE LIMIT — every other route requires a Groq key (the caller's own
// or the operator's), which at least ties usage to a credential. This route
// needs none ("No Groq key needed" below) and, until now, had no throttling
// of any kind either - it is a free, fully anonymous HTTP proxy (SSRF-guarded,
// but still egress-capable) that anyone who finds the server URL could hammer
// to scrape arbitrary sites through it or just to run up the operator's
// bandwidth/CPU. A simple fixed-window counter per client IP closes that
// without needing Ktor 3.x's RateLimit plugin (unavailable on 2.3.12).
// ============================================================================
private const val RATE_LIMIT_MAX_REQUESTS = 20
private const val RATE_LIMIT_WINDOW_MS = 60_000L

private class RateWindow(@Volatile var count: Int, @Volatile var windowStartMs: Long)

private val rateLimitState = ConcurrentHashMap<String, RateWindow>()

/** True when [clientKey] has exceeded [RATE_LIMIT_MAX_REQUESTS] in the current window. */
internal fun isFetchRecipeRateLimited(clientKey: String, nowMs: Long = System.currentTimeMillis()): Boolean {
    var limited = false
    rateLimitState.compute(clientKey) { _, existing ->
        if (existing == null || nowMs - existing.windowStartMs >= RATE_LIMIT_WINDOW_MS) {
            RateWindow(1, nowMs)
        } else {
            existing.count += 1
            limited = existing.count > RATE_LIMIT_MAX_REQUESTS
            existing
        }
    }
    return limited
}

// ============================================================================
// GET /api/fetch-recipe?url=<recipe-page-url>
//
// Proxies a recipe blog URL, extracts schema.org Recipe JSON-LD,
// and returns a compact object the client can pre-fill.
// Mirrors api/fetch-recipe.ts
//
// No Groq key needed — pure HTML fetch + parse.
// ============================================================================

private val httpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
        connectTimeoutMillis = 8_000
    }
    // Redirects are followed manually below so every hop gets the same
    // private-address check — automatic following would let a public URL
    // 302 the server into localhost or the cloud metadata endpoint.
    followRedirects = false
}

/**
 * True when [url] may be fetched: http(s) only, and the host must not resolve
 * to a loopback, private, link-local (incl. 169.254.169.254 metadata), or
 * IPv6 unique-local address. This route proxies arbitrary user-supplied URLs,
 * so without this check it is a straight SSRF hole into whatever network the
 * server runs on.
 *
 * Known residual gap: this resolves DNS here to validate, but the actual
 * request below (httpClient.get) resolves the same hostname again
 * independently when it connects. A host serving a public IP to this check
 * and then a private/loopback IP moments later on a short-TTL DNS record
 * (classic DNS-rebinding) would pass validation and still reach the private
 * address on the real request. Closing that fully requires resolving once
 * and connecting to the pinned IP directly (while still sending the correct
 * Host header / TLS SNI for the original hostname) - a bigger change than
 * this pass covers; flagging so it isn't mistaken for a complete SSRF fix.
 */
private fun isFetchableUrl(url: String): Boolean {
    val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (parsed.scheme != "http" && parsed.scheme != "https") return false
    val host = parsed.host ?: return false
    val addresses = runCatching { java.net.InetAddress.getAllByName(host) }.getOrNull() ?: return false
    return addresses.all { addr ->
        !addr.isLoopbackAddress && !addr.isSiteLocalAddress && !addr.isLinkLocalAddress &&
        !addr.isAnyLocalAddress && !addr.isMulticastAddress &&
        // IPv6 unique-local fc00::/7 — not covered by isSiteLocalAddress
        (addr.address.size != 16 || (addr.address[0].toInt() and 0xFE) != 0xFC)
    }
}

private const val MAX_REDIRECTS = 3

// A malicious/misconfigured server can stream far more than a real recipe
// page ever needs within the 8s timeout window; bodyAsText() would otherwise
// buffer all of it in memory from a single request.
private const val MAX_HTML_BYTES = 3L * 1024 * 1024

fun Route.fetchRecipeRoute() {
    get("/fetch-recipe") {
        val clientKey = call.request.origin.remoteHost
        if (isFetchRecipeRateLimited(clientKey)) {
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit"))
            return@get
        }
        val url = call.request.queryParameters["url"]
        if (url.isNullOrBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing url parameter"))
            return@get
        }
        if (!isFetchableUrl(url)) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("URL must be a public http(s) address"))
            return@get
        }

        try {
            var currentUrl = url
            var response = httpClient.get(currentUrl) {
                header("User-Agent", "ScanEat/0.1 (+https://github.com/scanneat)")
                header("Accept", "text/html,application/xhtml+xml")
            }
            var hops = 0
            while (response.status.value in 301..308 && hops < MAX_REDIRECTS) {
                val location = response.headers[HttpHeaders.Location] ?: break
                val next = java.net.URI(currentUrl).resolve(location).toString()
                if (!isFetchableUrl(next)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("URL must be a public http(s) address"))
                    return@get
                }
                currentUrl = next
                hops++
                response = httpClient.get(currentUrl) {
                    header("User-Agent", "ScanEat/0.1 (+https://github.com/scanneat)")
                    header("Accept", "text/html,application/xhtml+xml")
                }
            }
            val packet = response.bodyAsChannel().readRemaining(MAX_HTML_BYTES + 1)
            if (packet.remaining > MAX_HTML_BYTES) {
                packet.close()
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Recipe page too large"))
                return@get
            }
            val html = packet.readText()

            val recipe = parseSchemaOrgRecipe(html, url)
            if (recipe == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No schema.org Recipe found on this page"))
                return@get
            }
            call.respond(recipe)
        } catch (e: Exception) {
            log.error("[/api/fetch-recipe] $url", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}

// ============================================================================
// Schema.org parser — mirrors the TS implementation in fetch-recipe.ts
// ============================================================================

private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

// internal (not private) so tests can exercise the real extraction logic
// instead of asserting on a fixture string the test itself wrote.
internal fun parseSchemaOrgRecipe(html: String, sourceUrl: String): FetchedRecipeResponse? {
    // Extract all <script type="application/ld+json"> blocks
    val ldJsonBlocks = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE
    ).findAll(html).mapNotNull { m ->
        val raw = m.groupValues[1].trim()
        if (raw.isEmpty()) return@mapNotNull null
        runCatching { jsonParser.parseToJsonElement(raw) }.getOrNull()
            ?: runCatching {
                // Best-effort: unescape common HTML entities
                val cleaned = raw.replace("&quot;", "\"").replace("&amp;", "&").replace("&#34;", "\"")
                jsonParser.parseToJsonElement(cleaned)
            }.getOrNull()
    }.toList()

    // Find the first Recipe object (may be nested in @graph)
    val recipe = ldJsonBlocks.firstNotNullOfOrNull { findRecipe(it) } ?: return null

    return FetchedRecipeResponse(
        name        = stringOr(recipe["name"]),
        servings    = yieldToServings(recipe["recipeYield"])?.toString(),
        ingredients = stringArray(recipe["recipeIngredient"]),
        steps       = extractSteps(recipe["recipeInstructions"]),
        cookTimeMin = parseISODuration(recipe["totalTime"]) ?: parseISODuration(recipe["cookTime"]),
        nutrition   = extractNutrition(recipe["nutrition"]),
        sourceUrl   = sourceUrl,
    )
}

private fun findRecipe(element: JsonElement): JsonObject? {
    return when (element) {
        is JsonObject -> {
            val type = element["@type"]
            val isRecipe = type?.let {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull == "Recipe"
                    is JsonArray     -> it.any { e -> e is JsonPrimitive && e.contentOrNull == "Recipe" }
                    else -> false
                }
            } ?: false
            if (isRecipe) element
            else element["@graph"]?.let { findRecipe(it) }
        }
        is JsonArray  -> element.firstNotNullOfOrNull { findRecipe(it) }
        else          -> null
    }
}

private fun stringOr(element: JsonElement?, fallback: String = ""): String =
    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

private fun stringArray(element: JsonElement?): List<String> = when (element) {
    is JsonArray     -> element.mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            is JsonObject    -> (e["text"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            else             -> null
        }
    }
    is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
    else             -> emptyList()
}

private fun extractSteps(element: JsonElement?): List<String> {
    if (element == null) return emptyList()
    val list = when (element) {
        is JsonArray -> element.mapNotNull { s ->
            when (s) {
                is JsonPrimitive -> s.contentOrNull?.trim()
                is JsonObject    -> (s["text"] as? JsonPrimitive)?.contentOrNull?.trim()
                    ?: (s["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                else             -> null
            }?.takeIf { it.isNotEmpty() }
        }
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
        else             -> emptyList()
    }
    return list
}

/** Null when recipeYield is absent or unparsable — the caller must not
 *  fabricate a "1 serving" the source page never actually declared. */
private fun yieldToServings(element: JsonElement?): Int? {
    if (element == null) return null
    return when (element) {
        is JsonPrimitive -> {
            element.intOrNull ?: Regex("(\\d+)").find(element.contentOrNull ?: "")
                ?.groupValues?.get(1)?.toIntOrNull()
        }
        is JsonArray -> yieldToServings(element.firstOrNull())
        else -> null
    }
}

private fun parseISODuration(element: JsonElement?): Int? {
    val s = (element as? JsonPrimitive)?.contentOrNull ?: return null
    val m = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$").matchEntire(s) ?: return null
    val h = m.groupValues[1].toIntOrNull() ?: 0
    val min = m.groupValues[2].toIntOrNull() ?: 0
    val sec = m.groupValues[3].toIntOrNull() ?: 0
    return h * 60 + min + (sec / 60)
}

// schema.org nutrition values commonly carry units in the same string
// (e.g. "350 calories", "12g") — extract the leading numeric token instead
// of requiring the whole string to parse as a number, which previously
// discarded the entire nutrition block the moment any field had a unit.
private fun numFrom(element: JsonElement?): Double = when (element) {
    is JsonPrimitive -> element.doubleOrNull ?: run {
        val content = element.contentOrNull?.replace(",", ".") ?: ""
        content.toDoubleOrNull()
            ?: Regex("""(\d+(?:\.\d+)?)""").find(content)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 0.0
    }
    else -> 0.0
}

private fun extractNutrition(element: JsonElement?): FetchedNutritionDto? {
    val obj = element as? JsonObject ?: return null
    val kcal = numFrom(obj["calories"])
    if (kcal == 0.0) return null
    return FetchedNutritionDto(
        kcal      = kcal,
        proteinG  = numFrom(obj["proteinContent"]),
        fatG      = numFrom(obj["fatContent"]),
        carbsG    = numFrom(obj["carbohydrateContent"]),
    )
}
