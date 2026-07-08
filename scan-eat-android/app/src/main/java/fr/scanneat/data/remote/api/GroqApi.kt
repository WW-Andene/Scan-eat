package fr.scanneat.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.*

// ============================================================================
// GROQ API — OpenAI-compatible chat completions endpoint
// Used by OcrParser (vision) and text-only recipe/suggestion calls
// ============================================================================

interface GroqApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest,
    ): ChatResponse
}

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @Json(name = "max_tokens") val maxTokens: Int = 2000,
    val temperature: Double = 0.1,
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,                      // "user" | "assistant" | "system"
    val content: List<ContentPart>,
)

/** A content part — either plain text or an image_url block. */
@JsonClass(generateAdapter = true)
data class ContentPart(
    val type: String,                      // "text" | "image_url"
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: ImageUrl? = null,
)

@JsonClass(generateAdapter = true)
data class ImageUrl(
    val url: String,                       // "data:image/jpeg;base64,..."
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val choices: List<Choice>,
    val model: String?,
    val usage: Usage?,
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: AssistantMessage,
    @Json(name = "finish_reason") val finishReason: String?,
)

@JsonClass(generateAdapter = true)
data class AssistantMessage(
    val role: String,
    val content: String?,
)

@JsonClass(generateAdapter = true)
data class Usage(
    @Json(name = "prompt_tokens") val promptTokens: Int,
    @Json(name = "completion_tokens") val completionTokens: Int,
    @Json(name = "total_tokens") val totalTokens: Int,
)
