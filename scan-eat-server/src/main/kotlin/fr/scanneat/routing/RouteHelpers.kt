package fr.scanneat.routing

import fr.scanneat.model.ErrorResponse
import fr.scanneat.model.ImageDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

// ============================================================================
// ROUTING HELPERS — shared across all routes
// Mirrors api/_lib.ts: requirePost, normalizeImages, mapErrorToPublicMessage
// ============================================================================

const val MAX_BODY_BYTES = 12 * 1024 * 1024   // 12 MB

/** Resolve the Groq API key: X-Groq-Key header first, then GROQ_API_KEY env. */
fun ApplicationCall.resolveGroqKey(): String? {
    val header = request.header("X-Groq-Key")?.takeIf { it.isNotBlank() }
    if (header != null) return header
    return System.getenv("GROQ_API_KEY")?.takeIf { it.isNotBlank() }
}

/** Respond 503 if no Groq key is available. Returns false if the caller should abort. */
suspend fun ApplicationCall.requireGroqKey(): String? {
    val key = resolveGroqKey()
    if (key == null) {
        respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("service_unavailable", "No Groq API key — set GROQ_API_KEY env var or pass X-Groq-Key header"))
    }
    return key
}

/** Normalize a legacy `imageBase64` field alongside the `images` array. */
fun normalizeImages(images: List<ImageDto>?, imageBase64: String?, mime: String?): List<ImageDto> {
    if (!images.isNullOrEmpty()) {
        return images.filter { it.base64.isNotBlank() }
    }
    if (!imageBase64.isNullOrBlank()) {
        return listOf(ImageDto(imageBase64, mime ?: "image/jpeg"))
    }
    return emptyList()
}

/** Map internal errors to non-revealing public messages. */
fun mapError(err: Throwable): Pair<HttpStatusCode, ErrorResponse> {
    val msg = err.message ?: ""
    return when {
        "413" in msg || "too large" in msg.lowercase() -> Pair(
            HttpStatusCode.PayloadTooLarge, ErrorResponse("Request body too large"))
        "rate_limit" in msg.lowercase() || "429" in msg -> Pair(
            HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit"))
        "service_unavailable" in msg.lowercase() -> Pair(
            HttpStatusCode.ServiceUnavailable, ErrorResponse("service_unavailable"))
        else -> Pair(HttpStatusCode.InternalServerError, ErrorResponse("Internal error"))
    }
}
