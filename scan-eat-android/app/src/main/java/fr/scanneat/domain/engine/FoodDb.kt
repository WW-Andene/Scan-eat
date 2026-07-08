package fr.scanneat.domain.engine

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
//   NOT a bit-for-bit export. Accurate to ±10 % for these ~54 foods.
//   Do not use for clinical or research work without verifying against
//   the canonical ANSES XML distribution.
//
// Used for: Quick Add autocomplete, LLM-identify reconciliation.
// ============================================================================

data class FoodEntry(
    val name: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double = 0.0,
    val saltG: Double = 0.0,
    val aliases: List<String> = emptyList(),
)

val FOOD_DB: List<FoodEntry> = listOf(
    // Fruits
    FoodEntry("pomme",        54.0,  0.3,  12.0,  0.2,  2.4,  aliases = listOf("apple")),
    FoodEntry("banane",       90.0,  1.1,  20.0,  0.3,  2.6,  aliases = listOf("banana")),
    FoodEntry("orange",       45.0,  0.9,   9.0,  0.2,  2.2),
    FoodEntry("fraise",       33.0,  0.7,   5.0,  0.3,  2.0,  aliases = listOf("fraises", "strawberry")),
    FoodEntry("myrtille",     57.0,  0.7,  10.0,  0.3,  2.4,  aliases = listOf("myrtilles", "blueberry")),
    FoodEntry("avocat",      160.0,  2.0,   2.0, 15.0,  6.7,  aliases = listOf("avocado")),
    FoodEntry("kiwi",         61.0,  1.1,  11.0,  0.5,  3.0),
    FoodEntry("raisin",       69.0,  0.7,  16.0,  0.2,  0.9,  aliases = listOf("raisins", "grape")),

    // Légumes
    FoodEntry("tomate",       18.0,  0.9,   3.0,  0.2,  1.2,  aliases = listOf("tomate cerise", "tomato")),
    FoodEntry("carotte",      36.0,  0.6,   7.0,  0.2,  2.8,  aliases = listOf("carrot")),
    FoodEntry("brocoli",      30.0,  2.8,   2.0,  0.4,  2.6,  aliases = listOf("broccoli")),
    FoodEntry("épinard",      23.0,  2.9,   1.0,  0.4,  2.2,  aliases = listOf("épinards", "spinach")),
    FoodEntry("concombre",    12.0,  0.6,   2.0,  0.1,  0.5,  aliases = listOf("cucumber")),
    FoodEntry("courgette",    15.0,  1.3,   2.0,  0.1,  1.1,  aliases = listOf("zucchini")),
    FoodEntry("poivron",      27.0,  0.9,   5.0,  0.2,  1.9,  aliases = listOf("pepper")),
    FoodEntry("oignon",       34.0,  1.2,   6.0,  0.1,  1.7,  aliases = listOf("onion")),
    FoodEntry("salade verte", 15.0,  1.3,   1.5,  0.2,  1.3,  aliases = listOf("salade", "laitue", "lettuce")),
    FoodEntry("pomme de terre", 80.0, 2.0, 17.0,  0.1,  1.8,  aliases = listOf("patate", "potato")),

    // Céréales / féculents
    FoodEntry("riz blanc cuit",  130.0, 2.7, 28.0, 0.3, 0.4, aliases = listOf("riz cuit", "white rice")),
    FoodEntry("pâtes cuites",    140.0, 5.0, 28.0, 1.0, 1.8, aliases = listOf("pates", "pasta")),
    FoodEntry("pain blanc",      260.0, 8.0, 50.0, 2.5, 2.7, aliases = listOf("pain", "bread")),
    FoodEntry("pain complet",    240.0, 9.0, 45.0, 3.0, 6.5, aliases = listOf("whole wheat bread")),
    FoodEntry("baguette",        265.0, 8.0, 55.0, 1.0, 2.3),
    FoodEntry("croissant",       406.0, 8.0, 45.0, 21.0, 1.6),
    FoodEntry("avoine",          389.0, 17.0, 66.0, 7.0, 10.6, aliases = listOf("flocons d'avoine", "oats")),
    FoodEntry("quinoa cuit",     120.0, 4.4, 22.0, 1.9, 2.8),

    // Protéines animales
    FoodEntry("poulet rôti",    215.0, 30.0,  0.0, 10.0, 0.0, saltG = 0.2),
    FoodEntry("boeuf haché 5%", 130.0, 22.0,  0.0,  5.0, 0.0, saltG = 0.1, aliases = listOf("steak haché 5%")),
    FoodEntry("boeuf haché 15%",215.0, 20.0,  0.0, 15.0, 0.0, saltG = 0.1),
    FoodEntry("saumon",         208.0, 20.0,  0.0, 13.0, 0.0, aliases = listOf("salmon")),
    FoodEntry("thon",           130.0, 29.0,  0.0,  1.0, 0.0, aliases = listOf("tuna")),
    FoodEntry("oeuf",           155.0, 13.0,  1.1, 11.0, 0.0, aliases = listOf("œuf", "egg")),
    FoodEntry("jambon blanc",   115.0, 20.0,  1.0,  4.0, 0.0, saltG = 1.6, aliases = listOf("ham")),

    // Produits laitiers
    FoodEntry("lait demi-écrémé",  46.0,  3.2,  4.7,  1.6, 0.0, aliases = listOf("lait", "milk")),
    FoodEntry("yaourt nature",      60.0,  3.5,  4.7,  3.0, 0.0, aliases = listOf("yaourt", "yogurt")),
    FoodEntry("skyr",               60.0, 10.0,  4.0,  0.2, 0.0),
    FoodEntry("fromage blanc 0%",   45.0,  7.5,  4.0,  0.1, 0.0, aliases = listOf("fromage blanc")),
    FoodEntry("emmental",          380.0, 29.0,  0.0, 30.0, 0.0, saltG = 0.8, aliases = listOf("gruyère")),
    FoodEntry("camembert",         300.0, 20.0,  0.5, 24.0, 0.0, saltG = 1.4),

    // Légumineuses / oléagineux
    FoodEntry("lentille cuite",   115.0,  9.0, 20.0,  0.4, 3.8, aliases = listOf("lentilles", "lentils")),
    FoodEntry("pois chiche cuit", 165.0,  9.0, 27.0,  2.6, 4.5, aliases = listOf("pois chiches", "chickpea")),
    FoodEntry("amandes",          620.0, 21.0, 20.0, 51.0, 12.5, aliases = listOf("amande", "almonds")),
    FoodEntry("noix",             655.0, 15.0, 14.0, 65.0,  6.7),

    // Matières grasses
    FoodEntry("huile d'olive",  900.0, 0.0, 0.0, 100.0, 0.0, aliases = listOf("olive oil")),
    FoodEntry("beurre",         745.0, 0.7, 0.7,  82.0, 0.0, aliases = listOf("butter")),

    // Sucreries / snacks
    FoodEntry("chocolat noir 70%", 580.0, 8.0, 46.0, 40.0, 10.9, aliases = listOf("chocolat", "dark chocolate")),
    FoodEntry("chocolat au lait",  540.0, 7.0, 58.0, 30.0,  1.5),
    FoodEntry("biscuit",           480.0, 6.0, 65.0, 21.0,  2.0),
    FoodEntry("miel",              304.0, 0.3, 82.0,  0.0,  0.2, aliases = listOf("honey")),

    // Boissons
    FoodEntry("café noir",      2.0,  0.3,  0.0,  0.0, 0.0, aliases = listOf("coffee", "café")),
    FoodEntry("thé",            1.0,  0.0,  0.0,  0.0, 0.0, aliases = listOf("tea")),
    FoodEntry("jus d'orange",  45.0,  0.7, 10.0,  0.2, 0.2),
    FoodEntry("coca-cola",     42.0,  0.0, 10.6,  0.0, 0.0),
    FoodEntry("bière",         43.0,  0.4,  3.6,  0.0, 0.0),
    FoodEntry("vin rouge",     83.0,  0.0,  2.6,  0.0, 0.0),
)

// ============================================================================
// Search
// ============================================================================

private fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")

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
    fun consider(list: List<FoodEntry>, custom: Boolean) {
        for (f in list) {
            val haystack = listOf(f.name) + f.aliases
            val normHay = haystack.map { normalize(it) }
            val prefixIdx = normHay.indexOfFirst { it.startsWith(q) }
            val score = when {
                prefixIdx >= 0 -> if (custom) -0.5 else 0.0
                normHay.any { it.contains(q) } -> if (custom) 0.5 else 1.0
                else -> continue
            }
            matches += Ranked(f, score)
        }
    }
    consider(extraFoods, true)
    consider(FOOD_DB, false)
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
    ),
    weightG = portionG,
)
