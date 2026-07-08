package fr.scanneat

import fr.scanneat.routing.*
import fr.scanneat.service.GroqService
import fr.scanneat.service.OffService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

// ============================================================================
// SCAN'EAT KTOR SERVER
//
// Routes mirror api/*.ts Vercel functions exactly:
//   POST /api/score              — barcode + optional images → ScoreAudit
//   POST /api/identify           — images → identified food (fresh/unpackaged)
//   POST /api/identify-multi     — images → multiple foods on a plate
//   POST /api/identify-menu      — images → restaurant menu dishes
//   POST /api/identify-recipe    — images → structured recipe
//   POST /api/suggest-recipes    — ingredient → recipe ideas
//   POST /api/suggest-from-pantry — pantry list → recipes
//   GET  /api/fetch-recipe?url=  — scrape schema.org recipe from URL
//   GET  /health                 — liveness probe
// ============================================================================

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 8080, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // ---- Services (singleton pattern — no DI framework for simplicity) ----
    val groqService = GroqService()
    val offService  = OffService()

    // ---- Plugins ----
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults    = false
            prettyPrint       = false
        })
    }
    install(CallLogging) { level = Level.INFO }
    install(DefaultHeaders)
    install(Compression) { gzip { priority = 1.0 } }
    install(CORS) {
        anyHost()                        // tighten in production
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal server error"))
        }
    }

    // ---- Routing ----
    routing {
        get("/health") { call.respond(mapOf("status" to "ok", "version" to "0.1.0")) }

        route("/api") {
            scoreRoute(groqService, offService)
            identifyRoute(groqService)
            identifyMultiRoute(groqService)
            identifyMenuRoute(groqService)
            identifyRecipeRoute(groqService)
            suggestRecipesRoute(groqService)
            suggestFromPantryRoute(groqService)
            fetchRecipeRoute()
        }
    }
}
