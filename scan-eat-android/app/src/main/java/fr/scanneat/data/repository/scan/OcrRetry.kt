package fr.scanneat.data.repository.scan

import fr.scanneat.data.remote.api.ChatMessage
import fr.scanneat.data.remote.api.ChatRequest
import fr.scanneat.data.remote.api.Choice
import fr.scanneat.data.remote.api.ContentPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.pow
import kotlin.random.Random

// ============================================================================
// Multi-provider call/retry/fallback logic for OcrParser.
//
// Split out of OcrParser.kt (pure structural move, no behavior change).
// Declared as internal extension functions on OcrParser so they keep using
// its groqApi/cerebrasApi (now `internal`, was `private`, purely to allow
// this same-package split — no behavior change) instead of taking them as
// extra parameters.
// ============================================================================

/** Free-tier Cerebras vision-capable model, tried only after every Groq attempt fails. */
private const val CEREBRAS_MODEL = "llama-4-scout-17b-16e-instruct"

internal enum class Provider { GROQ, CEREBRAS }
internal data class ModelCandidate(val provider: Provider, val model: String)

/** Retryable: rate limiting (429), server errors (5xx), and transient network I/O failures. */
internal fun isRetryable(err: Throwable): Boolean = when (err) {
    is HttpException -> err.code() == 429 || err.code() in 500..599
    is IOException    -> true
    else              -> false
}

/**
 * Exponential backoff with jitter, replacing the old fixed `500L * attempt`
 * linear delay - same rough magnitude for the single inter-attempt wait this
 * file's 2-attempt-per-candidate budget ever actually takes (previously
 * ~500ms), but avoids every retrying client landing on the same delay in
 * lockstep against a momentarily-overloaded provider.
 *
 * Named distinctly from ScanRetryUtil.kt's backoffDelayMs (same package,
 * different default tuning - 250/150ms here vs 400/200ms there) - the two
 * were separate private functions in separate files before this split;
 * making both internal in the same package without renaming one would be a
 * same-signature redeclaration.
 */
internal fun ocrBackoffDelayMs(attempt: Int, baseDelayMs: Long = 250L, jitterMs: Long = 150L): Long =
    (baseDelayMs * 2.0.pow(attempt)).toLong() + Random.nextLong(0, jitterMs)

/**
 * Ordered candidate list: every Groq model first (only if a Groq key is
 * configured), then Cerebras as a fallback provider (only if configured).
 * A blank key means "not configured" — that provider is skipped entirely
 * rather than attempted and failing on a 401.
 */
internal fun buildCandidates(groqApiKey: String, cerebrasApiKey: String): List<Pair<ModelCandidate, String>> {
    val candidates = mutableListOf<Pair<ModelCandidate, String>>()
    if (groqApiKey.isNotBlank()) {
        candidates += ModelCandidate(Provider.GROQ, DEFAULT_MODEL) to groqApiKey
        candidates += ModelCandidate(Provider.GROQ, FALLBACK_MODEL) to groqApiKey
    }
    if (cerebrasApiKey.isNotBlank()) {
        candidates += ModelCandidate(Provider.CEREBRAS, CEREBRAS_MODEL) to cerebrasApiKey
    }
    return candidates
}

internal suspend fun OcrParser.callOnce(candidate: ModelCandidate, apiKey: String, content: List<ContentPart>, maxTokens: Int): Pair<Choice?, Boolean> {
    val request = ChatRequest(
        model     = candidate.model,
        messages  = listOf(ChatMessage(role = "user", content = content)),
        maxTokens = maxTokens,
    )
    val resp = when (candidate.provider) {
        Provider.GROQ     -> groqApi.chatCompletions("Bearer $apiKey", request)
        Provider.CEREBRAS -> cerebrasApi.chatCompletions("Bearer $apiKey", request)
    }
    val choice = resp.choices.firstOrNull()
    return choice to (choice?.finishReason == "length")
}

/**
 * Tries every configured (provider, model) candidate in order, each with its
 * own short retry loop for transient errors (429/5xx/IO) — only moves on to
 * the next candidate once the current one is exhausted or fails with a
 * non-retryable error (e.g. 401/404, meaning that model/key genuinely
 * doesn't work). A missing/invalid key on one provider no longer blocks
 * scanning entirely as long as another provider is configured.
 */
internal suspend fun OcrParser.callWithRetry(
    groqApiKey: String,
    cerebrasApiKey: String,
    content: List<ContentPart>,
    maxRetriesPerCandidate: Int = 2,
): String {
    val candidates = buildCandidates(groqApiKey, cerebrasApiKey)
    if (candidates.isEmpty()) throw IOException("No AI provider configured")
    var lastErr: Throwable? = null
    for ((candidate, apiKey) in candidates) {
        var attempt = 0
        while (attempt < maxRetriesPerCandidate) {
            val maxTokens = if (attempt > 0) 4000 else 2000
            val result = runCatching { callOnce(candidate, apiKey, content, maxTokens) }
            // runCatching also catches CancellationException — left unchecked, a
            // user leaving the scan screen mid-call would otherwise be treated as
            // just another retryable failure (isRetryable() returns false for it,
            // so the loop below) and fall through to the NEXT provider, firing a
            // brand-new billed network call instead of actually cancelling.
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            val (choice, truncated) = result.getOrNull() ?: (null to false)
            if (result.isSuccess) {
                if (truncated && attempt < maxRetriesPerCandidate - 1) {
                    lastErr = IOException("LLM response truncated at $maxTokens tokens")
                } else {
                    return choice?.message?.content ?: ""
                }
            } else {
                val err = result.exceptionOrNull()!!
                lastErr = err
                // Non-retryable (401/403/404/etc.) means this whole candidate is
                // dead, not just this attempt — stop retrying it and move on to
                // the next candidate immediately instead of burning the retry budget.
                if (!isRetryable(err)) break
            }
            attempt++
            if (attempt < maxRetriesPerCandidate) delay(ocrBackoffDelayMs(attempt))
        }
    }
    throw lastErr ?: RuntimeException("All AI provider/model candidates exhausted")
}
