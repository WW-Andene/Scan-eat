package fr.scanneat.presentation.foodsearch

/**
 * One nutrient's alias words (French/English) plus its accessor on
 * [FoodSearchItem] and, when meaningful, a "high X" default threshold used
 * when the nutrient is typed bare with no comparison ("protéine" alone means
 * "high protein", same as tapping the HIGH_PROTEIN chip). Nutrients with no
 * sensible bare-word meaning (kcal, carbs, vitamins) require an explicit
 * operator+number instead (see [NUTRIENT_QUERY_REGEX]).
 */
private class NutrientAlias(val getter: (FoodSearchItem) -> Double, val highThreshold: Double?)

private val NUTRIENT_ALIASES: Map<String, NutrientAlias> = buildMap {
    fun reg(names: List<String>, highThreshold: Double?, getter: (FoodSearchItem) -> Double) {
        val alias = NutrientAlias(getter, highThreshold)
        names.forEach { put(it, alias) }
    }
    reg(listOf("kcal", "calorie", "calories", "energie", "énergie"), null) { it.kcal }
    reg(listOf("protein", "proteine", "proteines", "protéine", "protéines"), 15.0) { it.proteinG }
    reg(listOf("carb", "carbs", "glucide", "glucides"), null) { it.carbsG }
    reg(listOf("fat", "gras", "lipide", "lipides", "matieres grasses", "matières grasses"), 17.5) { it.fatG }
    reg(listOf("fiber", "fibre", "fibres"), 3.0) { it.fiberG }
    reg(listOf("salt", "sel", "sodium"), 1.5) { it.saltG }
    reg(listOf("iron", "fer"), 2.0) { it.ironMg }
    reg(listOf("calcium"), 100.0) { it.calciumMg }
    reg(listOf("vitamine d", "vitamined", "vitd"), null) { it.vitDUg }
    reg(listOf("b12", "vitamine b12", "vitaminb12"), null) { it.b12Ug }
}

// Recognizes "<nutrient> <operator> <number>", e.g. "sucre<5", "sodium >= 200",
// "protéine > 20" - a real threshold query over ANY nutrient FoodSearchItem
// exposes, not just the 5 fixed filter chips above. Falls back, for a bare
// nutrient word with no operator, to that nutrient's own "high X" default
// (matching the old keyword-only behavior for fiber/protein/iron/calcium, now
// extended to fat/salt too).
private val NUTRIENT_QUERY_REGEX =
    Regex("""^\s*([a-zàâäéèêëïîôöùûüç ]+?)\s*(<=|>=|<|>)\s*(\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)

internal fun predicateFor(query: String): ((FoodSearchItem) -> Boolean)? {
    val trimmed = query.trim().lowercase()
    NUTRIENT_QUERY_REGEX.matchEntire(trimmed)?.let { m ->
        val (rawName, op, rawNum) = m.destructured
        val alias = NUTRIENT_ALIASES[rawName.trim()] ?: return null
        val num = rawNum.replace(',', '.').toDoubleOrNull() ?: return null
        return { item: FoodSearchItem ->
            val v = alias.getter(item)
            when (op) { "<" -> v < num; "<=" -> v <= num; ">" -> v > num; else -> v >= num }
        }
    }
    val alias = NUTRIENT_ALIASES[trimmed] ?: return null
    val threshold = alias.highThreshold ?: return null
    return { item: FoodSearchItem -> alias.getter(item) >= threshold }
}
