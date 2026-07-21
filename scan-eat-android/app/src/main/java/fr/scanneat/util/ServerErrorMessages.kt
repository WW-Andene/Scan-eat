package fr.scanneat.util

// Reaches the user verbatim (ScanViewModel/RecipeViewModel show e.message directly
// in the error banner) - shared by ScanRepository and RecipeRepository, both of
// which check ApiMode.SERVER's configured URL before calling out to it.
fun serverUrlMissingMessage(lang: String) =
    if (lang == "en") "Server URL not configured — set it up in Settings"
    else "URL du serveur non configurée — configurez-la dans Réglages"
