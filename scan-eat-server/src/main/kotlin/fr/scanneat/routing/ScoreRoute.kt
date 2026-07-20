package fr.scanneat.routing

import fr.scanneat.model.*
import fr.scanneat.service.*
import fr.scanneat.shared.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ScoreRoute")

// ============================================================================
// POST /api/score
//
// Hybrid scoring: barcode → OFF → optional LLM augment (if sparse) → score.
// Mirrors api/score.ts logic exactly.
//
// Auth: X-Groq-Key header (user's key) or GROQ_API_KEY env var.
// Body: ScoreRequest JSON (max 12 MB)
// Response: ScoreResponse JSON
// ============================================================================

fun Route.scoreRoute(groqService: GroqService, offService: OffService) {
    val scoreService = ScoreService(offService, groqService)

    post("/score") {
        if (call.rejectIfTooLarge()) return@post
        // Unauthenticated in Server mode (no X-Groq-Key required — the
        // operator's GROQ_API_KEY covers it), and this can call out to Groq's
        // paid vision API. Without a throttle, anyone who finds the server URL
        // could run up the operator's Groq bill for free. See RateLimiter.kt.
        if (call.rejectIfRateLimited(llmRateLimiter)) return@post
        val req = runCatching { call.receive<ScoreRequest>() }.getOrElse { e ->
            if (e is CancellationException) throw e
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }

        val images = normalizeImages(req.images, req.imageBase64, req.mime)
        val lang = resolveLang(req.lang)
        // req.model is caller-supplied and, in Server mode (no X-Groq-Key required),
        // reachable anonymously — without an allowlist any caller could force the
        // operator's own GROQ_API_KEY to invoke an arbitrary, possibly costlier Groq
        // model instead of the intended default.
        val requestedModel = req.model
        if (requestedModel != null && requestedModel !in ALLOWED_GROQ_MODELS) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Unsupported model"))
            return@post
        }
        val model = requestedModel ?: DEFAULT_GROQ_MODEL

        try {
            // ---- Barcode path ----
            if (!req.barcode.isNullOrBlank()) {
                val outcome = scoreService.scoreBarcode(req.barcode, images, { call.resolveGroqKey() }, lang, model)
                if (outcome is BarcodeScoreOutcome.Success) {
                    call.respond(outcome.response)
                    return@post
                }
                // OffMiss — fall through to image path below
            }

            // ---- Image-only path ----
            if (images.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing images"))
                return@post
            }
            val key = call.requireGroqKey() ?: return@post
            call.respond(scoreService.scoreFromImages(images, key, lang, model))

        } catch (e: Exception) {
            call.handleRouteError(log, "[/api/score]", e)
        }
    }
}
