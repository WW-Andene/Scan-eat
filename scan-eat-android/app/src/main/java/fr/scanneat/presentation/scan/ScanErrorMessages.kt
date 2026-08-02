package fr.scanneat.presentation.scan

import retrofit2.HttpException

/** Reaches the user verbatim in the scan error banner — needs to actually say what to do. */
internal fun invalidApiKeyMessage(lang: String) =
    if (lang == "en") "Groq rejected this API key — check it in Settings"
    else "Clé API Groq refusée — vérifiez-la dans Réglages"

/**
 * Groq model names get deprecated/retired periodically (the pinned
 * DEFAULT_MODEL/FALLBACK_MODEL are compile-time literals) - when that
 * happens Groq returns a 400/404 for the model, not the more common
 * 401/403/429/5xx this app already has friendly messages for, and it'd
 * otherwise surface as a bare HTTP error with no indication that the fix
 * is just picking a current model in Settings.
 */
internal fun invalidModelMessage(lang: String) =
    if (lang == "en") "This AI model is no longer available — pick a current one in Settings"
    else "Ce modèle IA n'est plus disponible — choisissez-en un à jour dans Réglages"

/**
 * OcrParser already retries a 429 internally (see isRetryable), so reaching
 * this branch means every retry was also rate-limited — a transient-but-
 * persistent state distinct from the other HTTP error branches, which this
 * file previously had no message for despite invalidModelMessage's own
 * comment claiming 429 already had a friendly message.
 */
internal fun rateLimitedMessage(lang: String) =
    if (lang == "en") "Groq is rate-limiting requests right now — wait a moment and try again"
    else "Groq limite les requêtes en ce moment — patientez un instant puis réessayez"

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
 * identifyMultiFromPhotos()'s empty-list fallback - the server can legitimately
 * return zero items (no distinct food detected on the plate) without that being
 * a request failure, so this needs its own message rather than reusing
 * genericErrorMessage's "Unknown error", which would be actively misleading.
 */
internal fun noFoodsDetectedMessage(lang: String) =
    if (lang == "en") "No distinct foods were detected in the photo(s) — try a clearer shot"
    else "Aucun aliment distinct détecté sur la ou les photos — essayez une photo plus nette"

/**
 * Shared HttpException-code-to-friendly-message mapping - score()'s own failure
 * branch already special-cased 401/403/400/404/429 so a rejected API key/retired
 * model/rate limit reads as actionable guidance rather than "HTTP 401 ", but
 * identifyFromPhotos()/identifyMultiFromPhotos() previously fell straight through
 * to `e.message ?: genericErrorMessage(lang)` for the exact same failure modes,
 * leaking the raw OkHttp/Retrofit exception string on the identify path only.
 * Falls back to e.message for anything already wrapped with a localized message
 * elsewhere (e.g. ScanServerClient's serverUnreachableMessage), and to
 * genericErrorMessage(lang) only when there's truly nothing better to show.
 */
internal fun httpFriendlyMessage(e: Throwable, lang: String): String = when {
    e is HttpException && (e.code() == 401 || e.code() == 403) -> invalidApiKeyMessage(lang)
    e is HttpException && (e.code() == 400 || e.code() == 404) -> invalidModelMessage(lang)
    e is HttpException && e.code() == 429 -> rateLimitedMessage(lang)
    else -> e.message ?: genericErrorMessage(lang)
}
