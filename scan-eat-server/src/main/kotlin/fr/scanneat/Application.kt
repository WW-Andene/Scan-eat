package fr.scanneat

import fr.scanneat.routing.*
import fr.scanneat.service.GroqService
import fr.scanneat.service.OffService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.Properties

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

// Generated from build.gradle.kts' `version =` via processResources — read once
// at startup instead of hardcoding a literal that silently drifts from it.
private val SERVER_VERSION: String by lazy {
    val props = Properties()
    object {}.javaClass.getResourceAsStream("/version.properties")?.use { props.load(it) }
    props.getProperty("version") ?: "unknown"
}

fun Application.module() {
    // ---- Services (singleton pattern — no DI framework for simplicity) ----
    val groqService = GroqService()
    val offService  = OffService()
    // Both services expose close() for their underlying CIO HttpClient, but nothing
    // called it — harmless under normal process-exit (the OS reclaims the sockets),
    // but wire it up properly so it's not dead cleanup code.
    environment.monitor.subscribe(ApplicationStopping) {
        groqService.close()
        offService.close()
    }

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
    // RateLimiter and fetchRecipeRoute's SSRF guard both key off request.origin.remoteHost.
    // Without this plugin that's always the direct TCP peer - every deployment this README
    // recommends (Docker behind a reverse proxy, Railway/Render/Fly.io) puts a proxy in
    // front, so remoteHost is the proxy's own IP for every client and the whole client base
    // collapses into one rate-limit bucket. XForwardedHeaders makes remoteHost follow
    // X-Forwarded-For instead.
    //
    // useLastProxy() (not the plugin's default useFirstProxy()) is what actually delivers
    // that intent: a standards-compliant reverse proxy *appends* its own observed IP rather
    // than replacing the header ("X-Forwarded-For: <whatever the client sent>, <proxy's real
    // observed IP>"), so the *last* entry is the only one the proxy itself vouches for - the
    // default trusts the *first* (leftmost) entry instead, which is exactly the value a
    // client can set itself, silently defeating the limiter/SSRF guard even in the documented
    // single-reverse-proxy deployments this comment describes, not just a no-proxy exposure.
    install(XForwardedHeaders) { useLastProxy() }
    // Some reverse proxies/orchestrators/uptime monitors send HEAD (not GET)
    // for liveness checks - without this, /health only answers GET and those
    // probes see a 404/405 instead of a clean liveness signal.
    install(AutoHeadResponse)
    install(Compression) { gzip { priority = 1.0 } }
    val isDevelopment = developmentMode
    install(CORS) {
        // The only client is the Android app, which doesn't go through CORS at
        // all (that's a browser-only mechanism) — anyHost() only matters once a
        // browser-based client exists, and until then it's needless exposure.
        // ALLOWED_ORIGINS is a comma-separated allowlist for that day; absent
        // any config, only development mode falls back to anyHost().
        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }
        if (!allowedOrigins.isNullOrEmpty()) {
            allowedOrigins.forEach { allowHost(it, schemes = listOf("http", "https")) }
        } else if (isDevelopment) {
            anyHost()
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        // Every route authenticates via X-Groq-Key (RouteHelpers.resolveGroqKey),
        // not Authorization - without this a browser client's preflight fails and
        // Direct-mode requests carrying their own key are silently blocked.
        allowHeader("X-Groq-Key")
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
        get("/health") { call.respond(mapOf("status" to "ok", "version" to SERVER_VERSION)) }

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
