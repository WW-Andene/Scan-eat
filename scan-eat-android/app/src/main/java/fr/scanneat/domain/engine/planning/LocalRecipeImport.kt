package fr.scanneat.domain.engine.planning

import fr.scanneat.data.repository.planning.FetchedRecipeResult
import fr.scanneat.domain.engine.nutrition.FOOD_DB_BY_NAME
import fr.scanneat.domain.engine.nutrition.OFFICIAL_RECIPE_DB
import fr.scanneat.domain.engine.nutrition.OcrLine
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.wordBoundaryMatch
import java.text.Normalizer
import kotlin.math.roundToInt

// ============================================================================
// LOCAL RECIPE IMPORT — on-device replacements for the former AI recipe
// features: reading a photographed recipe card/cookbook page, and suggesting
// recipes for an ingredient or the pantry from the built-in recipe database.
// Results only pre-fill the add-recipe dialog for the user to review.
// ============================================================================

private fun norm(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

private val INGREDIENTS_HEADER = Regex("""^ingredients?\b""")
private val STEPS_HEADER = Regex("""^(preparation|etapes?|instructions?|methode|method|directions|recette)\b""")
private val QUANTITY_START = Regex("""^[-•*·]?\s*(\d+[\d.,/]*|[½¼¾⅓]|une?|deux|trois|quatre|cinq|six|quelques|a pinch|une pincee|1/2)\b""")
private val UNIT = Regex("""\b(\d+\s*(g|kg|mg|ml|cl|dl|l)|c\.?\s*a\.?\s*[sc]|cuill|cuillere|tasse|cups?|tbsp|tsp|pincee|sachet|gousses?|tranches?|boites?)\b""")
private val STEP_NUMBER = Regex("""^(\d+)[.)]\s*""")
private val SERVINGS = Regex("""(\d+)\s*(personnes?|pers\.?|portions?|parts?|servings?|people)""")
private val MINUTES = Regex("""(\d+)\s*(min|minutes)\b""")
private val HOURS = Regex("""(\d+)\s*h(?:eures?|ours?)?\b""")

/** Reads a recipe from OCR lines of a recipe card or cookbook page. */
fun parseRecipeText(lines: List<OcrLine>): FetchedRecipeResult? {
    val ordered = lines.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
    if (ordered.isEmpty()) return null

    val ingredients = mutableListOf<String>()
    val steps = mutableListOf<String>()
    var section = 0 // 0 = before any header, 1 = ingredients, 2 = steps
    for (line in ordered) {
        val text = line.text.trim()
        val n = norm(text)
        when {
            INGREDIENTS_HEADER.containsMatchIn(n) -> { section = 1; continue }
            STEPS_HEADER.containsMatchIn(n) -> { section = 2; continue }
        }
        val looksLikeIngredient = QUANTITY_START.containsMatchIn(n) || UNIT.containsMatchIn(n)
        when {
            section == 1 || (section == 0 && looksLikeIngredient) -> ingredients += text.trimStart('-', '•', '*', '·', ' ')
            section == 2 -> {
                // A numbered line starts a step; anything else continues the previous one.
                val m = STEP_NUMBER.find(text)
                if (m != null || steps.isEmpty()) steps += text.removeRange(m?.range ?: IntRange.EMPTY).trim()
                else steps[steps.lastIndex] = steps.last() + " " + text
            }
        }
    }
    if (ingredients.isEmpty() && steps.isEmpty()) return null

    val all = norm(ordered.joinToString(" ") { it.text })
    val minutes = MINUTES.findAll(all).sumOf { it.groupValues[1].toInt() } + HOURS.findAll(all).sumOf { it.groupValues[1].toInt() * 60 }
    val name = ordered.filter { it.top <= ordered.maxOf { l -> l.bottom } * 0.3 }
        .filter { l -> norm(l.text).let { n -> n.count(Char::isLetter) >= 3 && !INGREDIENTS_HEADER.containsMatchIn(n) && !STEPS_HEADER.containsMatchIn(n) } }
        .maxByOrNull { it.height }?.text?.trim().orEmpty()
    return FetchedRecipeResult(
        name = name,
        servings = SERVINGS.find(all)?.groupValues?.get(1),
        ingredients = ingredients,
        steps = steps,
        cookTimeMinutes = minutes.takeIf { it > 0 },
        kcal = null, proteinG = null, fatG = null, carbsG = null,
        sourceUrl = "",
    )
}

/** Built-in recipes using [wanted] ingredients, best coverage first. */
fun suggestOfficialRecipes(wanted: List<String>, english: Boolean, limit: Int = 8): List<FetchedRecipeResult> {
    val keys = wanted.map(::norm).filter { it.length >= 2 }.distinct()
    if (keys.isEmpty()) return emptyList()
    return OFFICIAL_RECIPE_DB.mapNotNull { r ->
        val matched = r.ingredients.count { ing -> keys.any { k -> ingredientMatches(ing.foodName, k) } }
        if (matched == 0) null else Triple(r, matched, matched.toDouble() / r.ingredients.size)
    }
        .sortedWith(compareByDescending<Triple<OfficialRecipe, Int, Double>> { it.second }.thenByDescending { it.third })
        .take(limit)
        .map { (r, _, _) -> r.toFetched(english) }
}

private fun ingredientMatches(foodName: String, key: String): Boolean {
    val names = listOf(foodName) + (FOOD_DB_BY_NAME[foodName.lowercase()]?.aliases ?: emptyList())
    return names.any { n -> val nn = norm(n); wordBoundaryMatch(nn, key) || wordBoundaryMatch(key, nn) }
}

private fun OfficialRecipe.toFetched(english: Boolean) = FetchedRecipeResult(
    name = if (english) nameEn else nameFr,
    servings = "1",
    ingredients = ingredients.map { "${it.grams.roundToInt()} g ${it.foodName}" },
    steps = emptyList(),
    cookTimeMinutes = null,
    kcal = totalKcal, proteinG = totalProteinG, fatG = totalFatG, carbsG = totalCarbsG,
    sourceUrl = "",
)
