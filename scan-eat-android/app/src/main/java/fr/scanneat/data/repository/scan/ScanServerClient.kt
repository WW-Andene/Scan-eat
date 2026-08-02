package fr.scanneat.data.repository.scan

import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.util.serverUnreachableMessage
import fr.scanneat.util.serverUrlMissingMessage
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * SERVER-mode network calls extracted out of ScanRepository verbatim - same
 * retry/backoff policy, same request shapes, same NonFoodProductException
 * signalling, just grouped in their own file since they're the cohesive
 * "talk to scan-eat-server" concern within ScanRepository. ScanRepository
 * holds one instance of this and delegates to it; nothing about the public
 * ScanRepository API changes.
 */
internal class ScanServerClient(
    private val serverApiProvider: ServerScanApiProvider,
) {
    /**
     * Retries transient failures (429/5xx/IO) the same way fetchOffProduct/
     * OcrParser.callWithRetry already do for their own network calls - previously
     * a single unguarded call, so the server's own rate limit (30 req/min/IP,
     * see scan-eat-server's RateLimiter) or a momentary 5xx failed the whole
     * scan for SERVER-mode users with no retry, unlike every other network path
     * in this repository.
     */
    /**
     * Shared retry/backoff for scoreViaServer/identifyViaServer - both retried
     * the identical class of transient error (429/5xx/IO) with the identical
     * 3-attempt backoff loop, verbatim, differing only in which server call
     * they make. Extracted here so that logic (and any future tuning of it)
     * exists in exactly one place.
     */
    private suspend fun <T> retryServerCall(lang: String, block: suspend () -> T): T {
        var lastErr: Throwable? = null
        repeat(SERVER_MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() != 429 && e.code() !in 500..599) throw e
                lastErr = e
            } catch (e: IOException) {
                lastErr = e
            }
            if (attempt < SERVER_MAX_ATTEMPTS - 1) delay(backoffDelayMs(attempt))
        }
        // Every attempt against a *configured* server URL failed with an
        // IOException (DNS failure, connection refused, timeout - as opposed to
        // an HttpException, which means the server WAS reached but returned an
        // error) - previously rethrown as-is, surfacing either a raw OkHttp
        // exception string or falling through to the generic "unknown error"
        // message, neither of which told a self-hosting user their server
        // itself is unreachable rather than some other failure.
        val err = lastErr!!
        if (err is IOException) throw Exception(serverUnreachableMessage(lang), err)
        throw err
    }

    suspend fun scoreViaServer(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        barcode: String?,
        lang: String,
        model: String,
    ): ScanResult {
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerScoreRequest(
            images  = images.map { ServerImageDto(it.base64, it.mime) },
            barcode = barcode,
            lang    = lang,
            model   = model,
        )
        val response = retryServerCall(lang) {
            serverApiProvider.get(serverUrl).score(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        }
        // Same check as DIRECT mode's scoreDirectBarcode - the server already ran
        // classifyNonFood() against the barcode's own OFF category tags and skipped
        // LLM augmentation, but still returns a (unused) fallback score for backward
        // compatibility. This client understands the flag, so it shows the same
        // NonConsumableFound UI instead of that score.
        response.nonFoodCategory?.let { category ->
            throw NonFoodProductException(
                productName = response.product.name,
                brand       = response.nonFoodBrand ?: "",
                category    = category,
            )
        }
        return response.toDomain(lang)
    }

    /**
     * SERVER-mode counterpart to DIRECT mode's ocrParser.identifyFood() - calls
     * the server's own POST /api/identify (see scan-eat-server's IdentifyRoute),
     * which already existed but was never called from here: identifyOrScoreFromImages
     * previously ignored identifyMode entirely for SERVER mode and always fell
     * through to scoreViaServer's label-OCR path, so a SERVER-mode user tapping
     * "Identifier sans étiquette" on fresh produce or a plated dish silently got
     * whatever that path returned for a non-label photo. Same retry/backoff
     * policy as scoreViaServer.
     */
    suspend fun identifyViaServer(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        lang: String,
    ): ScanResult {
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        return retryServerCall(lang) {
            serverApiProvider.get(serverUrl).identify(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request).toDomain(lang)
        }
    }

    /**
     * SERVER-mode counterpart to identifyViaServer(), for a plate holding several
     * distinct foods - calls the server's own POST /api/identify-multi (see
     * scan-eat-server's IdentifyRoute), which already existed but was never
     * called from here: a user photographing a plate with several different
     * foods could previously only ever identify one item at a time via
     * identifyOrScoreFromImages/identify. DIRECT mode's ocrParser.identifyFood()
     * has no multi-item equivalent (it always returns a single Product), so this
     * skips the ApiMode branch entirely and goes straight to the server - same
     * "Server-mode only import" shape as RecipeRepository's fetchRecipeFromUrl/
     * identifyRecipeFromPhotos, which likewise have no DIRECT-mode counterpart
     * and never check prefs.apiMode either. Same retry/backoff policy as
     * scoreViaServer/identifyViaServer. Each returned item is rescored locally
     * via ServerIdentifyResponse.toDomain(), same as the single-item path.
     */
    suspend fun identifyMulti(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        lang: String,
    ): List<ScanResult> {
        if (serverUrl.isBlank()) error(serverUrlMissingMessage(lang))
        val request = ServerImagesRequest(images = images.map { ServerImageDto(it.base64, it.mime) }, lang = lang)
        val response = retryServerCall(lang) {
            serverApiProvider.get(serverUrl).identifyMulti(groqKey = apiKey.takeIf { it.isNotBlank() }, request = request)
        }
        return response.items.map { it.toDomain(lang) }
    }

    private companion object {
        /** Same retry budget as ScanOffLookup.OFF_MAX_ATTEMPTS, for scoreViaServer's own transient-error retry. */
        const val SERVER_MAX_ATTEMPTS = 3
    }
}
