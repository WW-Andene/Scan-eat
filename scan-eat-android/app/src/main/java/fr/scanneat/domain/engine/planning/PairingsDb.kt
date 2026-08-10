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
    "bacon" to "bacon",
    // User-reported: French-language pairing chips still showed raw English
    // words - these 79 partner-ingredient keys (PairingsData*.kt) were never
    // given a per-entry French name (PairingEntry.fr = null everywhere they
    // occur), so findPairings() fell straight to the English key with
    // underscores replaced by spaces regardless of app language. Added here
    // as a shared fallback instead of hand-editing every PairingEntry(...)
    // occurrence across the four PairingsData*.kt files.
    "bay" to "laurier",
    "bean" to "haricot",
    "berry" to "baie",
    "bitter_orange" to "orange amère",
    "black_bean" to "haricot noir",
    "brandy" to "eau-de-vie",
    "brown_rice" to "riz complet",
    "cane_molasses" to "mélasse de canne",
    "cereal" to "céréale",
    "cherry_brandy" to "eau-de-vie de cerise",
    "chervil" to "cerfeuil",
    "chicory" to "chicorée",
    "cider" to "cidre",
    "clam" to "palourde",
    "coriander" to "coriandre",
    "cured_pork" to "porc salé",
    "currant" to "groseille",
    "egg_noodle" to "nouille aux œufs",
    "enokidake" to "champignon enoki",
    "fenugreek" to "fenugrec",
    "galanga" to "galanga",
    "gelatin" to "gélatine",
    "gin" to "gin",
    "grape_juice" to "jus de raisin",
    "ham" to "jambon",
    "horseradish" to "raifort",
    "katsuobushi" to "bonite séchée",
    "kiwi" to "kiwi",
    "lavender" to "lavande",
    "lemon_peel" to "zeste de citron",
    "lima_bean" to "haricot de Lima",
    "lime_peel_oil" to "huile de zeste de citron vert",
    "lovage" to "livèche",
    "malt" to "malt",
    "mandarin_peel" to "zeste de mandarine",
    "marjoram" to "marjolaine",
    "meat" to "viande",
    "milk_fat" to "matière grasse laitière",
    "mussel" to "moule",
    "nectarine" to "nectarine",
    "nut" to "noix",
    "orange_peel" to "zeste d'orange",
    "ouzo" to "ouzo",
    "papaya" to "papaye",
    "parsnip" to "panais",
    "pear_brandy" to "eau-de-vie de poire",
    "pimento" to "piment doux",
    "plum" to "prune",
    "popcorn" to "pop-corn",
    "pork_sausage" to "saucisse de porc",
    "provolone_cheese" to "provolone",
    "rhubarb" to "rhubarbe",
    "roasted_peanut" to "cacahuète grillée",
    "roasted_sesame_seed" to "graine de sésame grillée",
    "rose" to "rose",
    "rye_flour" to "farine de seigle",
    "sake" to "saké",
    "salmon_roe" to "œufs de saumon",
    "savory" to "sarriette",
    "seed" to "graine",
    "sesame_oil" to "huile de sésame",
    "sherry" to "xérès",
    "shiitake" to "shiitake",
    "smoke" to "fumé",
    "squash" to "courge",
    "squid" to "calamar",
    "swiss_cheese" to "emmental suisse",
    "tabasco_pepper" to "piment tabasco",
    "tangerine" to "mandarine",
    "tea" to "thé",
    "tequila" to "tequila",
    "thai_pepper" to "piment thaï",
    "turnip" to "navet",
    "vegetable" to "légume",
    "wasabi" to "wasabi",
    "whiskey" to "whisky",
    "whole_grain_wheat_flour" to "farine de blé complet",
    "wine" to "vin",
    "yam" to "igname"
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
 *
 * [exclude] — user-reported: "pairs well with" suggested "poulet" for a
 * recipe whose main/largest ingredient already *is* poulet (riz's own
 * pairing entry lists chicken as a strong co-occurrence partner, correctly
 * on its own, but every caller here queries pairings for one ingredient of
 * a dish that already has several - nothing filtered out the dish's own
 * other ingredients from showing up as if they were a new suggestion).
 * Every name in [exclude] (normalized the same way [name] itself is
 * resolved) is dropped from the result, including [name] itself in case a
 * dataset entry ever lists an ingredient among its own pairings.
 *
 * User-reported (culinary-logic pass): raw co-occurrence data is real but
 * category-blind - "riz" (rice) correctly co-occurs with "macaronis"
 * (pasta) often enough in the source dataset, since plenty of composite
 * dishes use both, but suggesting a second starch for a dish that already
 * has one is bad plate-balance advice (same for suggesting a dairy fat like
 * butter when yaourt is already in the dish). [exclude]'s ingredients are
 * classified into rough food groups (classifyFoodGroup - USDA MyPlate/PNNS
 * basis, see CulinaryFoodGroups.kt) and every group already represented is
 * deprioritized, not hard-removed: a same-group suggestion can still win on
 * a strong enough co-occurrence lead, it's just no longer preferred purely
 * for being the single highest raw count.
 */
// User-reported: pairing suggestions showed up in French regardless of the
// app's own language setting - every call site fed [name] through in
// whichever language it already had at hand, but the RESULT was always
// rendered via `it.fr ?: ...`, so an English-language user always saw French
// suggestion chips next to their own English-language screen. [preferFrench]
// lets a caller opt into the English fallback (`it.b`, underscore-replaced)
// instead; defaults to the previous always-French behavior so call sites
// that haven't been updated yet are unaffected.
fun findPairings(name: String, limit: Int = 6, exclude: Set<String> = emptySet(), preferFrench: Boolean = true): List<String> {
    val en = resolveIngredient(name) ?: return emptyList()
    val entry = PAIRINGS[en] ?: return emptyList()
    val excludedEn = (exclude + name).mapNotNullTo(mutableSetOf()) { resolveIngredient(it) }
    val dishGroups = excludedEn.mapTo(mutableSetOf()) { classifyFoodGroup(it) } - FoodGroup.OTHER
    // Sort by co-occurrence count descending - PAIRINGS entries are stored in
    // whatever order the source dataset happened to list them (see e.g.
    // "beef": onion 3315, tomato 2107, beef_broth 410, garlic 2817, ...), not
    // pre-sorted by strength.
    val candidates = entry.pairs
        .filter { it.b !in excludedEn }
        .sortedByDescending { it.cooccur }

    // User-reported (2nd pass): the previous version only *reordered* by food
    // group (sort-key demotion) rather than actually filtering - since most
    // PAIRINGS entries carry close to [limit] pairs total (rice/oat/macaroni
    // etc. all have exactly 8, matching every real call site's limit=8), every
    // candidate still got shown via take(limit) regardless of tier, so a
    // redundant same-food-group suggestion (another starch for a dish that
    // already has one) kept appearing exactly as before - the demotion never
    // had room to actually exclude anything. Hard-partitioning and preferring
    // the novel-group tier wholesale (falling back to same-group only when
    // there genuinely aren't enough novel-group candidates to fill [limit])
    // makes the plate-balance intent this file's own header comment describes
    // actually take effect instead of being a no-op in the common case.
    val (novelGroup, sameGroup) = candidates.partition { classifyFoodGroup(it.b) !in dishGroups }
    val result = if (novelGroup.size >= limit) novelGroup else novelGroup + sameGroup

    return result
        .take(limit)
        .map { if (preferFrench) (it.fr ?: EN_TO_FR[it.b] ?: it.b.replace("_", " ")) else it.b.replace("_", " ") }
}

/**
 * Full pairing result including scores. Returns null on miss.
 * Port of matchPairings() from pairings.js.
 */
fun matchPairings(name: String): PairingsResult? {
    val en = resolveIngredient(name) ?: return null
    return PAIRINGS[en]
}