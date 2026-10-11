package fr.scanneat.presentation.scan

import retrofit2.HttpException

/** Open Food Facts kept refusing after the automatic retries (HTTP 429). */
internal fun rateLimitedMessage(lang: String) =
    if (lang == "en") "Open Food Facts is limiting requests right now — wait a moment and try again"
    else "Open Food Facts limite les requêtes en ce moment — patientez un instant puis réessayez"

/** Open Food Facts returned a 5xx on every automatic retry (see ScanRetryUtil.kt). */
internal fun serviceUnavailableMessage(lang: String) =
    if (lang == "en") "Open Food Facts is temporarily unavailable — this was already retried automatically; wait a moment and try again"
    else "Open Food Facts est temporairement indisponible — une nouvelle tentative automatique a déjà eu lieu ; patientez un instant puis réessayez"

internal fun noInputMessage(lang: String) =
    if (lang == "en") "Scan a barcode or take a photo"
    else "Scannez un code-barres ou prenez une photo"

/**
 * identifyFromPhotos() previously reused noInputMessage() for its offline branch
 * even though photos are guaranteed present there (the function early-returns on
 * an empty queue before this check) - a user with photos already queued, offline,
 * trying to identify unlabeled produce, got told to "scan a barcode or take a
 * photo" instead of the real, actionable problem. Mirrors ScanRepository's own
 * private offlineMessage(), which every barcode/label-parsing path already uses.
 */
internal fun offlineMessage(lang: String) =
    if (lang == "en") "No internet connection" else "Pas de connexion internet"

/**
 * Fallback for the `else` branch of the scan-failure `when` — every sibling
 * branch (invalidApiKeyMessage/invalidModelMessage/rateLimitedMessage/
 * noInputMessage) routes through lang, but this default case used to hardcode
 * the bare French literal "Erreur inconnue", so English-language users hit a
 * French message whenever an unrecognized exception surfaced.
 */
internal fun genericErrorMessage(lang: String) =
    if (lang == "en") "Unknown error"
    else "Erreur inconnue"

/**
 * Fallback for ProductNotFoundException's `e.message ?: ...` in ScanViewModel -
 * the exception's own message is always lang-aware in practice, but this
 * `?:` fallback previously hardcoded the French literal "Produit introuvable",
 * same landmine class genericErrorMessage's own doc comment describes.
 */
internal fun productNotFoundMessage(lang: String) =
    if (lang == "en") "Product not found"
    else "Produit introuvable"

/**
 * Friendly text for a network failure reaching the user verbatim in the scan
 * error banner (only barcode lookups touch the network now - photos are read
 * on the device). Falls back to e.message for already-localized errors.
 */
internal fun httpFriendlyMessage(e: Throwable, lang: String): String = when {
    e is HttpException && e.code() == 404 -> productNotFoundMessage(lang)
    e is HttpException && e.code() == 429 -> rateLimitedMessage(lang)
    e is HttpException && e.code() in 500..599 -> serviceUnavailableMessage(lang)
    else -> e.message ?: genericErrorMessage(lang)
}
