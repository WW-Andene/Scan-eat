package fr.scanneat.routing

import fr.scanneat.model.*
import fr.scanneat.service.*
import fr.scanneat.shared.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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
    post("/score") {
        val req = runCatching { call.receive<ScoreRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }

        val images = normalizeImages(req.images, req.imageBase64, req.mime)

        try {
            // ---- Barcode path ----
            if (!req.barcode.isNullOrBlank()) {
                val offRaw = offService.fetchProduct(req.barcode)
                val offProduct = offRaw?.let { mapOffProduct(it) }

                if (offProduct != null) {
                    // Sparse + have images → augment with LLM
                    if (isOffSparse(offProduct) && images.isNotEmpty()) {
                        val key = call.resolveGroqKey()
                        if (key != null) {
                            runCatching {
                                val parsed   = groqService.parseLabel(images, key)
                                val merged   = mergeOffWithLlm(offProduct, parsed.product)
                                val conflicts = detectSourceConflicts(offProduct, parsed.product)
                                val audit    = scoreProduct(merged)
                                call.respond(ScoreResponse(
                                    product  = merged.toDto(),
                                    audit    = audit.toDto(),
                                    warnings = parsed.warnings + conflicts.map { "Conflict: ${it.field} OFF=${it.offValue} LLM=${it.llmValue}" },
                                    source   = "merged",
                                    barcode  = req.barcode,
                                ))
                                return@post
                            }.onFailure { e ->
                                log.warn("LLM augmentation failed, falling back to OFF-only: ${e.message}")
                            }
                        }
                    }
                    // OFF-only
                    val audit = scoreProduct(offProduct)
                    call.respond(ScoreResponse(
                        product  = offProduct.toDto(),
                        audit    = audit.toDto(),
                        warnings = emptyList(),
                        source   = "openfoodfacts",
                        barcode  = req.barcode,
                    ))
                    return@post
                }
                // OFF miss — fall through to image path below
            }

            // ---- Image-only path ----
            if (images.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing images"))
                return@post
            }
            val key = call.requireGroqKey() ?: return@post

            val parsed = groqService.parseLabel(images, key)
            val audit  = scoreProduct(parsed.product)
            call.respond(ScoreResponse(
                product  = parsed.product.toDto(),
                audit    = audit.toDto(),
                warnings = parsed.warnings,
                source   = "llm",
                barcode  = parsed.barcode,
            ))

        } catch (e: Exception) {
            log.error("[/api/score]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}
