package app.scaneat

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 5173, host = "0.0.0.0") { scanEatModule() }.start(wait = true)
}

fun Application.scanEatModule(clients: ExternalClients = ExternalClients()) {
    val appLog = environment.log
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; prettyPrint = false }) }
    install(CORS) { anyHost(); allowHeader(HttpHeaders.ContentType); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Get) }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            appLog.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Scoring failed"))
        }
    }

    routing {
        post("/api/score") {
            val body = call.receive<ScoreRequest>()
            val product = body.product
                ?: body.barcode?.let { clients.productFromBarcode(it) }
                ?: fallbackProduct(normalizeImages(body), body.barcode)
            call.respond(ScoreResponse(product, ScoringEngine.score(product), source = if (body.barcode != null) "openfoodfacts_or_fallback" else "kotlin", barcode = body.barcode ?: product.barcode))
        }
        post("/api/identify") { call.respond(identifyFallback(call.receive<ScoreRequest>())) }
        post("/api/identify-multi") { call.respond(mapOf("items" to listOf(identifyFallback(call.receive<ScoreRequest>())))) }
        post("/api/identify-menu") { call.respond(mapOf("items" to emptyList<String>(), "warnings" to listOf("Menu OCR requires a configured LLM adapter"))) }
        post("/api/identify-recipe") { call.respond(mapOf("recipe" to null, "warnings" to listOf("Recipe OCR requires a configured LLM adapter"))) }
        post("/api/suggest-recipes") { call.respond(mapOf("recipes" to emptyList<String>())) }
        post("/api/suggest-from-pantry") { call.respond(mapOf("recipes" to emptyList<String>())) }
        post("/api/fetch-recipe") { call.respond(mapOf("error" to "Recipe fetching is not enabled in the Kotlin runtime")) }

        staticFiles("/", File("public")) { default("index.html") }
    }
}

private fun identifyFallback(req: ScoreRequest): Map<String, Any?> {
    val product = req.product ?: fallbackProduct(normalizeImages(req), req.barcode)
    return mapOf("product" to product, "warnings" to listOf("Kotlin fallback identification used; pass a product payload or barcode for best results"))
}

private fun fallbackProduct(images: List<ImagePayload>, barcode: String?): ProductInput = ProductInput(
    name = if (images.isEmpty()) "Produit inconnu" else "Produit scanné",
    category = "other",
    nova_class = 4,
    barcode = barcode,
)
