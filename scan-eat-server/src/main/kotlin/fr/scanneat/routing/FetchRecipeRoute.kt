package fr.scanneat.routing

import fr.scanneat.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger("FetchRecipeRoute")

// ============================================================================
// PER-IP RATE LIMIT — every other route requires a Groq key (the caller's own
// or the operator's), which at least ties usage to a credential. This route
// needs none ("No Groq key needed" below) - it is a free, fully anonymous HTTP
// proxy (SSRF-guarded, but still egress-capable) that anyone who finds the
// server URL could hammer to scrape arbitrary sites through it or just to run
// up the operator's bandwidth/CPU. Uses the shared RateLimiter (RateLimiter.kt)
// with a dedicated instance/budget, since this route's cost profile (outbound
// HTML fetch) differs from the LLM-calling routes' shared budget.
// ============================================================================
private val fetchRecipeRateLimiter = RateLimiter(maxRequests = 20, windowMs = 60_000L)

/** True when [clientKey] has exceeded the fetch-recipe budget in the current window. */
internal fun isFetchRecipeRateLimited(clientKey: String, nowMs: Long = System.currentTimeMillis()): Boolean =
    fetchRecipeRateLimiter.isLimited(clientKey, nowMs)

// ============================================================================
// GET /api/fetch-recipe?url=<recipe-page-url>
//
// Proxies a recipe blog URL, extracts schema.org Recipe JSON-LD,
// and returns a compact object the client can pre-fill.
// Mirrors api/fetch-recipe.ts
//
// No Groq key needed — pure HTML fetch + parse.
// ============================================================================

/**
 * True when [addr] is a public, routable address — not loopback, private,
 * link-local (incl. 169.254.169.254, the cloud metadata endpoint), or IPv6
 * unique-local. Shared by the fast pre-check below and the OkHttp [Dns]
 * override that actually governs the outbound connection, so both enforce
 * the identical definition of "safe" with no risk of the two drifting apart.
 */
// internal (not private) so tests can exercise the real address/DNS guard
// logic instead of asserting on a fixture the test itself wrote.
internal fun isPublicAddress(addr: InetAddress): Boolean =
    !addr.isLoopbackAddress && !addr.isSiteLocalAddress && !addr.isLinkLocalAddress &&
    !addr.isAnyLocalAddress && !addr.isMulticastAddress &&
    // IPv6 unique-local fc00::/7 — not covered by isSiteLocalAddress
    (addr.address.size != 16 || (addr.address[0].toInt() and 0xFE) != 0xFC)

/**
 * This route proxies arbitrary user-supplied URLs, so it needs to resolve the
 * hostname and confirm every address is public before connecting — otherwise
 * it's a straight SSRF hole into whatever network the server runs on.
 *
 * OkHttp calls [Dns.lookup] exactly once per connection attempt and connects
 * directly to whichever address that call returns — there is no second,
 * independent resolution afterwards the way Ktor's CIO client (or any client
 * that validates via one lookup and then connects via another) would do.
 * That makes validating *inside* this Dns override equivalent to pinning: a
 * host whose record flips to a private/loopback answer after this check runs
 * can never be reached through it, because this is the only resolution that
 * ever happens for the real request. This closes the DNS-rebinding TOCTOU gap
 * that a separate check-then-connect (resolve once to validate, then let the
 * HTTP client resolve again to connect) can't.
 */
internal object SsrfSafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = InetAddress.getAllByName(hostname).toList()
        if (addresses.isEmpty() || !addresses.all(::isPublicAddress)) {
            throw UnknownHostException("$hostname does not resolve to a public address")
        }
        return addresses
    }
}

private val httpClient = OkHttpClient.Builder()
    .dns(SsrfSafeDns)
    // Redirects are followed manually below so every hop gets the same
    // isFetchableUrl pre-check (and, via SsrfSafeDns, the same pinned-
    // resolution enforcement) — automatic following would let a public URL
    // 302 the server into localhost or the cloud metadata endpoint.
    .followRedirects(false)
    .followSslRedirects(false)
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(8, TimeUnit.SECONDS)
    .callTimeout(8, TimeUnit.SECONDS)
    .build()

/**
 * Cheap pre-check before spending a network round trip: http(s) scheme only,
 * and the host must currently resolve to a public address. This is NOT what
 * stops DNS rebinding — [SsrfSafeDns] is — it only exists to fail fast with a
 * clear 400 response instead of letting a malformed or already-unsafe URL
 * fall through to an OkHttp [UnknownHostException].
 */
internal fun isFetchableUrl(url: String): Boolean {
    val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    if (parsed.scheme != "http" && parsed.scheme != "https") return false
    val host = parsed.host ?: return false
    val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return false
    return addresses.isNotEmpty() && addresses.all(::isPublicAddress)
}

private const val MAX_REDIRECTS = 3

// A malicious/misconfigured server can stream far more than a real recipe
// page ever needs within the 8s timeout window; reading the body unbounded
// would otherwise buffer all of it in memory from a single request.
private const val MAX_HTML_BYTES = 3L * 1024 * 1024

/** Thrown when a redirect Location points at a non-public address; caught in [fetchRecipeRoute] as a 400. */
private class UnsafeRedirectException : Exception("URL must be a public http(s) address")

private fun buildRecipeRequest(url: String): Request =
    Request.Builder().url(url)
        .header("User-Agent", "ScanEat/0.1 (+https://github.com/scanneat)")
        .header("Accept", "text/html,application/xhtml+xml")
        .build()

private sealed class FetchOutcome {
    data class Html(val text: String) : FetchOutcome()
    object TooLarge : FetchOutcome()
}

/** Blocking — run inside [Dispatchers.IO]. Follows redirects itself (see [httpClient]'s followRedirects = false). */
private fun fetchRecipeHtml(startUrl: String): FetchOutcome {
    var currentUrl = startUrl
    var response = httpClient.newCall(buildRecipeRequest(currentUrl)).execute()
    var hops = 0
    while (response.code in 301..308 && hops < MAX_REDIRECTS) {
        val location = response.header(HttpHeaders.Location) ?: break
        val next = java.net.URI(currentUrl).resolve(location).toString()
        response.close()
        if (!isFetchableUrl(next)) throw UnsafeRedirectException()
        currentUrl = next
        hops++
        response = httpClient.newCall(buildRecipeRequest(currentUrl)).execute()
    }
    return response.use { resp ->
        val source = resp.body?.source()
        if (source == null) {
            FetchOutcome.Html("")
        } else {
            source.request(MAX_HTML_BYTES + 1)
            if (source.buffer.size > MAX_HTML_BYTES) FetchOutcome.TooLarge
            else FetchOutcome.Html(source.buffer.readUtf8())
        }
    }
}

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
            val outcome = withContext(Dispatchers.IO) { fetchRecipeHtml(url) }
            val html = when (outcome) {
                is FetchOutcome.TooLarge -> {
                    call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Recipe page too large"))
                    return@get
                }
                is FetchOutcome.Html -> outcome.text
            }

            val recipe = parseSchemaOrgRecipe(html, url)
            if (recipe == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("No schema.org Recipe found on this page"))
                return@get
            }
            call.respond(recipe)
        } catch (e: UnsafeRedirectException) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("URL must be a public http(s) address"))
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

// kotlinx.serialization's JSON parser is recursive-descent with no built-in depth
// limit - MAX_HTML_BYTES bounds total page size (3 MB) but not structural nesting,
// and a few bytes per level ("[[[[...") comfortably fits millions of levels in that
// budget, enough to blow the parsing coroutine's stack (a StackOverflowError). This
// route needs no Groq key and is reachable by anyone who can get the server to
// fetch a URL they control, so it's a repeatable crash vector against request-
// handling threads, not a one-off. A cheap pre-scan rejects anything past a depth
// no real schema.org Recipe block would ever need (genuine ones nest ~3-5 levels).
private const val MAX_JSON_NESTING_DEPTH = 64

private fun exceedsJsonNestingDepth(raw: String, maxDepth: Int): Boolean {
    var depth = 0
    for (c in raw) {
        when (c) {
            '{', '[' -> { depth++; if (depth > maxDepth) return true }
            '}', ']' -> depth--
        }
    }
    return false
}

// internal (not private) so tests can exercise the real extraction logic
// instead of asserting on a fixture string the test itself wrote.
internal fun parseSchemaOrgRecipe(html: String, sourceUrl: String): FetchedRecipeResponse? {
    // Extract all <script type="application/ld+json"> blocks
    val ldJsonBlocks = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE
    ).findAll(html).mapNotNull { m ->
        val raw = m.groupValues[1].trim()
        if (raw.isEmpty() || exceedsJsonNestingDepth(raw, MAX_JSON_NESTING_DEPTH)) return@mapNotNull null
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

// This route proxies arbitrary attacker-hostable pages with no Groq key required -
// numFrom() parses whatever a page's JSON-LD declares, including a negative value
// (e.g. a bare "-99999" parses as a valid Double) or an absurdly large one, with no
// floor/ceiling before it flows into FetchedNutritionDto and the client's diary/
// scoring pipeline - a direct, unfiltered path to poisoning a user's nutrition data.
// Bounds are generous (a whole recipe/batch, not per-100g like LlmLabelParser's
// coerceNutrient) since a real multi-serving recipe can legitimately run into the
// thousands of kcal/grams.
private fun coerceRecipeNutrient(v: Double, max: Double): Double = v.coerceIn(0.0, max)

private fun extractNutrition(element: JsonElement?): FetchedNutritionDto? {
    val obj = element as? JsonObject ?: return null
    val kcal = coerceRecipeNutrient(numFrom(obj["calories"]), 10_000.0)
    if (kcal == 0.0) return null
    return FetchedNutritionDto(
        kcal      = kcal,
        proteinG  = coerceRecipeNutrient(numFrom(obj["proteinContent"]), 1_000.0),
        fatG      = coerceRecipeNutrient(numFrom(obj["fatContent"]), 1_000.0),
        carbsG    = coerceRecipeNutrient(numFrom(obj["carbohydrateContent"]), 1_000.0),
    )
}
