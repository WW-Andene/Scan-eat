package fr.scanneat.routing

import fr.scanneat.model.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// ============================================================================
// SHARED PER-CLIENT RATE LIMITER
//
// Previously only /api/fetch-recipe had any throttling (it needs no Groq key
// at all, so it was the obviously-anonymous one). But every other route only
// requires a key when the operator hasn't set GROQ_API_KEY - in the very
// common "Server mode" deployment (operator sets GROQ_API_KEY, app never
// sends X-Groq-Key), /api/score, /api/identify, /api/identify-multi,
// /api/identify-menu, /api/identify-recipe, /api/suggest-recipes and
// /api/suggest-from-pantry are all wide open, fully anonymous endpoints that
// spend the operator's paid Groq quota on every hit - up to a 12 MB vision
// payload per call. Anyone who finds the server URL could hammer these to
// run up the operator's bill or exhaust the CIO connection pool, with
// nothing standing in the way. This is a fixed-window counter per client IP,
// the same approach already used (and reviewed) for fetch-recipe, extracted
// here so every LLM-calling route can share one throttle instead of each
// reinventing it.
// ============================================================================

class RateLimiter(private val maxRequests: Int, private val windowMs: Long) {

    private class Window(@Volatile var count: Int, @Volatile var windowStartMs: Long)

    private val state = ConcurrentHashMap<String, Window>()

    // Every distinct clientKey that ever calls a limited route earns an entry
    // here unless something removes stale ones. Since the key space is
    // attacker-reachable (anyone hitting the server can vary their apparent
    // client key), this would otherwise grow without bound as traffic scales.
    // Sweeping opportunistically keeps it bounded to roughly the number of
    // distinct clients active within one window, without a background thread.
    private val callCount = AtomicLong(0)
    private val sweepInterval = 200L

    private fun sweepExpired(nowMs: Long) {
        state.entries.removeIf { (_, window) -> nowMs - window.windowStartMs >= windowMs }
    }

    /** True when [clientKey] has exceeded [maxRequests] in the current window. */
    fun isLimited(clientKey: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (callCount.incrementAndGet() % sweepInterval == 0L) {
            sweepExpired(nowMs)
        }
        var limited = false
        state.compute(clientKey) { _, existing ->
            if (existing == null || nowMs - existing.windowStartMs >= windowMs) {
                Window(1, nowMs)
            } else {
                existing.count += 1
                limited = existing.count > maxRequests
                existing
            }
        }
        return limited
    }
}

// All LLM-calling routes share one budget per client IP: generous enough for
// normal interactive use (a user scanning/identifying several items in a
// row) while making a scripted hammering run cost the attacker many separate
// source IPs instead of one unauthenticated HTTP client.
val llmRateLimiter = RateLimiter(maxRequests = 30, windowMs = 60_000L)

/**
 * Reject with 429 and return true if the caller has exceeded [limiter]'s
 * budget for their client IP. Callers should check this before doing any
 * paid/expensive work (LLM calls, outbound fetches).
 */
suspend fun ApplicationCall.rejectIfRateLimited(limiter: RateLimiter): Boolean {
    val clientKey = request.origin.remoteHost
    if (limiter.isLimited(clientKey)) {
        respond(HttpStatusCode.TooManyRequests, ErrorResponse("rate_limit"))
        return true
    }
    return false
}
