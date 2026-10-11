package fr.scanneat.presentation.foodsearch

/** One external reference link offered for the current query - opened in the
 *  device's browser, never fetched/scraped in-app. */
data class SourceLink(val label: String, val url: String)

/**
 * A handful of well-known, query-parameterized reference sites - not a scraped
 * or fabricated result list, just the same "search on X" links a user could
 * type into their browser by hand, offered up front for whatever they typed
 * here (a food name, an additive code like "E330", a molecule). Ordered by
 * authority rather than alphabetically: official government/regulatory
 * bodies (USDA, ANSES, EFSA) and the official chemical database (PubChem)
 * first, Open Food Facts (crowdsourced but domain-specific) next, Wikipedia
 * (user-editable, general-purpose) last - a quick-overview link, not a
 * primary source. Wikipedia's domain follows the app's own language so
 * results come back in the right language rather than always French.
 */
internal fun buildSourceLinks(query: String, lang: String): List<SourceLink> {
    if (query.isBlank()) return emptyList()
    val q = java.net.URLEncoder.encode(query, "UTF-8")
    val wikiDomain = if (lang == "en") "en.wikipedia.org" else "fr.wikipedia.org"
    val wikiLabel = if (lang == "en") "Wikipedia (quick overview)" else "Wikipédia (aperçu rapide)"
    val efsaLabel = if (lang == "en") "EFSA (European authority)" else "EFSA (Autorité européenne)"
    return listOf(
        SourceLink("ANSES – Table CIQUAL", "https://ciqual.anses.fr/#/aliments?query=$q"),
        SourceLink("USDA FoodData Central", "https://fdc.nal.usda.gov/fdc-app.html#/?query=$q"),
        SourceLink(efsaLabel, "https://www.efsa.europa.eu/en/search?search_api_fulltext=$q"),
        SourceLink("PubChem", "https://pubchem.ncbi.nlm.nih.gov/#query=$q"),
        SourceLink("Open Food Facts", "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q"),
        SourceLink(wikiLabel, "https://$wikiDomain/wiki/Special:Search?search=$q"),
    )
}
