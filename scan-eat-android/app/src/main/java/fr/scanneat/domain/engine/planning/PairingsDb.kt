package fr.scanneat.domain.engine.planning

import java.text.Normalizer

// ============================================================================
// PAIRINGS DATABASE — port of public/data/pairings.js
//
// SOURCE: Ahn, Ahnert, Bagrow, Barabási — Sci. Rep. 1:196 (2011)
//   doi:10.1038/srep00196 — 56 498 published recipes, 11 cuisines
// SCORING: PPMI(a,b) × sqrt(count(a,b)), min co-occurrence 5 recipes
// ============================================================================

data class PairingEntry(
    val b: String,         // partner ingredient key (EN)
    val fr: String?,       // French display name
    val cooccur: Int,
)

data class PairingsResult(
    val en: String,
    val nameFr: String,
    val recipeCount: Int,
    val pairs: List<PairingEntry>,
)

private val EN_TO_FR: Map<String, String> = mapOf(
    "apple" to "pomme",
    "pear" to "poire",
    "banana" to "banane",
    "strawberry" to "fraise",
    "raspberry" to "framboise",
    "blueberry" to "myrtille",
    "blackberry" to "mûre",
    "cherry" to "cerise",
    "peach" to "pêche",
    "apricot" to "abricot",
    "fig" to "figue",
    "grape" to "raisin",
    "pineapple" to "ananas",
    "mango" to "mangue",
    "avocado" to "avocat",
    "lemon" to "citron",
    "lime" to "citron vert",
    "orange" to "orange",
    "mandarin" to "mandarine",
    "grapefruit" to "pamplemousse",
    "melon" to "melon",
    "watermelon" to "pastèque",
    "coconut" to "noix de coco",
    "tomato" to "tomate",
    "carrot" to "carotte",
    "onion" to "oignon",
    "scallion" to "ciboule",
    "garlic" to "ail",
    "shallot" to "échalote",
    "leek" to "poireau",
    "potato" to "pomme de terre",
    "sweet_potato" to "patate douce",
    "cucumber" to "concombre",
    "zucchini" to "courgette",
    "eggplant" to "aubergine",
    "bell_pepper" to "poivron",
    "cabbage" to "chou",
    "broccoli" to "brocoli",
    "cauliflower" to "chou-fleur",
    "spinach" to "épinard",
    "lettuce" to "salade verte",
    "celery" to "céleri",
    "mushroom" to "champignon",
    "asparagus" to "asperge",
    "fennel" to "fenouil",
    "pumpkin" to "potiron",
    "beet" to "betterave",
    "artichoke" to "artichaut",
    "pea" to "petit pois",
    "basil" to "basilic",
    "parsley" to "persil",
    "mint" to "menthe",
    "thyme" to "thym",
    "rosemary" to "romarin",
    "sage" to "sauge",
    "oregano" to "origan",
    "dill" to "aneth",
    "tarragon" to "estragon",
    "chive" to "ciboulette",
    "bay_leaf" to "laurier",
    "cilantro" to "coriandre",
    "cinnamon" to "cannelle",
    "clove" to "girofle",
    "ginger" to "gingembre",
    "nutmeg" to "muscade",
    "pepper" to "poivre",
    "black_pepper" to "poivre noir",
    "white_pepper" to "poivre blanc",
    "vanilla" to "vanille",
    "saffron" to "safran",
    "cumin" to "cumin",
    "paprika" to "paprika",
    "turmeric" to "curcuma",
    "cardamom" to "cardamome",
    "star_anise" to "anis étoilé",
    "anise" to "anis",
    "mustard" to "moutarde",
    "cayenne" to "cayenne",
    "chili_pepper" to "piment",
    "beef" to "boeuf",
    "pork" to "porc",
    "lamb" to "agneau",
    "chicken" to "poulet",
    "duck" to "canard",
    "turkey" to "dinde",
    "egg" to "œuf",
    "salmon" to "saumon",
    "smoked_salmon" to "saumon fumé",
    "tuna" to "thon",
    "shrimp" to "crevette",
    "crab" to "crabe",
    "lobster" to "homard",
    "scallop" to "coquille Saint-Jacques",
    "anchovy" to "anchois",
    "rice" to "riz",
    "wheat" to "blé",
    "oat" to "avoine",
    "corn" to "maïs",
    "barley" to "orge",
    "buckwheat" to "sarrasin",
    "rye" to "seigle",
    "lentil" to "lentille",
    "chickpea" to "pois chiche",
    "soybean" to "soja",
    "kidney_bean" to "haricot rouge",
    "almond" to "amandes",
    "walnut" to "noix",
    "hazelnut" to "noisette",
    "pistachio" to "pistache",
    "pecan" to "noix de pécan",
    "cashew" to "noix de cajou",
    "peanut" to "cacahuète",
    "sesame_seed" to "graines de sésame",
    "milk" to "lait",
    "cream" to "crème",
    "butter" to "beurre",
    "yogurt" to "yaourt",
    "cheese" to "fromage",
    "mozzarella_cheese" to "mozzarella",
    "parmesan_cheese" to "parmesan",
    "cheddar_cheese" to "cheddar",
    "camembert_cheese" to "camembert",
    "gruyere_cheese" to "gruyère",
    "emmental_cheese" to "emmental",
    "feta_cheese" to "feta",
    "goat_cheese" to "chèvre",
    "blue_cheese" to "fromage bleu",
    "roquefort_cheese" to "roquefort",
    "cottage_cheese" to "fromage blanc",
    "cream_cheese" to "cream cheese",
    "ricotta_cheese" to "ricotta",
    "honey" to "miel",
    "cocoa" to "cacao",
    "caramel" to "caramel",
    "maple_syrup" to "sirop d'érable",
    "cranberry" to "canneberge",
    "olive_oil" to "huile d'olive",
    "vegetable_oil" to "huile végétale",
    "vinegar" to "vinaigre",
    "balsamic_vinegar" to "vinaigre balsamique",
    "soy_sauce" to "sauce soja",
    "fish_sauce" to "sauce poisson",
    "coffee" to "café",
    "black_tea" to "thé noir",
    "green_tea" to "thé vert",
    "white_wine" to "vin blanc",
    "red_wine" to "vin rouge",
    "rum" to "rhum",
    "beer" to "bière",
    "olive" to "olive",
    "caper" to "câpre",
    "seaweed" to "algue",
    "tamarind" to "tamarin",
    "lemon_juice" to "jus de citron",
    "lime_juice" to "jus de citron vert",
    "orange_juice" to "jus d'orange",
    "peanut_butter" to "beurre de cacahuète",
    "chicken_broth" to "bouillon de poulet",
    "beef_broth" to "bouillon de bœuf",
    "vegetable_broth" to "bouillon de légumes",
    "macaroni" to "macaronis",
    "yeast" to "levure",
    "lard" to "saindoux",
    "sour_cream" to "crème fraîche",
    "buttermilk" to "babeurre",
    "bread" to "pain",
    "flour" to "farine",
    "sugar" to "sucre",
    "salt" to "sel",
    "raisin" to "raisin sec",
    "date" to "datte",
    "prune" to "pruneau",
    "fish" to "poisson",
    "green_bell_pepper" to "poivron vert",
    "red_bell_pepper" to "poivron rouge",
    "chinese_cabbage" to "chou chinois",
    "radish" to "radis",
    "kelp" to "varech",
    "lemongrass" to "citronnelle",
    "celery_oil" to "huile de céleri",
    "roasted_beef" to "boeuf rôti",
    "bacon" to "bacon"
)

// FR → EN reverse map (built from EN_TO_FR + ingredient display names)
private val FR_TO_EN: Map<String, String> by lazy {
    val m = mutableMapOf<String, String>()
    for ((en, fr) in EN_TO_FR) {
        m[fr.lowercase()] = en          // French display name → EN key
        m[en.replace("_", " ")] = en   // "smoked salmon" → "smoked_salmon"
        m[en] = en                      // exact EN key lookup
    }
    // Also index all ingredient EN keys directly from PAIRINGS
    for (key in PAIRINGS.keys) {
        m[key.replace("_", " ")] = key
        m[key] = key
    }
    m
}


private val PAIRINGS: Map<String, PairingsResult> =
    PAIRINGS_A_C + PAIRINGS_C_L + PAIRINGS_L_R + PAIRINGS_R_Z

private fun normalizePairing(s: String): String =
    Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace("_", " ")

/**
 * Resolve a French or English ingredient name to its canonical EN key.
 * Tries largest n-gram window first: "saumon fumé" → smoked_salmon.
 * Port of resolveIngredient() from pairings.js.
 */
fun resolveIngredient(name: String): String? {
    val q = normalizePairing(name)
    if (q.length < 2) return null
    FR_TO_EN[q]?.let { return it }
    val tokens = q.split(Regex("\\s+")).filter { it.length >= 2 }
    // A branded/multi-word product name (e.g. "Coca-Cola Vanille") can contain an
    // incidental flavor word ("vanille") that happens to key an ingredient, even
    // though the product itself isn't that ingredient at all. Requiring the
    // matched span to cover at least half the meaningful tokens keeps genuine
    // near-synonyms ("boeuf haché" -> boeuf) while rejecting a single stray word
    // buried in an otherwise unrelated name.
    val minSize = ((tokens.size + 1) / 2).coerceAtLeast(1)
    for (size in tokens.size downTo minSize) {
        for (start in 0..(tokens.size - size)) {
            val candidate = tokens.subList(start, start + size).joinToString(" ")
            FR_TO_EN[candidate]?.let { return it }
        }
    }
    return null
}

/**
 * Return French display names of ingredients that pair well with [name].
 * Port of findPairings() from pairings.js.
 */
fun findPairings(name: String, limit: Int = 6): List<String> {
    val en = resolveIngredient(name) ?: return emptyList()
    val entry = PAIRINGS[en] ?: return emptyList()
    // Sort by co-occurrence count descending before truncating - PAIRINGS entries
    // are stored in whatever order the source dataset happened to list them (see
    // e.g. "beef": onion 3315, tomato 2107, beef_broth 410, garlic 2817, ...),
    // not pre-sorted by strength. Without this, take(limit) returned an arbitrary
    // subset of a recipe's pairings rather than its `limit` *strongest* ones,
    // silently defeating the "min co-occurrence 5 recipes" scoring this file's
    // header comment describes and showing weaker suggestions than a lower-ranked
    // pairing that got cut off just because it was listed first.
    return entry.pairs.sortedByDescending { it.cooccur }.take(limit).map { it.fr ?: it.b.replace("_", " ") }
}

/**
 * Full pairing result including scores. Returns null on miss.
 * Port of matchPairings() from pairings.js.
 */
fun matchPairings(name: String): PairingsResult? {
    val en = resolveIngredient(name) ?: return null
    return PAIRINGS[en]
}