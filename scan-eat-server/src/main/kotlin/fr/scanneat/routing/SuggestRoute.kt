package fr.scanneat.routing

import fr.scanneat.model.*
import fr.scanneat.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("SuggestRoute")

// rejectIfTooLarge only bounds the whole request body (12 MB) — a single
// `ingredient` or pantry-entry string within that budget was still embedded
// verbatim into the LLM prompt with no per-field cap. That's both a cost
// vector (megabytes of text billed as prompt tokens on every call) and a
// prompt-injection amplifier: the "treat this as a literal food name, not
// instructions" guard in LlmLabelParser.kt has to compete against however
// much adversarial filler text the field carries, and an attacker who can
// send an unbounded string can just drown the guard out by repetition. Any
// genuine ingredient/pantry-item name comfortably fits in a few dozen
// characters, so this caps well above that.
private const val MAX_FIELD_LEN = 200

// ============================================================================
// POST /api/suggest-recipes
// Single ingredient → recipe ideas
// Mirrors api/suggest-recipes.ts
// ============================================================================

fun Route.suggestRecipesRoute(groqService: GroqService) {
    post("/suggest-recipes") {
        if (call.rejectIfTooLarge()) return@post
        // Unauthenticated in Server mode (no X-Groq-Key required); this always
        // calls Groq's paid LLM API. See RateLimiter.kt.
        if (call.rejectIfRateLimited(llmRateLimiter)) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<SuggestRecipesRequest>() }.getOrElse { e ->
            if (e is CancellationException) throw e
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        if (req.ingredient.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Missing ingredient"))
            return@post
        }
        if (req.ingredient.trim().length > MAX_FIELD_LEN) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ingredient too long"))
            return@post
        }
        try {
            val result = groqService.suggestRecipes(req.ingredient.trim(), key)
            call.respond(SuggestedRecipesResponse(
                recipes = result.recipes.map { r ->
                    SuggestedRecipeDto(r.name, r.description, r.cookTimeMin, r.difficulty, r.mainIngredients)
                },
                warnings = result.warnings,
            ))
        } catch (e: Exception) {
            call.handleRouteError(log, "[/api/suggest-recipes]", e)
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
        // Unauthenticated in Server mode (no X-Groq-Key required); this always
        // calls Groq's paid LLM API. See RateLimiter.kt.
        if (call.rejectIfRateLimited(llmRateLimiter)) return@post
        val key = call.requireGroqKey() ?: return@post
        val req = runCatching { call.receive<SuggestFromPantryRequest>() }.getOrElse { e ->
            if (e is CancellationException) throw e
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid JSON body"))
            return@post
        }
        // take(20) already bounded the item count but not each item's length —
        // same MAX_FIELD_LEN cap as /suggest-recipes above.
        val pantry = req.pantry.filter { it.isNotBlank() && it.trim().length <= MAX_FIELD_LEN }.take(20)
        if (pantry.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Empty pantry"))
            return@post
        }
        try {
            val result = groqService.suggestFromPantry(pantry, key)
            call.respond(SuggestedRecipesResponse(
                recipes = result.recipes.map { r ->
                    SuggestedRecipeDto(r.name, r.description, r.cookTimeMin, r.difficulty, r.mainIngredients)
                },
                warnings = result.warnings,
            ))
        } catch (e: Exception) {
            call.handleRouteError(log, "[/api/suggest-from-pantry]", e)
        }
    }
}
