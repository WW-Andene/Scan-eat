package fr.scanneat.util

// Reaches the user verbatim (ScanViewModel/RecipeViewModel show e.message directly
// in the error banner) - shared by ScanRepository and RecipeRepository, both of
// which check ApiMode.SERVER's configured URL before calling out to it.
fun serverUrlMissingMessage(lang: String) =
    if (lang == "en") "Server URL not configured — set it up in Settings"
    else "URL du serveur non configurée — configurez-la dans Réglages"

/**
 * Shown when every retry attempt against a *configured* server URL fails with
 * an IOException (DNS failure, connection refused, timeout) - distinct from
 * [serverUrlMissingMessage] (no URL set at all). Previously this case fell
 * through to the generic "unknown error" message, or a raw OkHttp exception
 * string like "Unable to resolve host" - neither told a self-hosting user
 * what actually went wrong (their server is down, or the URL/network is
 * wrong) or what to check.
 */
fun serverUnreachableMessage(lang: String) =
    if (lang == "en") "Can't reach your server — check the URL in Settings and that it's running"
    else "Impossible de joindre votre serveur — vérifiez l'URL dans Réglages et qu'il est bien lancé"
