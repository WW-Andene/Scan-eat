package fr.scanneat.domain.engine.planning

import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.domain.engine.nutrition.FOOD_DB
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.nutrition.searchFoodDB
import java.text.Normalizer

// ============================================================================
// INGREDIENT LINE PARSER — turns a free-text recipe ingredient line
// ("250 g de farine", "3 œufs", "2 c. à soupe d'huile d'olive", "1/2 citron")
// into a quantity in grams and a built-in food-database entry, so an imported
// recipe gets real nutrition instead of a block of text to retype by hand.
// The former AI import returned quantity/unit/name; this does it locally.
// ============================================================================

data class ParsedIngredient(
    val line: String,
    val food: FoodEntry?,
    /** Grams, or null when the line gives no usable quantity. */
    val grams: Double?,
    /** True when grams came from a typical-piece weight, not a stated weight/volume. */
    val gramsEstimated: Boolean,
)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("""\s+""")
private val PARENTHETICAL = Regex("""\(.*?\)""")
private val LEADING_BULLET = Regex("""^[-•*·\s]+""")
private val LEADING_PARTICLE = Regex("""^(de |d'|du |des |of )""")
private val TRAILING_CLAUSE = Regex("""[,;].*$""")

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        .replace("œ", "oe")

// Unit → grams per unit. Volumes use water density (close enough for
// cooking liquids); spoons/cups use standard kitchen measures.
private val UNITS: List<Pair<Regex, Double>> = listOf(
    Regex("""kg|kilos?|kilogrammes?""") to 1000.0,
    Regex("""mg|milligrammes?""") to 0.001,
    Regex("""g|gr|grammes?|grams?""") to 1.0,
    Regex("""l|litres?|liters?""") to 1000.0,
    Regex("""dl|decilitres?""") to 100.0,
    Regex("""cl|centilitres?""") to 10.0,
    Regex("""ml|millilitres?""") to 1.0,
    // Matched after accent stripping, so "c. à soupe" arrives as "c. a soupe".
    Regex("""(?:c|cuill?eres?)\.?\s*a\.?\s*(?:soupe|s)\.?|cs|tbsp|tablespoons?""") to 15.0,
    Regex("""(?:c|cuill?eres?)\.?\s*a\.?\s*(?:cafe|c)\.?|cc|tsp|teaspoons?""") to 5.0,
    Regex("""tasses?|cups?|verres?""") to 240.0,
    Regex("""pinc[eé]es?|pinch(?:es)?""") to 0.5,
    Regex("""sachets?""") to 10.0,
    Regex("""noix|knobs?""") to 15.0,
    Regex("""gousses?|cloves?""") to 5.0,
    Regex("""tranches?|slices?""") to 30.0,
    Regex("""poign[eé]es?|handfuls?""") to 30.0,
)

private val WORD_NUMBERS = mapOf(
    "un" to 1.0, "une" to 1.0, "deux" to 2.0, "trois" to 3.0, "quatre" to 4.0, "cinq" to 5.0,
    "six" to 6.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "half" to 0.5, "demi" to 0.5,
)
private val VULGAR = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

// "1 1/2", "1/2", "1½", "½", "1,5", "1.5", "2-3" (takes the first)
private val MIXED_FRACTION = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)""")
private val FRACTION = Regex("""^(\d+)\s*/\s*(\d+)""")
private val DECIMAL_OR_VULGAR = Regex("""^(\d+(?:[.,]\d+)?)?\s*([½¼¾⅓⅔])?(?:\s*-\s*\d+(?:[.,]\d+)?)?""")

// Words that describe the ingredient rather than name it.
private val FILLER = Regex("""\b(de|d'|du|des|of|the|a|an|frais|fraiche|fresh|bio|environ|about|gros|grosse|petits?|petites?|moyens?|moyennes?|large|small|medium|hache[es]?|chopped|emince[es]?|sliced|rape[es]?|grated|en des|en morceaux|pour la cuisson|facultatif|optional)\b""")

fun parseIngredientLine(line: String): ParsedIngredient {
    var rest = norm(line).replace(PARENTHETICAL, " ").replace(LEADING_BULLET, "").trim()

    // ---- quantity ----
    var qty: Double? = null
    WORD_NUMBERS.entries.firstOrNull { (w, _) -> Regex("""^$w\b""").containsMatchIn(rest) }?.let { (w, v) ->
        qty = v; rest = rest.removePrefix(w).trim()
    }
    if (qty == null) {
        MIXED_FRACTION.find(rest)?.let { m ->
            qty = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / m.groupValues[3].toDouble().coerceAtLeast(1.0)
            rest = rest.substring(m.range.last + 1).trim()
        } ?: FRACTION.find(rest)?.let { m ->
            qty = m.groupValues[1].toDouble() / m.groupValues[2].toDouble().coerceAtLeast(1.0)
            rest = rest.substring(m.range.last + 1).trim()
        } ?: DECIMAL_OR_VULGAR.find(rest)?.takeIf { it.value.isNotBlank() }?.let { m ->
            val whole = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0
            val frac = m.groupValues[2].firstOrNull()?.let { VULGAR[it] } ?: 0.0
            qty = (whole + frac).takeIf { it > 0 }
            rest = rest.substring(m.range.last + 1).trim()
        }
    }

    // ---- unit ----
    var gramsPerUnit: Double? = null
    for ((re, g) in UNITS) {
        val m = Regex("""^(?:$re)\.?(?![a-z])""").find(rest) ?: continue
        gramsPerUnit = g; rest = rest.substring(m.range.last + 1).trim(); break
    }

    // ---- food ----
    val cleaned = rest.replace(LEADING_PARTICLE, "").replace(TRAILING_CLAUSE, "").trim()
    val food = matchFood(cleaned)

    val (grams, estimated) = when {
        qty != null && gramsPerUnit != null -> qty!! * gramsPerUnit!! to false
        qty == null && gramsPerUnit != null -> gramsPerUnit!! to false // "une pincée de sel" handled above; bare unit = 1
        qty != null && food != null -> qty!! * (food.typicalPortionG ?: 100.0) to true
        else -> null to false
    }
    return ParsedIngredient(line.trim(), food, grams?.takeIf { it > 0 }, estimated)
}

/** The built-in food a free-text name refers to (exact name/alias first, then search). */
fun findFoodByName(name: String): FoodEntry? = matchFood(norm(name).trim())

/** Longest phrase of the line that names a built-in food, then shorter word windows. */
private fun matchFood(text: String): FoodEntry? {
    if (text.isBlank()) return null
    // An exact name/alias match on any word window wins over prefix search
    // ("farine" is the plain flour, not "farine complète"). Windows keep
    // linking words ("pommes de terre"); plurals are tried word by word.
    val raw = text.replace("'", "' ").split(WHITESPACE).filter { it.isNotBlank() }
    for (size in raw.size downTo 1) {
        for (start in 0..raw.size - size) {
            val window = raw.subList(start, start + size)
            if (FILLER.matches(window.first()) || FILLER.matches(window.last())) continue
            val phrase = window.joinToString(" ")
            exactFood(phrase)?.let { return it }
            exactFood(window.joinToString(" ") { w -> if (w.length > 3 && (w.endsWith("s") || w.endsWith("x"))) w.dropLast(1) else w })?.let { return it }
        }
    }
    val words = FILLER.replace(text, " ").split(WHITESPACE).filter { it.length >= 2 }
    searchFoodDB(text, limit = 1).firstOrNull()?.let { return it }
    for (size in words.size downTo 1) {
        for (start in 0..words.size - size) {
            val phrase = words.subList(start, start + size).joinToString(" ")
            // Singular form too ("oeufs" -> "oeuf", "tomates" -> "tomate").
            val hit = searchFoodDB(phrase, limit = 1).firstOrNull()
                ?: phrase.takeIf { it.endsWith("s") || it.endsWith("x") }?.let { searchFoodDB(it.dropLast(1), limit = 1).firstOrNull() }
            if (hit != null) return hit
        }
    }
    return null
}

private val EXACT_INDEX: Map<String, FoodEntry> by lazy {
    buildMap {
        // Real names first, so an alias on another entry never shadows a food's own name.
        for (f in FOOD_DB) putIfAbsent(key(f.name), f)
        for (f in FOOD_DB) f.aliases.forEach { putIfAbsent(key(it), f) }
    }
}

private fun key(s: String) = norm(s).replace("'", " ").replace(WHITESPACE, " ").trim()

private fun exactFood(phrase: String): FoodEntry? = EXACT_INDEX[key(phrase)]

fun ParsedIngredient.toRecipeComponent(): RecipeComponent? {
    val f = food ?: return null
    val g = grams ?: return null
    return RecipeComponent(
        productName = f.name, grams = g,
        kcal = f.kcal * g / 100.0, proteinG = f.proteinG * g / 100.0, carbsG = f.carbsG * g / 100.0,
        fatG = f.fatG * g / 100.0, fiberG = f.fiberG * g / 100.0, saltG = f.saltG * g / 100.0,
        saturatedFatG = f.saturatedFatG * g / 100.0, sugarsG = f.sugarsG * g / 100.0,
    )
}
