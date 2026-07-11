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

private val log = LoggerFactory.getLogger("IdentifyRoute")

// ============================================================================
// POST /api/identify
// Fresh food / plate → single IdentifiedFoodResponse
// Mirrors api/identify.ts
// ============================================================================

fun Route.identifyRoute(groqService: GroqService) {
    post("/identify") {
        if (call.rejectIfTooLarge()) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<ImagesRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        val images = normalizeImages(req.images, null, null)
        if (images.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing images"))
            return@post
        }
        try {
            val result = groqService.identifyFood(images, key)
            call.respond(IdentifiedFoodResponse(
                name        = result.product.name,
                category    = result.product.category.key,
                novaClass   = result.product.novaClass.value,
                ingredients = result.product.ingredients.map { it.toDto() },
                nutrition   = result.product.nutrition.toDto(),
                warnings    = result.warnings,
            ))
        } catch (e: Exception) {
            log.error("[/api/identify]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}

// ============================================================================
// POST /api/identify-multi
// Plate with multiple distinct foods → IdentifiedMultiFoodResponse
// Mirrors api/identify-multi.ts
// ============================================================================

fun Route.identifyMultiRoute(groqService: GroqService) {
    post("/identify-multi") {
        if (call.rejectIfTooLarge()) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<ImagesRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        val images = normalizeImages(req.images, null, null)
        if (images.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing images"))
            return@post
        }
        try {
            val result = groqService.identifyMultiFood(images, key)
            call.respond(IdentifiedMultiFoodResponse(
                items    = result.items.map { p ->
                    IdentifiedFoodResponse(
                        name        = p.name,
                        category    = p.category.key,
                        novaClass   = p.novaClass.value,
                        ingredients = p.ingredients.map { it.toDto() },
                        nutrition   = p.nutrition.toDto(),
                    )
                },
                warnings = result.warnings,
            ))
        } catch (e: Exception) {
            log.error("[/api/identify-multi]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}
