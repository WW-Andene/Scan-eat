package fr.scanneat.service

import fr.scanneat.model.ImageDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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

class GroqService {

    private val client = HttpClient(CIO) {
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
     * @param maxRetries Number of attempts before throwing.
     */
    suspend fun complete(
        prompt: String,
        images: List<ImageDto> = emptyList(),
        apiKey: String? = null,
        model: String = DEFAULT_GROQ_MODEL,
        maxRetries: Int = 3,
    ): String {
        val key = resolveKey(apiKey)

        val content: List<ContentPart> = images.map { img ->
            ContentPart(type = "image_url", imageUrl = ImageUrlPart("data:${img.mime};base64,${img.base64}"))
        } + ContentPart(type = "text", text = prompt)

        var lastErr: Throwable? = null
        repeat(maxRetries) { attempt ->
            val useModel = if (attempt == maxRetries - 1) FALLBACK_GROQ_MODEL else model
            runCatching {
                val resp: ChatResponse = client.post(GROQ_ENDPOINT) {
                    header("Authorization", "Bearer $key")
                    contentType(ContentType.Application.Json)
                    setBody(ChatRequest(model = useModel, messages = listOf(Message("user", content))))
                }.body()
                return resp.choices.firstOrNull()?.message?.content
                    ?: throw RuntimeException("Empty response from Groq")
            }.onFailure { e ->
                // A 4xx other than 429 (bad key, malformed request) will fail
                // identically on every retry and on the fallback model — fail
                // fast instead of burning attempts against it.
                if (e is ClientRequestException && e.response.status != HttpStatusCode.TooManyRequests) throw e
                lastErr = e
                log.warn("Groq attempt $attempt failed (model=$useModel): ${e.message}")
            }
        }
        throw lastErr ?: RuntimeException("All Groq retries exhausted")
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
