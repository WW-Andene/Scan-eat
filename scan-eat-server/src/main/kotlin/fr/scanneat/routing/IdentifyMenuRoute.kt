package fr.scanneat.routing

import fr.scanneat.model.*
import fr.scanneat.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("IdentifyMenuRoute")

// ============================================================================
// POST /api/identify-menu
// Restaurant menu photo → list of dishes with estimated macros
// Mirrors api/identify-menu.ts
// ============================================================================

fun Route.identifyMenuRoute(groqService: GroqService) {
    post("/identify-menu") {
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
            val result = groqService.identifyMenu(images, key)
            call.respond(IdentifiedMenuResponse(
                dishes   = result.dishes.map { d ->
                    MenuDishDto(
                        name          = d.name,
                        description   = d.description,
                        estimatedKcal = d.estimated_kcal,
                        proteinG      = d.protein_g,
                    )
                },
                warnings = result.warnings,
            ))
        } catch (e: Exception) {
            log.error("[/api/identify-menu]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}

// ============================================================================
// POST /api/identify-recipe
// Recipe card / cookbook page → structured recipe
// Mirrors api/identify-recipe.ts
// ============================================================================

fun Route.identifyRecipeRoute(groqService: GroqService) {
    post("/identify-recipe") {
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
            val result = groqService.identifyRecipe(images, key)
            call.respond(IdentifiedRecipeResponse(
                name        = result.name,
                servings    = result.servings,
                ingredients = result.ingredients.map { i -> RecipeIngredientDto(i.name, i.quantity, i.unit) },
                steps       = result.steps,
                cookTimeMin = result.cook_time_min,
            ))
        } catch (e: Exception) {
            log.error("[/api/identify-recipe]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}
