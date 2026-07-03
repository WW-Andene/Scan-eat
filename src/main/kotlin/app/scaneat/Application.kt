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
import kotlin.math.roundToInt
import java.io.File

fun main() {
    embeddedServer(Netty, port = System.getenv("PORT")?.toIntOrNull() ?: 5173, host = "0.0.0.0") { scanEatModule() }.start(wait = true)
}

fun Application.scanEatModule(clients: ExternalClients = ExternalClients()) {
    val appLog = environment.log
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; prettyPrint = false }) }
    install(CORS) { anyHost(); allowHeader(HttpHeaders.ContentType); allowMethod(HttpMethod.Post); allowMethod(HttpMethod.Get) }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (cause.message ?: "Invalid request")))
        }
        exception<Throwable> { call, cause ->
            appLog.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Scoring failed"))
        }
    }

    routing {
        get("/") { call.respond(mapOf("ok" to true, "engineVersion" to ScoringEngine.ENGINE_VERSION)) }
        get("/api/health") { call.respond(mapOf("ok" to true, "engineVersion" to ScoringEngine.ENGINE_VERSION)) }
        post("/api/score") {
            val body = call.receive<ScoreRequest>().validated()
            val product = body.product
                ?: body.barcode?.let { clients.productFromBarcode(it) }
                ?: fallbackProduct(normalizeImages(body), body.barcode)
            val source = when {
                body.product != null -> "manual_or_client_product"
                body.barcode != null && product.barcode == body.barcode -> "openfoodfacts_or_fallback"
                else -> "kotlin_fallback"
            }
            call.respond(ScoreResponse(product, ScoringEngine.score(product, body.preferences), source = source, barcode = body.barcode ?: product.barcode))
        }
        post("/api/identify") { call.respond(identifyFallback(call.receive<ScoreRequest>().validated(requireSignal = true))) }
        post("/api/identify-multi") { call.respond(mapOf("items" to listOf(identifyFallback(call.receive<ScoreRequest>().validated(requireSignal = true))))) }
        post("/api/identify-menu") { call.respond(clients.proxyOrUnavailable("identify-menu", call.receiveText())) }
        post("/api/identify-recipe") { call.respond(clients.proxyOrUnavailable("identify-recipe", call.receiveText())) }
        post("/api/suggest-recipes") { call.respond(clients.proxyOrUnavailable("suggest-recipes", call.receiveText())) }
        post("/api/suggest-from-pantry") { call.respond(clients.proxyOrUnavailable("suggest-from-pantry", call.receiveText())) }
        post("/api/fetch-recipe") { call.respond(clients.proxyOrUnavailable("fetch-recipe", call.receiveText())) }

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

private fun ScoreRequest.validated(requireSignal: Boolean = false): ScoreRequest {
    barcode?.let { require(Regex("^(?:\\d{8}|\\d{12,14})$").matches(it.trim())) { "Invalid barcode" } }
    val imgs = normalizeImages(this)
    require(imgs.size <= 4) { "Too many images (max 4)" }
    imgs.forEachIndexed { index, image ->
        require(image.mime in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "Unsupported image type at index $index" }
        require(image.base64.length <= 3_500_000) { "Image ${index} is too large (${(image.base64.length / 1024.0 / 1024.0 * 10).roundToInt() / 10.0} MB base64)" }
        require(Regex("^[A-Za-z0-9+/]+={0,2}$").matches(image.base64) && image.base64.length % 4 == 0) { "Invalid base64 image at index $index" }
    }
    if (requireSignal) require(imgs.isNotEmpty() || barcode != null || product != null) { "Missing images, barcode, or product" }
    return this
}
