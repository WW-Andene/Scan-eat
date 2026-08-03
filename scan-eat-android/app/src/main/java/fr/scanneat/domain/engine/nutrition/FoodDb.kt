package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.NovaClass
import java.text.Normalizer

// ============================================================================
// FOOD DATABASE — port of public/data/food-db.js
//
// ⚠️  PROVENANCE NOTICE:
//   Values are hand-transcribed approximations of CIQUAL 2020
//   (ANSES, https://ciqual.anses.fr/, DOI 10.5281/zenodo.4770600).
//   NOT a bit-for-bit export. Accurate to ±10 % for these ~230 foods
//   (expanded 2026-07-17 from the original ~54, then again 2026-08-03 from
//   ~146 to ~230 - Quick Add search and LLM reconciliation previously
//   missed most common everyday foods outside the smaller sets, e.g.
//   peach/cauliflower/turkey/tofu/hazelnut/hummus in the first expansion,
//   then passion fruit/fennel/sourdough bread/rabbit/pecan/raclette/
//   maple syrup/ratatouille/kebab and more in the second - falling back to
//   the LLM's own rough macro guess instead of a grounded CIQUAL-style
//   value for anything not in it).
//   Do not use for clinical or research work without verifying against
//   the canonical ANSES XML distribution.
//
// Used for: Quick Add autocomplete, LLM-identify reconciliation.
//
// Data split across sibling files in this package (model + per-category
// entries); this file assembles FOOD_DB and holds search/reconciliation:
//   FoodEntry.kt                       - the FoodEntry data class
//   FoodDbFruitsAndVegetables.kt       - fruits + vegetables
//   FoodDbGrainsAndProteins.kt         - grains/starches + animal proteins
//   FoodDbDairyAndLegumes.kt           - dairy + legumes/nuts
//   FoodDbFatsSweetsAndMeals.kt        - fats, sweets/snacks, beverages, meals
// ============================================================================

val FOOD_DB: List<FoodEntry> =
    FOOD_DB_FRUITS_AND_VEGETABLES +
        FOOD_DB_GRAINS_AND_PROTEINS +
        FOOD_DB_DAIRY_AND_LEGUMES +
        FOOD_DB_FATS_SWEETS_AND_MEALS

// ============================================================================
// Search
// ============================================================================

// Was compiled fresh on every normalize() call — searchFoodDB() calls
// normalize() once per name+alias of every FOOD_DB entry, on every single
// keystroke of a food search, so this pattern got recompiled on the order of
// (search-box keystrokes) x (FOOD_DB entries) x (aliases per entry) times.
// Compiling it once at class-init time removes that entirely.
private val DIACRITICS_RE = Regex("[\\u0300-\\u036f]")

private fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS_RE, "")

// FOOD_DB itself never changes at runtime, but searchFoodDB() previously
// re-normalized every entry's name + all its aliases from scratch on every
// call — full Normalizer.normalize() + regex pass per string, per keystroke,
// for a list that's identical every time. Precomputing it once here turns a
// repeated-every-search cost into a one-time cost at class-init, the same
// strategy AdditivesDb.kt's NORMALIZED_ADDITIVES already uses for its
// synonym table. extraFoods (the user's custom foods) is NOT precomputed
// here since it's caller-supplied and can change between calls.
private val NORMALIZED_FOOD_DB: List<Pair<FoodEntry, List<String>>> =
    FOOD_DB.map { f -> f to (listOf(f.name) + f.aliases).map(::normalize) }

/**
 * Find up to [limit] foods whose name or alias starts with / contains [query].
 * Case- and accent-insensitive. Custom foods from [extraFoods] win ties
 * (same ranking as the original JS implementation).
 *
 * Port of searchFoodDB() from food-db.js.
 */
fun searchFoodDB(
    query: String,
    limit: Int = 6,
    extraFoods: List<FoodEntry> = emptyList(),
): List<FoodEntry> {
    val q = normalize(query.trim())
    if (q.length < 2) return emptyList()

    data class Ranked(val food: FoodEntry, val score: Double)

    val matches = mutableListOf<Ranked>()

    fun scoreOf(normHay: List<String>, custom: Boolean): Double? {
        val prefixIdx = normHay.indexOfFirst { it.startsWith(q) }
        return when {
            prefixIdx >= 0 -> if (custom) -0.5 else 0.0
            normHay.any { it.contains(q) } -> if (custom) 0.5 else 1.0
            else -> null
        }
    }

    // Custom foods are caller-supplied and can change between calls, so they're
    // normalized live; FOOD_DB below reuses the precomputed table instead.
    for (f in extraFoods) {
        val normHay = (listOf(f.name) + f.aliases).map(::normalize)
        scoreOf(normHay, custom = true)?.let { matches += Ranked(f, it) }
    }
    for ((f, normHay) in NORMALIZED_FOOD_DB) {
        scoreOf(normHay, custom = false)?.let { matches += Ranked(f, it) }
    }

    matches.sortWith(compareBy({ it.score }, { it.food.name }))
    return matches.take(limit).map { it.food }
}

// ============================================================================
// LLM reconciliation
// ============================================================================

data class ReconcileResult(
    val name: String,
    val estimatedGrams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String,            // "db" | "llm"
    val matchedName: String? = null,
)

/**
 * Match an LLM-identified food against the DB. On hit, replace per-100g
 * macros with CIQUAL values while keeping the LLM's gram estimate.
 * Port of reconcileWithFoodDB() from food-db.js.
 */
fun reconcileWithFoodDB(
    name: String,
    estimatedGrams: Double,
    llmKcal: Double,
    llmProteinG: Double,
    llmCarbsG: Double,
    llmFatG: Double,
    extraFoods: List<FoodEntry> = emptyList(),
): ReconcileResult {
    if (estimatedGrams <= 0) {
        return ReconcileResult(name, estimatedGrams, llmKcal, llmProteinG, llmCarbsG, llmFatG, "llm")
    }

    fun tryMatch(q: String): FoodEntry? = searchFoodDB(q, 1, extraFoods).firstOrNull()

    var match = tryMatch(name)
    if (match == null) {
        val firstToken = name.trim().split(Regex("\\s+")).firstOrNull() ?: ""
        if (firstToken.length >= 2 && firstToken != name) match = tryMatch(firstToken)
    }

    if (match == null) {
        return ReconcileResult(name, estimatedGrams, llmKcal, llmProteinG, llmCarbsG, llmFatG, "llm")
    }

    val f = estimatedGrams / 100.0
    return ReconcileResult(
        name           = match.name,
        estimatedGrams = estimatedGrams,
        kcal           = (match.kcal  * f * 10).toLong() / 10.0,
        proteinG       = (match.proteinG * f * 10).toLong() / 10.0,
        carbsG         = (match.carbsG  * f * 10).toLong() / 10.0,
        fatG           = (match.fatG   * f * 10).toLong() / 10.0,
        source         = "db",
        matchedName    = match.name,
    )
}

/** Convert a FoodEntry + portion to a domain Product (for scoring quick-add foods). */
fun FoodEntry.toProduct(portionG: Double = 100.0): Product = Product(
    name        = name,
    category    = ProductCategory.OTHER,
    novaClass   = NovaClass.UNPROCESSED,
    ingredients = emptyList(),
    nutrition   = NutritionPer100g(
        energyKcal    = kcal,
        fatG          = fatG,
        saturatedFatG = 0.0,
        carbsG        = carbsG,
        sugarsG       = 0.0,
        fiberG        = fiberG,
        proteinG      = proteinG,
        saltG         = saltG,
        ironMg        = ironMg,
        calciumMg     = calciumMg,
        vitDUg        = vitDUg,
        b12Ug         = b12Ug,
    ),
    weightG = portionG,
)
