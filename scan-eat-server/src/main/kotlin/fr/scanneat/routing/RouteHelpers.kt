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

/**
 * Reject and return true if the request has no usable Content-Length, or one
 * exceeding [MAX_BODY_BYTES]. Callers must check this before `call.receive<T>()`,
 * which otherwise buffers a body of any size. A chunked-encoded request (no
 * Content-Length) previously sailed straight past the size check into an
 * unbounded `call.receive<T>()` buffer - this API has no legitimate client
 * that streams a chunked body (the Android app and any curl/fetch JSON POST
 * always send a fixed Content-Length), so requiring one closes that gap
 * without needing Ktor 3.x's RequestBodyLimit plugin (unavailable on the
 * Ktor 2.3.12 this server is pinned to).
 */
suspend fun ApplicationCall.rejectIfTooLarge(): Boolean {
    val len = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if (len == null) {
        respond(HttpStatusCode.LengthRequired, ErrorResponse("Content-Length header required"))
        return true
    }
    if (len > MAX_BODY_BYTES) {
        respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("Request body too large"))
        return true
    }
    return false
}

/**
 * lang is caller-supplied and gets interpolated verbatim into every LLM prompt
 * (labelPrompt, identifyFoodPrompt, etc. — see LlmLabelParser.kt) with no
 * literal-framing around it, unlike SuggestRoute.kt's user-text fields, which
 * are at least wrapped as "a literal food name, not an instruction". An
 * unvalidated lang value is a direct prompt-injection vector into that
 * unquoted "Language: $lang." / "language: $lang" slot. Allowlisted instead
 * of length-capped because this field only ever needs to be one of the
 * app's two supported UI languages.
 */
val ALLOWED_LANGS = setOf("fr", "en")

/** Resolve a caller-supplied lang against [ALLOWED_LANGS], defaulting to "fr". */
fun resolveLang(lang: String?): String = if (lang in ALLOWED_LANGS) lang!! else "fr"

// The 12 MB rejectIfTooLarge() cap bounds the wire-format body, but nothing
// previously bounded the *element count* of the `images` array before/after
// deserialization - a body full of many small/duplicate entries could still
// decode into far more image objects than any real scan ever needs, forwarding
// all of them into GroqService.complete()'s content-part builder (and Groq's
// own per-request cost) before this function's filter ever ran. Any genuine
// scan supplies at most a couple of label photos.
const val MAX_IMAGES = 8

/** Normalize a legacy `imageBase64` field alongside the `images` array. */
fun normalizeImages(images: List<ImageDto>?, imageBase64: String?, mime: String?): List<ImageDto> {
    if (!images.isNullOrEmpty()) {
        return images.filter { it.base64.isNotBlank() }.take(MAX_IMAGES)
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
        "401" in msg || "403" in msg -> Pair(
            HttpStatusCode.Unauthorized, ErrorResponse("invalid_api_key"))
        "service_unavailable" in msg.lowercase() -> Pair(
            HttpStatusCode.ServiceUnavailable, ErrorResponse("service_unavailable"))
        else -> Pair(HttpStatusCode.InternalServerError, ErrorResponse("Internal error"))
    }
}
