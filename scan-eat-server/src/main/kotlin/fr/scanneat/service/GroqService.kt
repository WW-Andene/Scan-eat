package fr.scanneat.service

import fr.scanneat.model.ImageDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

// ============================================================================
// GROQ SERVICE
//
// Central HTTP client for all Groq chat completion calls.
// Handles retries, model fallback, and key routing.
//
// API key resolution order:
//   1. Provided per-call (user's own key — Direct mode)
//   2. GROQ_API_KEY env var (server's key — Server mode)
// ============================================================================

private val log = LoggerFactory.getLogger("GroqService")

const val DEFAULT_GROQ_MODEL    = "meta-llama/llama-4-scout-17b-16e-instruct"
const val FALLBACK_GROQ_MODEL   = "llama-3.3-70b-versatile"
const val GROQ_ENDPOINT         = "https://api.groq.com/openai/v1/chat/completions"

/** Models /api/score's caller-supplied `model` field is allowed to select — see ScoreRoute.kt. */
val ALLOWED_GROQ_MODELS = setOf(DEFAULT_GROQ_MODEL, FALLBACK_GROQ_MODEL)

// ---- Wire types ----

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int = 2000,
    val temperature: Double = 0.1,
)

@Serializable
private data class Message(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
private data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrlPart? = null,
)

@Serializable
private data class ImageUrlPart(val url: String)

@Serializable
private data class ChatResponse(
    val choices: List<Choice>,
)

@Serializable
private data class Choice(
    val message: AssistantMsg,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class AssistantMsg(
    val role: String,
    val content: String? = null,
)

// ---- Service ----

// engine defaults to the real CIO engine for every production call site
// (Application.kt's `GroqService()`) - the parameter exists purely so tests can
// substitute a MockEngine instead of hitting the real, paid Groq API over the
// network. See GroqServiceTest.kt.
class GroqService(engine: HttpClientEngine = CIO.create()) {

    // RateLimiter.kt bounds request *rate* per client IP, but not server-wide
    // *concurrency* - a Groq slowdown (degraded-but-not-fully-down, distinct from
    // the already-handled full-outage/429 cases) lets each in-flight call hold its
    // coroutine/CIO connection for up to ~91.5s (30s timeout * 3 retries + backoff).
    // Many distinct source IPs hitting /api/score, /api/identify*, or /api/suggest*
    // during such a slowdown could each pin one of those, compounding the very
    // degradation the per-IP limiter was meant to contain. tryAcquire fails fast
    // once the ceiling is hit instead of queuing an unbounded wait behind it.
    private val concurrencyLimiter = Semaphore(permits = 20)

    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = false })
        }
        install(Logging) { level = LogLevel.INFO }
        install(HttpTimeout) {
            requestTimeoutMillis  = 30_000
            connectTimeoutMillis  = 10_000
        }
        // Without this, a 429/500 from Groq is fed to the ChatResponse
        // deserializer and surfaces as a serialization error whose message
        // never contains the status code — so mapError() could never map
        // rate limits to 429 and every Groq error reached clients as a
        // generic 500.
        expectSuccess = true
    }

    /**
     * Call the Groq chat completions endpoint with retry + model fallback.
     *
     * @param apiKey  Caller-supplied key (Direct mode). Falls back to GROQ_API_KEY env var.
     * @param images  Optional vision images.
     * @param prompt  Text prompt appended after any images.
     * @param model   Primary model; final retry switches to FALLBACK_GROQ_MODEL.
     * @param maxTokens Response token budget — raise for prompts whose JSON output
     *   (multiple items/recipes) routinely runs long; the Groq default of 2000 was
     *   silently truncating those mid-JSON with no signal beyond a downstream parse
     *   failure that looked identical to a genuinely malformed LLM response.
     * @param maxRetries Number of attempts before throwing.
     */
    suspend fun complete(
        prompt: String,
        images: List<ImageDto> = emptyList(),
        apiKey: String? = null,
        model: String = DEFAULT_GROQ_MODEL,
        maxTokens: Int = 2000,
        maxRetries: Int = 3,
    ): String {
        // mapError() (RouteHelpers.kt) already maps a "service_unavailable" message
        // to HTTP 503 - reusing that string here fails fast past the concurrency
        // ceiling instead of queuing an unbounded wait behind it.
        if (!concurrencyLimiter.tryAcquire()) {
            throw RuntimeException("service_unavailable: too many concurrent Groq requests")
        }
        try {
            val key = resolveKey(apiKey)

            val content: List<ContentPart> = images.map { img ->
                ContentPart(type = "image_url", imageUrl = ImageUrlPart("data:${img.mime};base64,${img.base64}"))
            } + ContentPart(type = "text", text = prompt)

            // FALLBACK_GROQ_MODEL is text-only — switching to it on the last
            // attempt is only a genuine extra chance for a text-only request.
            // Every caller here always includes image content when images is
            // non-empty, so falling back would guarantee-fail the vision
            // request's last retry instead of giving it a real one.
            val hasImages = images.isNotEmpty()
            var lastErr: Throwable? = null
            repeat(maxRetries) { attempt ->
                val useModel = if (attempt == maxRetries - 1 && !hasImages) FALLBACK_GROQ_MODEL else model
                runCatching {
                    val resp: ChatResponse = client.post(GROQ_ENDPOINT) {
                        header("Authorization", "Bearer $key")
                        contentType(ContentType.Application.Json)
                        setBody(ChatRequest(model = useModel, messages = listOf(Message("user", content)), maxTokens = maxTokens))
                    }.body()
                    val choice = resp.choices.firstOrNull()
                        ?: throw RuntimeException("Empty response from Groq")
                    // finish_reason was parsed but never read - a response cut off mid-JSON
                    // by hitting maxTokens surfaced downstream only as an opaque JSON-parse
                    // failure, indistinguishable from the model genuinely returning garbage.
                    if (choice.finishReason == "length")
                        log.warn("Groq response truncated at maxTokens=$maxTokens (model=$useModel)")
                    return choice.message.content
                        ?: throw RuntimeException("Empty response from Groq")
                }.onFailure { e ->
                    // CancellationException (client disconnect, request scope closing) is a
                    // subtype of Exception, so runCatching/onFailure catches it just like any
                    // real failure - without rethrowing it first, a cancelled request fell
                    // through to the retry logic below and got retried against the paid Groq
                    // API, then logged as an ordinary failed attempt.
                    if (e is CancellationException) throw e
                    // A 4xx other than 429 (bad key, malformed request) will fail
                    // identically on every retry and on the fallback model — fail
                    // fast instead of burning attempts against it.
                    if (e is ClientRequestException && e.response.status != HttpStatusCode.TooManyRequests) throw e
                    lastErr = e
                    log.warn("Groq attempt $attempt failed (model=$useModel): ${e.message}")
                }
                if (attempt < maxRetries - 1) delay(500L * (attempt + 1))
            }
            throw lastErr ?: RuntimeException("All Groq retries exhausted")
        } finally {
            concurrencyLimiter.release()
        }
    }

    private fun resolveKey(callerKey: String?): String {
        if (!callerKey.isNullOrBlank()) return callerKey
        val envKey = System.getenv("GROQ_API_KEY")
        if (!envKey.isNullOrBlank()) return envKey
        error("No Groq API key — pass X-Groq-Key header or set GROQ_API_KEY env var")
    }

    fun close() = client.close()
}

// ---- JSON extraction helper (strips markdown fences) ----

fun extractJson(raw: String): String {
    val stripped = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = stripped.indexOf('{')
    val end   = stripped.lastIndexOf('}')
    return if (start >= 0 && end > start) stripped.substring(start, end + 1) else stripped
}

fun extractJsonArray(raw: String): String {
    val stripped = raw.trim()
        .removePrefix("```json").removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = stripped.indexOf('[')
    val end   = stripped.lastIndexOf(']')
    return if (start >= 0 && end > start) stripped.substring(start, end + 1) else stripped
}
