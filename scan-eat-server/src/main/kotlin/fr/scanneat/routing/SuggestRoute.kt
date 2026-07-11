package fr.scanneat.routing

import fr.scanneat.model.*
import fr.scanneat.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SuggestRoute")

// ============================================================================
// POST /api/suggest-recipes
// Single ingredient → recipe ideas
// Mirrors api/suggest-recipes.ts
// ============================================================================

fun Route.suggestRecipesRoute(groqService: GroqService) {
    post("/suggest-recipes") {
        if (call.rejectIfTooLarge()) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<SuggestRecipesRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        if (req.ingredient.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing ingredient"))
            return@post
        }
        try {
            val result = groqService.suggestRecipes(req.ingredient.trim(), key)
            call.respond(SuggestedRecipesResponse(
                recipes = result.recipes.map { r ->
                    SuggestedRecipeDto(r.name, r.description, r.cook_time_min, r.difficulty, r.main_ingredients)
                }
            ))
        } catch (e: Exception) {
            log.error("[/api/suggest-recipes]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}

// ============================================================================
// POST /api/suggest-from-pantry
// List of pantry items → recipe ideas using what you have
// Mirrors api/suggest-from-pantry.ts
// ============================================================================

fun Route.suggestFromPantryRoute(groqService: GroqService) {
    post("/suggest-from-pantry") {
        if (call.rejectIfTooLarge()) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<SuggestFromPantryRequest>() }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        val pantry = req.pantry.filter { it.isNotBlank() }.take(20)
        if (pantry.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Empty pantry"))
            return@post
        }
        try {
            val result = groqService.suggestFromPantry(pantry, key)
            call.respond(SuggestedRecipesResponse(
                recipes = result.recipes.map { r ->
                    SuggestedRecipeDto(r.name, r.description, r.cook_time_min, r.difficulty, r.main_ingredients)
                }
            ))
        } catch (e: Exception) {
            log.error("[/api/suggest-from-pantry]", e)
            val (status, body) = mapError(e)
            call.respond(status, body)
        }
    }
}
