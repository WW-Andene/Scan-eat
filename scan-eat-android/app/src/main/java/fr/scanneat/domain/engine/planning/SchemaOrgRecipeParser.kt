package fr.scanneat.domain.engine.planning

import fr.scanneat.data.repository.planning.FetchedRecipeResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// ============================================================================
// SCHEMA.ORG RECIPE PARSER — reads the structured Recipe data (JSON-LD) that
// recipe sites embed for search engines. Runs in the app on a page the user
// asked to import; previously this ran on the companion server.
// ============================================================================

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// Real schema.org blocks nest ~3-5 levels; a cheap pre-scan rejects pathological
// nesting before the recursive parser sees it.
private const val MAX_JSON_NESTING_DEPTH = 64

private fun exceedsJsonNestingDepth(raw: String, maxDepth: Int): Boolean {
    var depth = 0
    for (c in raw) {
        when (c) {
            '{', '[' -> { depth++; if (depth > maxDepth) return true }
            '}', ']' -> depth--
        }
    }
    return false
}

/** The first schema.org Recipe found in [html]'s JSON-LD blocks, or null if the page has none. */
fun parseSchemaOrgRecipe(html: String, sourceUrl: String): FetchedRecipeResult? {
    val blocks = Regex(
        """<script[^>]*type=["']application/ld\+json["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE,
    ).findAll(html).mapNotNull { m ->
        val raw = m.groupValues[1].trim()
        if (raw.isEmpty() || exceedsJsonNestingDepth(raw, MAX_JSON_NESTING_DEPTH)) return@mapNotNull null
        runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: runCatching {
                json.parseToJsonElement(raw.replace("&quot;", "\"").replace("&amp;", "&").replace("&#34;", "\""))
            }.getOrNull()
    }.toList()

    val recipe = blocks.firstNotNullOfOrNull { findRecipe(it) } ?: return null
    val nutrition = recipe["nutrition"] as? JsonObject
    val kcal = nutrition?.let { coerce(numFrom(it["calories"]), 10_000.0) }?.takeIf { it > 0.0 }
    return FetchedRecipeResult(
        name = cleanHtml(stringOr(recipe["name"])),
        servings = yieldToServings(recipe["recipeYield"])?.toString(),
        ingredients = stringArray(recipe["recipeIngredient"]).map { cleanHtml(it) }.filter { it.isNotEmpty() },
        steps = extractSteps(recipe["recipeInstructions"]).map { cleanHtml(it) }.filter { it.isNotEmpty() },
        cookTimeMinutes = parseISODuration(recipe["totalTime"]) ?: parseISODuration(recipe["cookTime"]),
        kcal = kcal,
        proteinG = kcal?.let { coerce(numFrom(nutrition["proteinContent"]), 1_000.0) },
        fatG = kcal?.let { coerce(numFrom(nutrition["fatContent"]), 1_000.0) },
        carbsG = kcal?.let { coerce(numFrom(nutrition["carbohydrateContent"]), 1_000.0) },
        sourceUrl = sourceUrl,
    )
}

private fun findRecipe(element: JsonElement): JsonObject? = when (element) {
    is JsonObject -> {
        val isRecipe = when (val type = element["@type"]) {
            is JsonPrimitive -> isRecipeType(type.contentOrNull)
            is JsonArray -> type.any { it is JsonPrimitive && isRecipeType(it.contentOrNull) }
            else -> false
        }
        if (isRecipe) element else element["@graph"]?.let { findRecipe(it) }
    }
    is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it) }
    else -> null
}

// "Recipe", but also the prefixed/IRI forms some sites emit ("schema:Recipe", "https://schema.org/Recipe").
private fun isRecipeType(type: String?): Boolean =
    type != null && (type == "Recipe" || type.endsWith("/Recipe") || type.endsWith(":Recipe"))

private val HTML_TAG = Regex("<[^>]*>")
private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
private val NAMED_ENTITY = Regex("&([a-zA-Z0-9]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "agrave" to "à", "acirc" to "â",
    "ccedil" to "ç", "ocirc" to "ô", "ucirc" to "û", "ugrave" to "ù", "icirc" to "î", "iuml" to "ï",
    "Eacute" to "É", "Egrave" to "È", "Agrave" to "À", "Ccedil" to "Ç", "oelig" to "œ",
    "rsquo" to "’", "lsquo" to "‘", "laquo" to "«", "raquo" to "»", "ndash" to "–", "mdash" to "—",
    "hellip" to "…", "deg" to "°", "euro" to "€", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
)

/**
 * JSON-LD text from recipe plugins routinely carries HTML: tags (`<br>`, `<p>`) and entities
 * (`&amp;`, `&#039;`, `&eacute;`). Shown raw they read as garbage in the imported recipe, so strip
 * tags, decode the common entities and collapse whitespace. Unknown entities are left untouched.
 */
private fun cleanHtml(s: String): String {
    var t = HTML_TAG.replace(s, " ")
    t = NUMERIC_ENTITY.replace(t) { m ->
        val g = m.groupValues[1]
        val cp = if (g.startsWith("x") || g.startsWith("X")) g.substring(1).toIntOrNull(16) else g.toIntOrNull()
        if (cp != null && cp in 1..0x10FFFF) String(Character.toChars(cp)) else m.value
    }
    t = NAMED_ENTITY.replace(t) { m -> NAMED_ENTITIES[m.groupValues[1]] ?: m.value }
    return t.replace(Regex("\\s+"), " ").trim()
}

private fun stringOr(element: JsonElement?, fallback: String = ""): String =
    (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

private fun stringArray(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { e ->
        when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> (e["text"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
    is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
    else -> emptyList()
}

private fun extractSteps(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.flatMap { s ->
        when (s) {
            is JsonPrimitive -> listOfNotNull(s.contentOrNull?.trim())
            // HowToSection groups its own HowToStep list.
            is JsonObject -> if (s["itemListElement"] != null) extractSteps(s["itemListElement"])
                else listOfNotNull(((s["text"] ?: s["name"]) as? JsonPrimitive)?.contentOrNull?.trim())
            else -> emptyList()
        }
    }.filter { it.isNotEmpty() }
    is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf { it.isNotEmpty() })
    else -> emptyList()
}

/**
 * Null when recipeYield is absent or unparsable - never a fabricated "1 serving". Also null for a
 * count outside 1..100: a yield like "500 g" / "1 litre" would otherwise become 500 servings, and
 * "0" would put a zero divisor into every per-serving figure downstream.
 */
private fun yieldToServings(element: JsonElement?): Int? {
    val n = when (element) {
        is JsonPrimitive -> element.intOrNull ?: Regex("(\\d+)").find(element.contentOrNull ?: "")?.groupValues?.get(1)?.toIntOrNull()
        is JsonArray -> return yieldToServings(element.firstOrNull())
        else -> null
    }
    return n?.takeIf { it in 1..100 }
}

/**
 * Minutes, or null when absent/unparsable/zero. A zero duration ("PT0M", "PT", "PT0S" - common
 * placeholders on pages with no timing) must read as "unknown", otherwise it is non-null and the
 * caller's `totalTime ?: cookTime` never falls back to the real cookTime.
 */
private fun parseISODuration(element: JsonElement?): Int? {
    val s = (element as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    val m = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$", RegexOption.IGNORE_CASE).matchEntire(s) ?: return null
    val days = m.groupValues[1].toIntOrNull() ?: 0
    val hours = m.groupValues[2].toIntOrNull() ?: 0
    val minutes = m.groupValues[3].toIntOrNull() ?: 0
    val seconds = m.groupValues[4].toIntOrNull() ?: 0
    return (days * 24 * 60 + hours * 60 + minutes + seconds / 60).takeIf { it > 0 }
}

// Values often carry units in the string ("350 calories", "12 g").
private fun numFrom(element: JsonElement?): Double = when (element) {
    is JsonPrimitive -> element.doubleOrNull ?: run {
        val content = element.contentOrNull?.replace(",", ".") ?: ""
        content.toDoubleOrNull() ?: Regex("""(\d+(?:\.\d+)?)""").find(content)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }
    else -> 0.0
}

// A page can declare anything (negative, absurd); bound it before it reaches the diary.
private fun coerce(v: Double, max: Double): Double = v.coerceIn(0.0, max)
