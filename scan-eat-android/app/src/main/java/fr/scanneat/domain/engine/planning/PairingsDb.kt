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

private val PAIRINGS: Map<String, PairingsResult> = mapOf(
    "almond" to PairingsResult("almond", "amandes", 2318, listOf(
        PairingEntry("cherry", "cerise", 178),
        PairingEntry("vanilla", "vanille", 712),
        PairingEntry("wheat", "blé", 1353),
        PairingEntry("raisin", "raisin sec", 226),
        PairingEntry("egg", "œuf", 1322),
        PairingEntry("butter", "beurre", 1288),
        PairingEntry("apricot", "abricot", 103),
        PairingEntry("cocoa", "cacao", 393)
    )),
    "anise" to PairingsResult("anise", "anis", 223, listOf(
        PairingEntry("nut", null, 68),
        PairingEntry("seed", null, 56),
        PairingEntry("ginger", "gingembre", 75),
        PairingEntry("fennel", "fenouil", 32),
        PairingEntry("ouzo", null, 5),
        PairingEntry("wheat", "blé", 153),
        PairingEntry("pumpkin", "potiron", 17),
        PairingEntry("egg", "œuf", 143)
    )),
    "apple" to PairingsResult("apple", "pomme", 2414, listOf(
        PairingEntry("cinnamon", "cannelle", 1051),
        PairingEntry("raisin", "raisin sec", 381),
        PairingEntry("nutmeg", "muscade", 364),
        PairingEntry("walnut", "noix", 365),
        PairingEntry("cranberry", "canneberge", 177),
        PairingEntry("grape", "raisin", 95),
        PairingEntry("cider", null, 178),
        PairingEntry("cane_molasses", null, 636)
    )),
    "apricot" to PairingsResult("apricot", "abricot", 620, listOf(
        PairingEntry("plum", null, 39),
        PairingEntry("almond", "amandes", 103),
        PairingEntry("fig", "figue", 18),
        PairingEntry("raisin", "raisin sec", 70),
        PairingEntry("brandy", null, 28),
        PairingEntry("orange_juice", "jus d'orange", 58),
        PairingEntry("cranberry", "canneberge", 37),
        PairingEntry("date", "datte", 22)
    )),
    "artichoke" to PairingsResult("artichoke", "artichaut", 391, listOf(
        PairingEntry("parmesan_cheese", "parmesan", 128),
        PairingEntry("olive_oil", "huile d'olive", 214),
        PairingEntry("olive", "olive", 71),
        PairingEntry("lemon", "citron", 80),
        PairingEntry("parsley", "persil", 105),
        PairingEntry("garlic", "ail", 221),
        PairingEntry("feta_cheese", "feta", 26),
        PairingEntry("basil", "basilic", 75)
    )),
    "asparagus" to PairingsResult("asparagus", "asperge", 438, listOf(
        PairingEntry("parmesan_cheese", "parmesan", 102),
        PairingEntry("olive_oil", "huile d'olive", 163),
        PairingEntry("ham", null, 40),
        PairingEntry("macaroni", "macaronis", 68),
        PairingEntry("chicken_broth", "bouillon de poulet", 73),
        PairingEntry("leek", "poireau", 20),
        PairingEntry("tarragon", "estragon", 21),
        PairingEntry("pea", "petit pois", 32)
    )),
    "avocado" to PairingsResult("avocado", "avocat", 649, listOf(
        PairingEntry("cilantro", "coriandre", 228),
        PairingEntry("lime_juice", "jus de citron vert", 181),
        PairingEntry("lettuce", "salade verte", 115),
        PairingEntry("tomato", "tomate", 366),
        PairingEntry("cayenne", "cayenne", 313),
        PairingEntry("lime", "citron vert", 87),
        PairingEntry("corn", "maïs", 160),
        PairingEntry("black_bean", null, 42)
    )),
    "bacon" to PairingsResult("bacon", "bacon", 2154, listOf(
        PairingEntry("onion", "oignon", 1258),
        PairingEntry("potato", "pomme de terre", 383),
        PairingEntry("cheddar_cheese", "cheddar", 333),
        PairingEntry("bean", null, 211),
        PairingEntry("pepper", "poivre", 595),
        PairingEntry("smoke", null, 72),
        PairingEntry("vinegar", "vinaigre", 500),
        PairingEntry("cured_pork", null, 53)
    )),
    "banana" to PairingsResult("banana", "banane", 982, listOf(
        PairingEntry("pineapple", "ananas", 173),
        PairingEntry("strawberry", "fraise", 122),
        PairingEntry("vanilla", "vanille", 400),
        PairingEntry("walnut", "noix", 161),
        PairingEntry("yogurt", "yaourt", 85),
        PairingEntry("rum", "rhum", 60),
        PairingEntry("kiwi", null, 24),
        PairingEntry("coconut", "noix de coco", 103)
    )),
    "barley" to PairingsResult("barley", "orge", 232, listOf(
        PairingEntry("soybean", "soja", 65),
        PairingEntry("cereal", null, 22),
        PairingEntry("carrot", "carotte", 79),
        PairingEntry("rice", "riz", 76),
        PairingEntry("lentil", "lentille", 17),
        PairingEntry("celery", "céleri", 54),
        PairingEntry("beef_broth", "bouillon de bœuf", 20),
        PairingEntry("malt", null, 5)
    )),
    "basil" to PairingsResult("basil", "basilic", 3779, listOf(
        PairingEntry("oregano", "origan", 1350),
        PairingEntry("tomato", "tomate", 2273),
        PairingEntry("olive_oil", "huile d'olive", 2205),
        PairingEntry("mozzarella_cheese", "mozzarella", 647),
        PairingEntry("macaroni", "macaronis", 1017),
        PairingEntry("parmesan_cheese", "parmesan", 984),
        PairingEntry("rosemary", "romarin", 736),
        PairingEntry("garlic", "ail", 2685)
    )),
    "beef" to PairingsResult("beef", "boeuf", 4820, listOf(
        PairingEntry("onion", "oignon", 3315),
        PairingEntry("tomato", "tomate", 2107),
        PairingEntry("beef_broth", "bouillon de bœuf", 410),
        PairingEntry("garlic", "ail", 2817),
        PairingEntry("tamarind", "tamarin", 522),
        PairingEntry("black_pepper", "poivre noir", 1686),
        PairingEntry("celery_oil", "huile de céleri", 341),
        PairingEntry("soy_sauce", "sauce soja", 688)
    )),
    "beef_broth" to PairingsResult("beef_broth", "bouillon de bœuf", 835, listOf(
        PairingEntry("beef", "boeuf", 410),
        PairingEntry("carrot", "carotte", 236),
        PairingEntry("bay", null, 137),
        PairingEntry("red_wine", "vin rouge", 132),
        PairingEntry("onion", "oignon", 601),
        PairingEntry("mushroom", "champignon", 167),
        PairingEntry("garlic", "ail", 501),
        PairingEntry("sake", null, 63)
    )),
    "beer" to PairingsResult("beer", "bière", 303, listOf(
        PairingEntry("tamarind", "tamarin", 38),
        PairingEntry("mustard", "moutarde", 64),
        PairingEntry("meat", null, 25),
        PairingEntry("onion", "oignon", 168),
        PairingEntry("black_pepper", "poivre noir", 103),
        PairingEntry("beef", "boeuf", 61),
        PairingEntry("cayenne", "cayenne", 84),
        PairingEntry("garlic", "ail", 148)
    )),
    "beet" to PairingsResult("beet", "betterave", 231, listOf(
        PairingEntry("vinegar", "vinaigre", 132),
        PairingEntry("cider", null, 34),
        PairingEntry("horseradish", null, 18),
        PairingEntry("goat_cheese", "chèvre", 13),
        PairingEntry("red_wine", "vin rouge", 28),
        PairingEntry("turnip", null, 10),
        PairingEntry("olive_oil", "huile d'olive", 94),
        PairingEntry("shallot", "échalote", 24)
    )),
    "bell_pepper" to PairingsResult("bell_pepper", "poivron", 5846, listOf(
        PairingEntry("garlic", "ail", 3626),
        PairingEntry("olive_oil", "huile d'olive", 2358),
        PairingEntry("oregano", "origan", 1064),
        PairingEntry("onion", "oignon", 3544),
        PairingEntry("cumin", "cumin", 1005),
        PairingEntry("green_bell_pepper", "poivron vert", 838),
        PairingEntry("tomato", "tomate", 2111),
        PairingEntry("cayenne", "cayenne", 1796)
    )),
    "black_pepper" to PairingsResult("black_pepper", "poivre noir", 9752, listOf(
        PairingEntry("garlic", "ail", 5422),
        PairingEntry("onion", "oignon", 5429),
        PairingEntry("olive_oil", "huile d'olive", 3253),
        PairingEntry("thyme", "thym", 1298),
        PairingEntry("oregano", "origan", 1289),
        PairingEntry("bell_pepper", "poivron", 2004),
        PairingEntry("bay", null, 745),
        PairingEntry("beef", "boeuf", 1686)
    )),
    "black_tea" to PairingsResult("black_tea", "thé noir", 37, listOf(
        PairingEntry("tea", null, 5),
        PairingEntry("cardamom", "cardamome", 5),
        PairingEntry("ginger", "gingembre", 12),
        PairingEntry("lime", "citron vert", 6),
        PairingEntry("lemon", "citron", 10),
        PairingEntry("orange_juice", "jus d'orange", 7),
        PairingEntry("cinnamon", "cannelle", 11),
        PairingEntry("soy_sauce", "sauce soja", 7)
    )),
    "blackberry" to PairingsResult("blackberry", "mûre", 164, listOf(
        PairingEntry("raspberry", "framboise", 51),
        PairingEntry("blueberry", "myrtille", 41),
        PairingEntry("berry", null, 15),
        PairingEntry("strawberry", "fraise", 30),
        PairingEntry("peach", "pêche", 14),
        PairingEntry("buttermilk", "babeurre", 24),
        PairingEntry("gelatin", null, 21),
        PairingEntry("vanilla", "vanille", 64)
    )),
    "blue_cheese" to PairingsResult("blue_cheese", "fromage bleu", 395, listOf(
        PairingEntry("lettuce", "salade verte", 47),
        PairingEntry("vinegar", "vinaigre", 130),
        PairingEntry("walnut", "noix", 58),
        PairingEntry("olive_oil", "huile d'olive", 140),
        PairingEntry("pear", "poire", 19),
        PairingEntry("red_wine", "vin rouge", 35),
        PairingEntry("bacon", "bacon", 42),
        PairingEntry("grape_juice", null, 21)
    )),
    "blueberry" to PairingsResult("blueberry", "myrtille", 464, listOf(
        PairingEntry("strawberry", "fraise", 130),
        PairingEntry("raspberry", "framboise", 95),
        PairingEntry("blackberry", "mûre", 41),
        PairingEntry("berry", null, 24),
        PairingEntry("vanilla", "vanille", 179),
        PairingEntry("cream_cheese", "cream cheese", 83),
        PairingEntry("banana", "banane", 42),
        PairingEntry("kiwi", null, 14)
    )),
    "bread" to PairingsResult("bread", "pain", 4552, listOf(
        PairingEntry("parmesan_cheese", "parmesan", 611),
        PairingEntry("mozzarella_cheese", "mozzarella", 318),
        PairingEntry("swiss_cheese", null, 176),
        PairingEntry("cheddar_cheese", "cheddar", 544),
        PairingEntry("parsley", "persil", 846),
        PairingEntry("beef", "boeuf", 719),
        PairingEntry("lettuce", "salade verte", 258),
        PairingEntry("onion", "oignon", 2082)
    )),
    "broccoli" to PairingsResult("broccoli", "brocoli", 901, listOf(
        PairingEntry("cauliflower", "chou-fleur", 106),
        PairingEntry("macaroni", "macaronis", 195),
        PairingEntry("cheddar_cheese", "cheddar", 193),
        PairingEntry("carrot", "carotte", 191),
        PairingEntry("mushroom", "champignon", 167),
        PairingEntry("parmesan_cheese", "parmesan", 161),
        PairingEntry("chicken", "poulet", 200),
        PairingEntry("cheese", "fromage", 126)
    )),
    "buckwheat" to PairingsResult("buckwheat", "sarrasin", 69, listOf(
        PairingEntry("radish", "radis", 19),
        PairingEntry("katsuobushi", null, 6),
        PairingEntry("sesame_oil", null, 20),
        PairingEntry("seaweed", "algue", 7),
        PairingEntry("roasted_sesame_seed", null, 11),
        PairingEntry("pear", "poire", 9),
        PairingEntry("wasabi", null, 5),
        PairingEntry("scallion", "ciboule", 26)
    )),
    "butter" to PairingsResult("butter", "beurre", 20734, listOf(
        PairingEntry("wheat", "blé", 13075),
        PairingEntry("vanilla", "vanille", 6154),
        PairingEntry("milk", "lait", 7655),
        PairingEntry("egg", "œuf", 11119),
        PairingEntry("cocoa", "cacao", 3131),
        PairingEntry("pecan", "noix de pécan", 1483),
        PairingEntry("cane_molasses", null, 4078),
        PairingEntry("cream", "crème", 5061)
    )),
    "buttermilk" to PairingsResult("buttermilk", "babeurre", 1633, listOf(
        PairingEntry("egg", "œuf", 1285),
        PairingEntry("wheat", "blé", 1167),
        PairingEntry("vegetable_oil", "huile végétale", 584),
        PairingEntry("cream", "crème", 533),
        PairingEntry("whole_grain_wheat_flour", null, 84),
        PairingEntry("vanilla", "vanille", 464),
        PairingEntry("lard", "saindoux", 199),
        PairingEntry("butter", "beurre", 867)
    )),
    "cabbage" to PairingsResult("cabbage", "chou", 935, listOf(
        PairingEntry("carrot", "carotte", 325),
        PairingEntry("vinegar", "vinaigre", 395),
        PairingEntry("seed", null, 100),
        PairingEntry("vegetable_oil", "huile végétale", 385),
        PairingEntry("cider", null, 88),
        PairingEntry("scallion", "ciboule", 192),
        PairingEntry("soy_sauce", "sauce soja", 157),
        PairingEntry("onion", "oignon", 524)
    )),
    "cardamom" to PairingsResult("cardamom", "cardamome", 368, listOf(
        PairingEntry("cinnamon", "cannelle", 164),
        PairingEntry("ginger", "gingembre", 115),
        PairingEntry("coriander", null, 55),
        PairingEntry("turmeric", "curcuma", 48),
        PairingEntry("cumin", "cumin", 78),
        PairingEntry("saffron", "safran", 20),
        PairingEntry("pistachio", "pistache", 18),
        PairingEntry("lamb", "agneau", 23)
    )),
    "carrot" to PairingsResult("carrot", "carotte", 3570, listOf(
        PairingEntry("celery", "céleri", 1197),
        PairingEntry("potato", "pomme de terre", 806),
        PairingEntry("onion", "oignon", 2344),
        PairingEntry("bay", null, 426),
        PairingEntry("cabbage", "chou", 325),
        PairingEntry("chicken_broth", "bouillon de poulet", 648),
        PairingEntry("pea", "petit pois", 295),
        PairingEntry("thyme", "thym", 549)
    )),
    "cashew" to PairingsResult("cashew", "noix de cajou", 199, listOf(
        PairingEntry("nut", null, 29),
        PairingEntry("turmeric", "curcuma", 25),
        PairingEntry("brown_rice", null, 13),
        PairingEntry("grape", "raisin", 13),
        PairingEntry("fenugreek", null, 19),
        PairingEntry("soy_sauce", "sauce soja", 38),
        PairingEntry("chicken", "poulet", 52),
        PairingEntry("coriander", null, 24)
    )),
    "cauliflower" to PairingsResult("cauliflower", "chou-fleur", 325, listOf(
        PairingEntry("broccoli", "brocoli", 106),
        PairingEntry("carrot", "carotte", 104),
        PairingEntry("turmeric", "curcuma", 44),
        PairingEntry("coriander", null, 40),
        PairingEntry("fenugreek", null, 26),
        PairingEntry("pea", "petit pois", 29),
        PairingEntry("cheddar_cheese", "cheddar", 53),
        PairingEntry("celery", "céleri", 59)
    )),
    "cayenne" to PairingsResult("cayenne", "cayenne", 7948, listOf(
        PairingEntry("cumin", "cumin", 1849),
        PairingEntry("garlic", "ail", 5340),
        PairingEntry("cilantro", "coriandre", 1415),
        PairingEntry("tomato", "tomate", 3273),
        PairingEntry("onion", "oignon", 4845),
        PairingEntry("scallion", "ciboule", 1646),
        PairingEntry("lime_juice", "jus de citron vert", 726),
        PairingEntry("bell_pepper", "poivron", 1796)
    )),
    "celery" to PairingsResult("celery", "céleri", 3603, listOf(
        PairingEntry("carrot", "carotte", 1197),
        PairingEntry("onion", "oignon", 2714),
        PairingEntry("sage", "sauge", 387),
        PairingEntry("thyme", "thym", 753),
        PairingEntry("marjoram", null, 270),
        PairingEntry("chicken_broth", "bouillon de poulet", 756),
        PairingEntry("bay", null, 444),
        PairingEntry("green_bell_pepper", "poivron vert", 581)
    )),
    "celery_oil" to PairingsResult("celery_oil", "huile de céleri", 994, listOf(
        PairingEntry("corn", "maïs", 994),
        PairingEntry("vinegar", "vinaigre", 994),
        PairingEntry("tomato", "tomate", 994),
        PairingEntry("tamarind", "tamarin", 337),
        PairingEntry("garlic", "ail", 994),
        PairingEntry("mustard", "moutarde", 367),
        PairingEntry("cane_molasses", null, 522),
        PairingEntry("beef", "boeuf", 341)
    )),
    "cheddar_cheese" to PairingsResult("cheddar_cheese", "cheddar", 3029, listOf(
        PairingEntry("onion", "oignon", 1773),
        PairingEntry("cayenne", "cayenne", 834),
        PairingEntry("bacon", "bacon", 333),
        PairingEntry("broccoli", "brocoli", 193),
        PairingEntry("bread", "pain", 544),
        PairingEntry("potato", "pomme de terre", 448),
        PairingEntry("ham", null, 220),
        PairingEntry("green_bell_pepper", "poivron vert", 330)
    )),
    "cheese" to PairingsResult("cheese", "fromage", 3281, listOf(
        PairingEntry("macaroni", "macaronis", 644),
        PairingEntry("tomato", "tomate", 1240),
        PairingEntry("basil", "basilic", 604),
        PairingEntry("parmesan_cheese", "parmesan", 513),
        PairingEntry("mozzarella_cheese", "mozzarella", 273),
        PairingEntry("cayenne", "cayenne", 845),
        PairingEntry("bread", "pain", 509),
        PairingEntry("garlic", "ail", 1444)
    )),
    "cherry" to PairingsResult("cherry", "cerise", 1083, listOf(
        PairingEntry("pineapple", "ananas", 239),
        PairingEntry("almond", "amandes", 178),
        PairingEntry("pecan", "noix de pécan", 163),
        PairingEntry("gelatin", null, 123),
        PairingEntry("vanilla", "vanille", 393),
        PairingEntry("date", "datte", 58),
        PairingEntry("cherry_brandy", null, 20),
        PairingEntry("brandy", null, 57)
    )),
    "chicken" to PairingsResult("chicken", "poulet", 5292, listOf(
        PairingEntry("chicken_broth", "bouillon de poulet", 1000),
        PairingEntry("pepper", "poivre", 1651),
        PairingEntry("garlic", "ail", 2572),
        PairingEntry("celery", "céleri", 799),
        PairingEntry("onion", "oignon", 2683),
        PairingEntry("turmeric", "curcuma", 383),
        PairingEntry("mushroom", "champignon", 726),
        PairingEntry("cumin", "cumin", 686)
    )),
    "chicken_broth" to PairingsResult("chicken_broth", "bouillon de poulet", 3454, listOf(
        PairingEntry("chicken", "poulet", 1000),
        PairingEntry("celery", "céleri", 756),
        PairingEntry("thyme", "thym", 659),
        PairingEntry("onion", "oignon", 2137),
        PairingEntry("carrot", "carotte", 648),
        PairingEntry("sage", "sauge", 266),
        PairingEntry("bay", null, 335),
        PairingEntry("garlic", "ail", 1785)
    )),
    "chickpea" to PairingsResult("chickpea", "pois chiche", 404, listOf(
        PairingEntry("cumin", "cumin", 155),
        PairingEntry("olive_oil", "huile d'olive", 244),
        PairingEntry("coriander", null, 71),
        PairingEntry("turmeric", "curcuma", 60),
        PairingEntry("roasted_sesame_seed", null, 41),
        PairingEntry("kidney_bean", "haricot rouge", 34),
        PairingEntry("tomato", "tomate", 191),
        PairingEntry("garlic", "ail", 269)
    )),
    "chinese_cabbage" to PairingsResult("chinese_cabbage", "chou chinois", 161, listOf(
        PairingEntry("shrimp", "crevette", 108),
        PairingEntry("fish", "poisson", 107),
        PairingEntry("ginger", "gingembre", 130),
        PairingEntry("scallion", "ciboule", 128),
        PairingEntry("radish", "radis", 52),
        PairingEntry("sesame_oil", null, 71),
        PairingEntry("vegetable", null, 75),
        PairingEntry("roasted_sesame_seed", null, 47)
    )),
    "chive" to PairingsResult("chive", "ciboulette", 1315, listOf(
        PairingEntry("cucumber", "concombre", 577),
        PairingEntry("parsley", "persil", 756),
        PairingEntry("mustard", "moutarde", 643),
        PairingEntry("cream", "crème", 709),
        PairingEntry("vegetable_oil", "huile végétale", 714),
        PairingEntry("onion", "oignon", 786),
        PairingEntry("chervil", null, 22),
        PairingEntry("egg", "œuf", 791)
    )),
    "cilantro" to PairingsResult("cilantro", "coriandre", 2249, listOf(
        PairingEntry("cayenne", "cayenne", 1415),
        PairingEntry("lime_juice", "jus de citron vert", 582),
        PairingEntry("cumin", "cumin", 773),
        PairingEntry("avocado", "avocat", 228),
        PairingEntry("lime", "citron vert", 285),
        PairingEntry("black_bean", null, 173),
        PairingEntry("garlic", "ail", 1446),
        PairingEntry("coriander", null, 291)
    )),
    "cinnamon" to PairingsResult("cinnamon", "cannelle", 5600, listOf(
        PairingEntry("nutmeg", "muscade", 1480),
        PairingEntry("apple", "pomme", 1051),
        PairingEntry("raisin", "raisin sec", 872),
        PairingEntry("pumpkin", "potiron", 495),
        PairingEntry("cane_molasses", null, 1892),
        PairingEntry("ginger", "gingembre", 1175),
        PairingEntry("walnut", "noix", 874),
        PairingEntry("wheat", "blé", 3643)
    )),
    "cocoa" to PairingsResult("cocoa", "cacao", 4795, listOf(
        PairingEntry("vanilla", "vanille", 2830),
        PairingEntry("milk", "lait", 2509),
        PairingEntry("coffee", "café", 427),
        PairingEntry("peanut_butter", "beurre de cacahuète", 477),
        PairingEntry("wheat", "blé", 3302),
        PairingEntry("egg", "œuf", 3160),
        PairingEntry("butter", "beurre", 3131),
        PairingEntry("walnut", "noix", 670)
    )),
    "coconut" to PairingsResult("coconut", "noix de coco", 1709, listOf(
        PairingEntry("vanilla", "vanille", 728),
        PairingEntry("pineapple", "ananas", 236),
        PairingEntry("turmeric", "curcuma", 200),
        PairingEntry("fenugreek", null, 166),
        PairingEntry("coriander", null, 212),
        PairingEntry("lemongrass", "citronnelle", 61),
        PairingEntry("oat", "avoine", 170),
        PairingEntry("rum", "rhum", 111)
    )),
    "coffee" to PairingsResult("coffee", "café", 713, listOf(
        PairingEntry("cocoa", "cacao", 427),
        PairingEntry("vanilla", "vanille", 357),
        PairingEntry("cream", "crème", 333),
        PairingEntry("milk", "lait", 346),
        PairingEntry("rum", "rhum", 48),
        PairingEntry("egg", "œuf", 429),
        PairingEntry("cane_molasses", null, 177),
        PairingEntry("whiskey", null, 14)
    )),
    "corn" to PairingsResult("corn", "maïs", 4764, listOf(
        PairingEntry("celery_oil", "huile de céleri", 994),
        PairingEntry("tomato", "tomate", 2082),
        PairingEntry("cayenne", "cayenne", 1492),
        PairingEntry("vinegar", "vinaigre", 1334),
        PairingEntry("black_bean", null, 196),
        PairingEntry("onion", "oignon", 2376),
        PairingEntry("tamarind", "tamarin", 402),
        PairingEntry("garlic", "ail", 2222)
    )),
    "cottage_cheese" to PairingsResult("cottage_cheese", "fromage blanc", 348, listOf(
        PairingEntry("mozzarella_cheese", "mozzarella", 65),
        PairingEntry("macaroni", "macaronis", 87),
        PairingEntry("parmesan_cheese", "parmesan", 87),
        PairingEntry("egg_noodle", null, 25),
        PairingEntry("cheddar_cheese", "cheddar", 57),
        PairingEntry("basil", "basilic", 61),
        PairingEntry("oregano", "origan", 51),
        PairingEntry("egg", "œuf", 204)
    )),
    "crab" to PairingsResult("crab", "crabe", 550, listOf(
        PairingEntry("lovage", null, 53),
        PairingEntry("shrimp", "crevette", 134),
        PairingEntry("tamarind", "tamarin", 85),
        PairingEntry("scallion", "ciboule", 148),
        PairingEntry("bay", null, 77),
        PairingEntry("cream_cheese", "cream cheese", 103),
        PairingEntry("tabasco_pepper", null, 55),
        PairingEntry("clam", null, 32)
    )),
    "cranberry" to PairingsResult("cranberry", "canneberge", 914, listOf(
        PairingEntry("berry", null, 83),
        PairingEntry("orange", "orange", 151),
        PairingEntry("apple", "pomme", 177),
        PairingEntry("orange_juice", "jus d'orange", 126),
        PairingEntry("pineapple", "ananas", 103),
        PairingEntry("walnut", "noix", 139),
        PairingEntry("orange_peel", null, 54),
        PairingEntry("raspberry", "framboise", 62)
    )),
    "cream" to PairingsResult("cream", "crème", 10145, listOf(
        PairingEntry("milk", "lait", 4029),
        PairingEntry("vanilla", "vanille", 3040),
        PairingEntry("chive", "ciboulette", 709),
        PairingEntry("cream_cheese", "cream cheese", 1090),
        PairingEntry("gelatin", null, 665),
        PairingEntry("cocoa", "cacao", 1560),
        PairingEntry("butter", "beurre", 5061),
        PairingEntry("egg", "œuf", 5017)
    )),
    "cream_cheese" to PairingsResult("cream_cheese", "cream cheese", 2838, listOf(
        PairingEntry("cream", "crème", 1090),
        PairingEntry("vanilla", "vanille", 992),
        PairingEntry("pecan", "noix de pécan", 323),
        PairingEntry("wheat", "blé", 1531),
        PairingEntry("milk", "lait", 1023),
        PairingEntry("crab", "crabe", 103),
        PairingEntry("strawberry", "fraise", 156),
        PairingEntry("gelatin", null, 186)
    )),
    "cucumber" to PairingsResult("cucumber", "concombre", 1810, listOf(
        PairingEntry("chive", "ciboulette", 577),
        PairingEntry("mustard", "moutarde", 765),
        PairingEntry("parsley", "persil", 728),
        PairingEntry("vegetable_oil", "huile végétale", 937),
        PairingEntry("vinegar", "vinaigre", 740),
        PairingEntry("onion", "oignon", 1115),
        PairingEntry("lettuce", "salade verte", 173),
        PairingEntry("radish", "radis", 101)
    )),
    "cumin" to PairingsResult("cumin", "cumin", 3220, listOf(
        PairingEntry("coriander", null, 1242),
        PairingEntry("turmeric", "curcuma", 1034),
        PairingEntry("fenugreek", null, 857),
        PairingEntry("cayenne", "cayenne", 1849),
        PairingEntry("cilantro", "coriandre", 773),
        PairingEntry("garlic", "ail", 2165),
        PairingEntry("bell_pepper", "poivron", 1005),
        PairingEntry("pepper", "poivre", 1328)
    )),
    "date" to PairingsResult("date", "datte", 375, listOf(
        PairingEntry("walnut", "noix", 156),
        PairingEntry("raisin", "raisin sec", 80),
        PairingEntry("cherry", "cerise", 58),
        PairingEntry("pecan", "noix de pécan", 74),
        PairingEntry("currant", null, 21),
        PairingEntry("cinnamon", "cannelle", 107),
        PairingEntry("wheat", "blé", 269),
        PairingEntry("vanilla", "vanille", 142)
    )),
    "dill" to PairingsResult("dill", "aneth", 1108, listOf(
        PairingEntry("salmon", "saumon", 83),
        PairingEntry("vinegar", "vinaigre", 363),
        PairingEntry("smoked_salmon", "saumon fumé", 30),
        PairingEntry("mustard", "moutarde", 217),
        PairingEntry("cucumber", "concombre", 126),
        PairingEntry("vegetable_oil", "huile végétale", 391),
        PairingEntry("lemon_juice", "jus de citron", 214),
        PairingEntry("yogurt", "yaourt", 72)
    )),
    "egg" to PairingsResult("egg", "œuf", 20941, listOf(
        PairingEntry("wheat", "blé", 13946),
        PairingEntry("vanilla", "vanille", 6879),
        PairingEntry("milk", "lait", 8201),
        PairingEntry("butter", "beurre", 11119),
        PairingEntry("lard", "saindoux", 2323),
        PairingEntry("vegetable_oil", "huile végétale", 6145),
        PairingEntry("cocoa", "cacao", 3160),
        PairingEntry("yeast", "levure", 2329)
    )),
    "fennel" to PairingsResult("fennel", "fenouil", 907, listOf(
        PairingEntry("pork_sausage", null, 415),
        PairingEntry("olive_oil", "huile d'olive", 519),
        PairingEntry("lavender", null, 42),
        PairingEntry("basil", "basilic", 266),
        PairingEntry("cured_pork", null, 65),
        PairingEntry("savory", null, 43),
        PairingEntry("tomato", "tomate", 412),
        PairingEntry("oregano", "origan", 185)
    )),
    "feta_cheese" to PairingsResult("feta_cheese", "feta", 621, listOf(
        PairingEntry("olive_oil", "huile d'olive", 398),
        PairingEntry("olive", "olive", 154),
        PairingEntry("tomato", "tomate", 317),
        PairingEntry("oregano", "origan", 157),
        PairingEntry("mint", "menthe", 64),
        PairingEntry("macaroni", "macaronis", 124),
        PairingEntry("basil", "basilic", 139),
        PairingEntry("lettuce", "salade verte", 64)
    )),
    "fig" to PairingsResult("fig", "figue", 139, listOf(
        PairingEntry("apricot", "abricot", 18),
        PairingEntry("honey", "miel", 34),
        PairingEntry("goat_cheese", "chèvre", 10),
        PairingEntry("cinnamon", "cannelle", 46),
        PairingEntry("plum", null, 9),
        PairingEntry("raisin", "raisin sec", 23),
        PairingEntry("grape_juice", null, 14),
        PairingEntry("orange", "orange", 19)
    )),
    "fish" to PairingsResult("fish", "poisson", 1878, listOf(
        PairingEntry("chinese_cabbage", "chou chinois", 107),
        PairingEntry("shrimp", "crevette", 278),
        PairingEntry("scallion", "ciboule", 498),
        PairingEntry("radish", "radis", 141),
        PairingEntry("kelp", "varech", 85),
        PairingEntry("cayenne", "cayenne", 674),
        PairingEntry("lemongrass", "citronnelle", 73),
        PairingEntry("soybean", "soja", 186)
    )),
    "garlic" to PairingsResult("garlic", "ail", 16893, listOf(
        PairingEntry("tomato", "tomate", 6767),
        PairingEntry("cayenne", "cayenne", 5340),
        PairingEntry("olive_oil", "huile d'olive", 6188),
        PairingEntry("onion", "oignon", 9573),
        PairingEntry("oregano", "origan", 2459),
        PairingEntry("black_pepper", "poivre noir", 5422),
        PairingEntry("basil", "basilic", 2685),
        PairingEntry("bell_pepper", "poivron", 3626)
    )),
    "ginger" to PairingsResult("ginger", "gingembre", 3809, listOf(
        PairingEntry("soy_sauce", "sauce soja", 1180),
        PairingEntry("sesame_oil", null, 518),
        PairingEntry("cinnamon", "cannelle", 1175),
        PairingEntry("pumpkin", "potiron", 353),
        PairingEntry("scallion", "ciboule", 887),
        PairingEntry("lovage", null, 139),
        PairingEntry("chinese_cabbage", "chou chinois", 130),
        PairingEntry("sake", null, 250)
    )),
    "goat_cheese" to PairingsResult("goat_cheese", "chèvre", 259, listOf(
        PairingEntry("olive_oil", "huile d'olive", 161),
        PairingEntry("basil", "basilic", 61),
        PairingEntry("beet", "betterave", 13),
        PairingEntry("fig", "figue", 10),
        PairingEntry("grape_juice", null, 22),
        PairingEntry("chicory", null, 9),
        PairingEntry("shallot", "échalote", 25),
        PairingEntry("thyme", "thym", 44)
    )),
    "grape" to PairingsResult("grape", "raisin", 345, listOf(
        PairingEntry("strawberry", "fraise", 66),
        PairingEntry("apple", "pomme", 95),
        PairingEntry("mandarin", "mandarine", 27),
        PairingEntry("melon", "melon", 21),
        PairingEntry("orange", "orange", 62),
        PairingEntry("pineapple", "ananas", 50),
        PairingEntry("gelatin", null, 46),
        PairingEntry("yogurt", "yaourt", 38)
    )),
    "grapefruit" to PairingsResult("grapefruit", "pamplemousse", 114, listOf(
        PairingEntry("orange", "orange", 30),
        PairingEntry("grape", "raisin", 11),
        PairingEntry("avocado", "avocat", 14),
        PairingEntry("lime_juice", "jus de citron vert", 17),
        PairingEntry("orange_juice", "jus d'orange", 18),
        PairingEntry("lettuce", "salade verte", 14),
        PairingEntry("lime", "citron vert", 12),
        PairingEntry("honey", "miel", 19)
    )),
    "green_bell_pepper" to PairingsResult("green_bell_pepper", "poivron vert", 2578, listOf(
        PairingEntry("onion", "oignon", 2176),
        PairingEntry("tomato", "tomate", 1461),
        PairingEntry("bell_pepper", "poivron", 838),
        PairingEntry("celery", "céleri", 581),
        PairingEntry("oregano", "origan", 456),
        PairingEntry("pepper", "poivre", 895),
        PairingEntry("garlic", "ail", 1400),
        PairingEntry("mushroom", "champignon", 431)
    )),
    "green_tea" to PairingsResult("green_tea", "thé vert", 32, listOf(
        PairingEntry("soybean", "soja", 5),
        PairingEntry("cream_cheese", "cream cheese", 5),
        PairingEntry("ginger", "gingembre", 5),
        PairingEntry("cream", "crème", 8),
        PairingEntry("wheat", "blé", 15),
        PairingEntry("vanilla", "vanille", 7),
        PairingEntry("milk", "lait", 9),
        PairingEntry("egg", "œuf", 12)
    )),
    "gruyere_cheese" to PairingsResult("gruyere_cheese", "gruyère", 44, listOf(
        PairingEntry("milk_fat", null, 16),
        PairingEntry("cheese", "fromage", 27),
        PairingEntry("ham", null, 12),
        PairingEntry("nutmeg", "muscade", 16),
        PairingEntry("thyme", "thym", 11),
        PairingEntry("parmesan_cheese", "parmesan", 11),
        PairingEntry("black_pepper", "poivre noir", 19),
        PairingEntry("butter", "beurre", 32)
    )),
    "hazelnut" to PairingsResult("hazelnut", "noisette", 288, listOf(
        PairingEntry("cocoa", "cacao", 111),
        PairingEntry("vanilla", "vanille", 98),
        PairingEntry("coffee", "café", 18),
        PairingEntry("butter", "beurre", 175),
        PairingEntry("wheat", "blé", 170),
        PairingEntry("fig", "figue", 7),
        PairingEntry("egg", "œuf", 164),
        PairingEntry("almond", "amandes", 30)
    )),
    "honey" to PairingsResult("honey", "miel", 2480, listOf(
        PairingEntry("whole_grain_wheat_flour", null, 184),
        PairingEntry("sake", null, 169),
        PairingEntry("soy_sauce", "sauce soja", 395),
        PairingEntry("mustard", "moutarde", 395),
        PairingEntry("oat", "avoine", 176),
        PairingEntry("ginger", "gingembre", 368),
        PairingEntry("orange_juice", "jus d'orange", 199),
        PairingEntry("orange", "orange", 182)
    )),
    "kelp" to PairingsResult("kelp", "varech", 178, listOf(
        PairingEntry("sake", null, 80),
        PairingEntry("shiitake", null, 60),
        PairingEntry("radish", "radis", 58),
        PairingEntry("enokidake", null, 34),
        PairingEntry("soybean", "soja", 71),
        PairingEntry("soy_sauce", "sauce soja", 113),
        PairingEntry("fish", "poisson", 85),
        PairingEntry("katsuobushi", null, 18)
    )),
    "kidney_bean" to PairingsResult("kidney_bean", "haricot rouge", 442, listOf(
        PairingEntry("bean", null, 150),
        PairingEntry("tomato", "tomate", 322),
        PairingEntry("cayenne", "cayenne", 244),
        PairingEntry("onion", "oignon", 386),
        PairingEntry("green_bell_pepper", "poivron vert", 118),
        PairingEntry("beef", "boeuf", 166),
        PairingEntry("lima_bean", null, 31),
        PairingEntry("black_bean", null, 44)
    )),
    "lamb" to PairingsResult("lamb", "agneau", 472, listOf(
        PairingEntry("mint", "menthe", 90),
        PairingEntry("olive_oil", "huile d'olive", 262),
        PairingEntry("rosemary", "romarin", 97),
        PairingEntry("garlic", "ail", 327),
        PairingEntry("cumin", "cumin", 108),
        PairingEntry("red_wine", "vin rouge", 65),
        PairingEntry("black_pepper", "poivre noir", 208),
        PairingEntry("coriander", null, 63)
    )),
    "lard" to PairingsResult("lard", "saindoux", 3051, listOf(
        PairingEntry("wheat", "blé", 2772),
        PairingEntry("egg", "œuf", 2323),
        PairingEntry("vanilla", "vanille", 1111),
        PairingEntry("cinnamon", "cannelle", 697),
        PairingEntry("butter", "beurre", 1758),
        PairingEntry("cane_molasses", null, 789),
        PairingEntry("cocoa", "cacao", 542),
        PairingEntry("nutmeg", "muscade", 340)
    )),
    "leek" to PairingsResult("leek", "poireau", 411, listOf(
        PairingEntry("chicken_broth", "bouillon de poulet", 147),
        PairingEntry("carrot", "carotte", 132),
        PairingEntry("thyme", "thym", 113),
        PairingEntry("bay", null, 66),
        PairingEntry("potato", "pomme de terre", 107),
        PairingEntry("celery", "céleri", 107),
        PairingEntry("turnip", null, 22),
        PairingEntry("white_wine", "vin blanc", 72)
    )),
    "lemon" to PairingsResult("lemon", "citron", 3024, listOf(
        PairingEntry("lemon_juice", "jus de citron", 740),
        PairingEntry("lime", "citron vert", 275),
        PairingEntry("orange", "orange", 254),
        PairingEntry("artichoke", "artichaut", 80),
        PairingEntry("salmon", "saumon", 79),
        PairingEntry("tea", null, 33),
        PairingEntry("orange_juice", "jus d'orange", 189),
        PairingEntry("olive_oil", "huile d'olive", 761)
    )),
    "lemon_juice" to PairingsResult("lemon_juice", "jus de citron", 5022, listOf(
        PairingEntry("lemon_peel", null, 390),
        PairingEntry("lemon", "citron", 740),
        PairingEntry("olive_oil", "huile d'olive", 1557),
        PairingEntry("parsley", "persil", 957),
        PairingEntry("mint", "menthe", 232),
        PairingEntry("white_wine", "vin blanc", 399),
        PairingEntry("yogurt", "yaourt", 218),
        PairingEntry("lettuce", "salade verte", 233)
    )),
    "lemongrass" to PairingsResult("lemongrass", "citronnelle", 137, listOf(
        PairingEntry("galanga", null, 28),
        PairingEntry("thai_pepper", null, 27),
        PairingEntry("fish", "poisson", 73),
        PairingEntry("cilantro", "coriandre", 74),
        PairingEntry("coconut", "noix de coco", 61),
        PairingEntry("lime", "citron vert", 49),
        PairingEntry("coriander", null, 54),
        PairingEntry("lime_juice", "jus de citron vert", 46)
    )),
    "lentil" to PairingsResult("lentil", "lentille", 250, listOf(
        PairingEntry("carrot", "carotte", 94),
        PairingEntry("cumin", "cumin", 89),
        PairingEntry("turmeric", "curcuma", 54),
        PairingEntry("coriander", null, 49),
        PairingEntry("onion", "oignon", 201),
        PairingEntry("fenugreek", null, 33),
        PairingEntry("barley", "orge", 17),
        PairingEntry("celery", "céleri", 66)
    )),
    "lettuce" to PairingsResult("lettuce", "salade verte", 1152, listOf(
        PairingEntry("avocado", "avocat", 115),
        PairingEntry("vinegar", "vinaigre", 460),
        PairingEntry("cucumber", "concombre", 173),
        PairingEntry("tomato", "tomate", 496),
        PairingEntry("vegetable_oil", "huile végétale", 477),
        PairingEntry("bread", "pain", 258),
        PairingEntry("scallion", "ciboule", 240),
        PairingEntry("olive", "olive", 129)
    )),
    "lime" to PairingsResult("lime", "citron vert", 1069, listOf(
        PairingEntry("lime_juice", "jus de citron vert", 250),
        PairingEntry("cilantro", "coriandre", 285),
        PairingEntry("lemon", "citron", 275),
        PairingEntry("tequila", null, 59),
        PairingEntry("lemongrass", "citronnelle", 49),
        PairingEntry("galanga", null, 27),
        PairingEntry("avocado", "avocat", 87),
        PairingEntry("rum", "rhum", 70)
    )),
    "lime_juice" to PairingsResult("lime_juice", "jus de citron vert", 1423, listOf(
        PairingEntry("cilantro", "coriandre", 582),
        PairingEntry("lime", "citron vert", 250),
        PairingEntry("cayenne", "cayenne", 726),
        PairingEntry("avocado", "avocat", 181),
        PairingEntry("lime_peel_oil", null, 84),
        PairingEntry("mango", "mangue", 109),
        PairingEntry("cumin", "cumin", 313),
        PairingEntry("fish", "poisson", 203)
    )),
    "lobster" to PairingsResult("lobster", "homard", 126, listOf(
        PairingEntry("clam", null, 18),
        PairingEntry("mussel", null, 10),
        PairingEntry("scallop", "coquille Saint-Jacques", 12),
        PairingEntry("saffron", "safran", 10),
        PairingEntry("shrimp", "crevette", 23),
        PairingEntry("tarragon", "estragon", 12),
        PairingEntry("bay", null, 19),
        PairingEntry("sherry", null, 11)
    )),
    "macaroni" to PairingsResult("macaroni", "macaronis", 3073, listOf(
        PairingEntry("parmesan_cheese", "parmesan", 1136),
        PairingEntry("basil", "basilic", 1017),
        PairingEntry("tomato", "tomate", 1582),
        PairingEntry("olive_oil", "huile d'olive", 1505),
        PairingEntry("mozzarella_cheese", "mozzarella", 389),
        PairingEntry("cheese", "fromage", 644),
        PairingEntry("garlic", "ail", 1898),
        PairingEntry("oregano", "origan", 522)
    )),
    "mandarin" to PairingsResult("mandarin", "mandarine", 278, listOf(
        PairingEntry("pineapple", "ananas", 119),
        PairingEntry("gelatin", null, 60),
        PairingEntry("grape", "raisin", 27),
        PairingEntry("orange_juice", "jus d'orange", 52),
        PairingEntry("banana", "banane", 34),
        PairingEntry("orange", "orange", 44),
        PairingEntry("lettuce", "salade verte", 35),
        PairingEntry("kiwi", null, 10)
    )),
    "mango" to PairingsResult("mango", "mangue", 388, listOf(
        PairingEntry("lime_juice", "jus de citron vert", 109),
        PairingEntry("cilantro", "coriandre", 96),
        PairingEntry("coriander", null, 66),
        PairingEntry("papaya", null, 13),
        PairingEntry("lime", "citron vert", 49),
        PairingEntry("fenugreek", null, 44),
        PairingEntry("turmeric", "curcuma", 52),
        PairingEntry("ginger", "gingembre", 93)
    )),
    "maple_syrup" to PairingsResult("maple_syrup", "sirop d'érable", 467, listOf(
        PairingEntry("cider", null, 42),
        PairingEntry("cane_molasses", null, 140),
        PairingEntry("cinnamon", "cannelle", 112),
        PairingEntry("pecan", "noix de pécan", 60),
        PairingEntry("nutmeg", "muscade", 60),
        PairingEntry("yam", null, 9),
        PairingEntry("oat", "avoine", 37),
        PairingEntry("walnut", "noix", 60)
    )),
    "melon" to PairingsResult("melon", "melon", 153, listOf(
        PairingEntry("watermelon", "pastèque", 22),
        PairingEntry("lime_juice", "jus de citron vert", 40),
        PairingEntry("grape", "raisin", 21),
        PairingEntry("lime_peel_oil", null, 12),
        PairingEntry("mint", "menthe", 28),
        PairingEntry("orange_juice", "jus d'orange", 27),
        PairingEntry("pineapple", "ananas", 26),
        PairingEntry("strawberry", "fraise", 19)
    )),
    "milk" to PairingsResult("milk", "lait", 12885, listOf(
        PairingEntry("wheat", "blé", 8542),
        PairingEntry("vanilla", "vanille", 4576),
        PairingEntry("egg", "œuf", 8201),
        PairingEntry("yeast", "levure", 2122),
        PairingEntry("milk_fat", null, 947),
        PairingEntry("butter", "beurre", 7655),
        PairingEntry("cocoa", "cacao", 2509),
        PairingEntry("cream", "crème", 4029)
    )),
    "mint" to PairingsResult("mint", "menthe", 930, listOf(
        PairingEntry("lamb", "agneau", 90),
        PairingEntry("lime_juice", "jus de citron vert", 132),
        PairingEntry("yogurt", "yaourt", 98),
        PairingEntry("olive_oil", "huile d'olive", 385),
        PairingEntry("cucumber", "concombre", 125),
        PairingEntry("lemon_juice", "jus de citron", 232),
        PairingEntry("feta_cheese", "feta", 64),
        PairingEntry("cilantro", "coriandre", 127)
    )),
    "mozzarella_cheese" to PairingsResult("mozzarella_cheese", "mozzarella", 1289, listOf(
        PairingEntry("basil", "basilic", 647),
        PairingEntry("parmesan_cheese", "parmesan", 467),
        PairingEntry("tomato", "tomate", 822),
        PairingEntry("macaroni", "macaronis", 389),
        PairingEntry("oregano", "origan", 387),
        PairingEntry("olive_oil", "huile d'olive", 657),
        PairingEntry("cured_pork", null, 86),
        PairingEntry("pork_sausage", null, 175)
    )),
    "mushroom" to PairingsResult("mushroom", "champignon", 3317, listOf(
        PairingEntry("onion", "oignon", 2008),
        PairingEntry("macaroni", "macaronis", 512),
        PairingEntry("chicken", "poulet", 726),
        PairingEntry("green_bell_pepper", "poivron vert", 431),
        PairingEntry("beef", "boeuf", 634),
        PairingEntry("pepper", "poivre", 1000),
        PairingEntry("garlic", "ail", 1591),
        PairingEntry("egg_noodle", null, 108)
    )),
    "mustard" to PairingsResult("mustard", "moutarde", 4096, listOf(
        PairingEntry("vinegar", "vinaigre", 1880),
        PairingEntry("cucumber", "concombre", 765),
        PairingEntry("chive", "ciboulette", 643),
        PairingEntry("tamarind", "tamarin", 507),
        PairingEntry("celery_oil", "huile de céleri", 367),
        PairingEntry("lovage", null, 140),
        PairingEntry("onion", "oignon", 2431),
        PairingEntry("vegetable_oil", "huile végétale", 1619)
    )),
    "nutmeg" to PairingsResult("nutmeg", "muscade", 2512, listOf(
        PairingEntry("cinnamon", "cannelle", 1480),
        PairingEntry("pumpkin", "potiron", 348),
        PairingEntry("ginger", "gingembre", 521),
        PairingEntry("raisin", "raisin sec", 326),
        PairingEntry("wheat", "blé", 1665),
        PairingEntry("apple", "pomme", 364),
        PairingEntry("egg", "œuf", 1626),
        PairingEntry("cane_molasses", null, 738)
    )),
    "oat" to PairingsResult("oat", "avoine", 1267, listOf(
        PairingEntry("cane_molasses", null, 813),
        PairingEntry("vanilla", "vanille", 631),
        PairingEntry("raisin", "raisin sec", 235),
        PairingEntry("whole_grain_wheat_flour", null, 134),
        PairingEntry("wheat", "blé", 985),
        PairingEntry("cinnamon", "cannelle", 400),
        PairingEntry("cocoa", "cacao", 344),
        PairingEntry("peanut_butter", "beurre de cacahuète", 128)
    )),
    "olive" to PairingsResult("olive", "olive", 1795, listOf(
        PairingEntry("tomato", "tomate", 912),
        PairingEntry("pimento", null, 124),
        PairingEntry("olive_oil", "huile d'olive", 830),
        PairingEntry("feta_cheese", "feta", 154),
        PairingEntry("bell_pepper", "poivron", 532),
        PairingEntry("basil", "basilic", 400),
        PairingEntry("oregano", "origan", 350),
        PairingEntry("garlic", "ail", 1079)
    )),
    "olive_oil" to PairingsResult("olive_oil", "huile d'olive", 9843, listOf(
        PairingEntry("garlic", "ail", 6188),
        PairingEntry("basil", "basilic", 2205),
        PairingEntry("tomato", "tomate", 3803),
        PairingEntry("parsley", "persil", 2304),
        PairingEntry("bell_pepper", "poivron", 2358),
        PairingEntry("macaroni", "macaronis", 1505),
        PairingEntry("parmesan_cheese", "parmesan", 1457),
        PairingEntry("black_pepper", "poivre noir", 3253)
    )),
    "onion" to PairingsResult("onion", "oignon", 18030, listOf(
        PairingEntry("tomato", "tomate", 6806),
        PairingEntry("garlic", "ail", 9573),
        PairingEntry("green_bell_pepper", "poivron vert", 2176),
        PairingEntry("tamarind", "tamarin", 1621),
        PairingEntry("cayenne", "cayenne", 4845),
        PairingEntry("celery", "céleri", 2714),
        PairingEntry("beef", "boeuf", 3315),
        PairingEntry("pepper", "poivre", 5231)
    )),
    "orange" to PairingsResult("orange", "orange", 1703, listOf(
        PairingEntry("orange_juice", "jus d'orange", 527),
        PairingEntry("bitter_orange", null, 52),
        PairingEntry("cranberry", "canneberge", 151),
        PairingEntry("orange_peel", null, 113),
        PairingEntry("brandy", null, 78),
        PairingEntry("lemon", "citron", 254),
        PairingEntry("pineapple", "ananas", 161),
        PairingEntry("grape", "raisin", 62)
    )),
    "orange_juice" to PairingsResult("orange_juice", "jus d'orange", 1692, listOf(
        PairingEntry("orange", "orange", 527),
        PairingEntry("orange_peel", null, 251),
        PairingEntry("pineapple", "ananas", 179),
        PairingEntry("cranberry", "canneberge", 126),
        PairingEntry("honey", "miel", 199),
        PairingEntry("mandarin", "mandarine", 52),
        PairingEntry("ginger", "gingembre", 228),
        PairingEntry("lime_juice", "jus de citron vert", 112)
    )),
    "oregano" to PairingsResult("oregano", "origan", 3179, listOf(
        PairingEntry("basil", "basilic", 1350),
        PairingEntry("tomato", "tomate", 1778),
        PairingEntry("rosemary", "romarin", 671),
        PairingEntry("garlic", "ail", 2459),
        PairingEntry("thyme", "thym", 808),
        PairingEntry("bell_pepper", "poivron", 1064),
        PairingEntry("olive_oil", "huile d'olive", 1442),
        PairingEntry("cumin", "cumin", 664)
    )),
    "parmesan_cheese" to PairingsResult("parmesan_cheese", "parmesan", 3167, listOf(
        PairingEntry("macaroni", "macaronis", 1136),
        PairingEntry("basil", "basilic", 984),
        PairingEntry("mozzarella_cheese", "mozzarella", 467),
        PairingEntry("olive_oil", "huile d'olive", 1457),
        PairingEntry("garlic", "ail", 1874),
        PairingEntry("oregano", "origan", 554),
        PairingEntry("parsley", "persil", 752),
        PairingEntry("cheese", "fromage", 513)
    )),
    "parsley" to PairingsResult("parsley", "persil", 5562, listOf(
        PairingEntry("chive", "ciboulette", 756),
        PairingEntry("olive_oil", "huile d'olive", 2304),
        PairingEntry("cucumber", "concombre", 728),
        PairingEntry("basil", "basilic", 1064),
        PairingEntry("onion", "oignon", 3243),
        PairingEntry("white_wine", "vin blanc", 682),
        PairingEntry("garlic", "ail", 2844),
        PairingEntry("thyme", "thym", 794)
    )),
    "pea" to PairingsResult("pea", "petit pois", 1099, listOf(
        PairingEntry("carrot", "carotte", 295),
        PairingEntry("rice", "riz", 229),
        PairingEntry("chicken_broth", "bouillon de poulet", 197),
        PairingEntry("celery", "céleri", 193),
        PairingEntry("onion", "oignon", 616),
        PairingEntry("macaroni", "macaronis", 171),
        PairingEntry("potato", "pomme de terre", 171),
        PairingEntry("scallion", "ciboule", 193)
    )),
    "peach" to PairingsResult("peach", "pêche", 527, listOf(
        PairingEntry("raspberry", "framboise", 35),
        PairingEntry("orange_juice", "jus d'orange", 54),
        PairingEntry("nectarine", null, 9),
        PairingEntry("strawberry", "fraise", 39),
        PairingEntry("blackberry", "mûre", 14),
        PairingEntry("cinnamon", "cannelle", 113),
        PairingEntry("blueberry", "myrtille", 23),
        PairingEntry("grape", "raisin", 17)
    )),
    "peanut" to PairingsResult("peanut", "cacahuète", 447, listOf(
        PairingEntry("peanut_butter", "beurre de cacahuète", 133),
        PairingEntry("cocoa", "cacao", 138),
        PairingEntry("popcorn", null, 17),
        PairingEntry("rice", "riz", 85),
        PairingEntry("soy_sauce", "sauce soja", 81),
        PairingEntry("cereal", null, 17),
        PairingEntry("gelatin", null, 41),
        PairingEntry("vanilla", "vanille", 141)
    )),
    "peanut_butter" to PairingsResult("peanut_butter", "beurre de cacahuète", 992, listOf(
        PairingEntry("cocoa", "cacao", 477),
        PairingEntry("peanut", "cacahuète", 133),
        PairingEntry("vanilla", "vanille", 452),
        PairingEntry("oat", "avoine", 128),
        PairingEntry("roasted_peanut", null, 37),
        PairingEntry("cane_molasses", null, 314),
        PairingEntry("cereal", null, 30),
        PairingEntry("gelatin", null, 81)
    )),
    "pear" to PairingsResult("pear", "poire", 468, listOf(
        PairingEntry("radish", "radis", 50),
        PairingEntry("pear_brandy", null, 9),
        PairingEntry("ginger", "gingembre", 98),
        PairingEntry("cinnamon", "cannelle", 122),
        PairingEntry("chinese_cabbage", "chou chinois", 16),
        PairingEntry("roasted_sesame_seed", null, 30),
        PairingEntry("nut", null, 40),
        PairingEntry("sesame_oil", null, 41)
    )),
    "pecan" to PairingsResult("pecan", "noix de pécan", 2176, listOf(
        PairingEntry("vanilla", "vanille", 1069),
        PairingEntry("cane_molasses", null, 768),
        PairingEntry("butter", "beurre", 1483),
        PairingEntry("wheat", "blé", 1456),
        PairingEntry("egg", "œuf", 1443),
        PairingEntry("cocoa", "cacao", 488),
        PairingEntry("cinnamon", "cannelle", 533),
        PairingEntry("cream_cheese", "cream cheese", 323)
    )),
    "pepper" to PairingsResult("pepper", "poivre", 9059, listOf(
        PairingEntry("fenugreek", null, 854),
        PairingEntry("turmeric", "curcuma", 899),
        PairingEntry("onion", "oignon", 5231),
        PairingEntry("coriander", null, 959),
        PairingEntry("cumin", "cumin", 1328),
        PairingEntry("garlic", "ail", 4339),
        PairingEntry("tomato", "tomate", 2769),
        PairingEntry("chicken", "poulet", 1651)
    )),
    "pineapple" to PairingsResult("pineapple", "ananas", 1615, listOf(
        PairingEntry("cherry", "cerise", 239),
        PairingEntry("gelatin", null, 259),
        PairingEntry("mandarin", "mandarine", 119),
        PairingEntry("coconut", "noix de coco", 236),
        PairingEntry("banana", "banane", 173),
        PairingEntry("rum", "rhum", 102),
        PairingEntry("orange_juice", "jus d'orange", 179),
        PairingEntry("orange", "orange", 161)
    )),
    "pistachio" to PairingsResult("pistachio", "pistache", 219, listOf(
        PairingEntry("almond", "amandes", 55),
        PairingEntry("rose", null, 11),
        PairingEntry("cardamom", "cardamome", 18),
        PairingEntry("apricot", "abricot", 16),
        PairingEntry("vanilla", "vanille", 80),
        PairingEntry("cherry", "cerise", 21),
        PairingEntry("cocoa", "cacao", 49),
        PairingEntry("milk_fat", null, 17)
    )),
    "pork" to PairingsResult("pork", "porc", 1961, listOf(
        PairingEntry("soy_sauce", "sauce soja", 435),
        PairingEntry("garlic", "ail", 1124),
        PairingEntry("celery_oil", "huile de céleri", 162),
        PairingEntry("vinegar", "vinaigre", 578),
        PairingEntry("onion", "oignon", 1093),
        PairingEntry("black_pepper", "poivre noir", 660),
        PairingEntry("ginger", "gingembre", 324),
        PairingEntry("mustard", "moutarde", 338)
    )),
    "potato" to PairingsResult("potato", "pomme de terre", 3539, listOf(
        PairingEntry("carrot", "carotte", 806),
        PairingEntry("onion", "oignon", 2168),
        PairingEntry("pepper", "poivre", 1207),
        PairingEntry("celery", "céleri", 599),
        PairingEntry("bacon", "bacon", 383),
        PairingEntry("cheddar_cheese", "cheddar", 448),
        PairingEntry("black_pepper", "poivre noir", 988),
        PairingEntry("leek", "poireau", 107)
    )),
    "pumpkin" to PairingsResult("pumpkin", "potiron", 799, listOf(
        PairingEntry("nutmeg", "muscade", 348),
        PairingEntry("cinnamon", "cannelle", 495),
        PairingEntry("ginger", "gingembre", 353),
        PairingEntry("enokidake", null, 20),
        PairingEntry("vanilla", "vanille", 260),
        PairingEntry("pecan", "noix de pécan", 97),
        PairingEntry("soybean", "soja", 61),
        PairingEntry("wheat", "blé", 489)
    )),
    "radish" to PairingsResult("radish", "radis", 509, listOf(
        PairingEntry("scallion", "ciboule", 261),
        PairingEntry("kelp", "varech", 58),
        PairingEntry("chinese_cabbage", "chou chinois", 52),
        PairingEntry("fish", "poisson", 141),
        PairingEntry("enokidake", null, 42),
        PairingEntry("sake", null, 82),
        PairingEntry("soybean", "soja", 97),
        PairingEntry("vegetable", null, 112)
    )),
    "raisin" to PairingsResult("raisin", "raisin sec", 1903, listOf(
        PairingEntry("cinnamon", "cannelle", 872),
        PairingEntry("walnut", "noix", 465),
        PairingEntry("apple", "pomme", 381),
        PairingEntry("oat", "avoine", 235),
        PairingEntry("nutmeg", "muscade", 326),
        PairingEntry("cane_molasses", null, 566),
        PairingEntry("wheat", "blé", 1176),
        PairingEntry("date", "datte", 80)
    )),
    "raspberry" to PairingsResult("raspberry", "framboise", 781, listOf(
        PairingEntry("blueberry", "myrtille", 95),
        PairingEntry("blackberry", "mûre", 51),
        PairingEntry("strawberry", "fraise", 109),
        PairingEntry("gelatin", null, 105),
        PairingEntry("berry", null, 35),
        PairingEntry("cranberry", "canneberge", 62),
        PairingEntry("cream", "crème", 281),
        PairingEntry("vanilla", "vanille", 250)
    )),
    "red_wine" to PairingsResult("red_wine", "vin rouge", 1391, listOf(
        PairingEntry("vinegar", "vinaigre", 851),
        PairingEntry("olive_oil", "huile d'olive", 711),
        PairingEntry("beef_broth", "bouillon de bœuf", 132),
        PairingEntry("bay", null, 167),
        PairingEntry("tomato", "tomate", 539),
        PairingEntry("garlic", "ail", 776),
        PairingEntry("onion", "oignon", 776),
        PairingEntry("beef", "boeuf", 278)
    )),
    "rice" to PairingsResult("rice", "riz", 3385, listOf(
        PairingEntry("sesame_oil", null, 471),
        PairingEntry("soybean", "soja", 405),
        PairingEntry("soy_sauce", "sauce soja", 739),
        PairingEntry("scallion", "ciboule", 763),
        PairingEntry("roasted_sesame_seed", null, 186),
        PairingEntry("chicken_broth", "bouillon de poulet", 540),
        PairingEntry("seaweed", "algue", 101),
        PairingEntry("ginger", "gingembre", 569)
    )),
    "roasted_beef" to PairingsResult("roasted_beef", "boeuf rôti", 222, listOf(
        PairingEntry("beef", "boeuf", 94),
        PairingEntry("beef_broth", "bouillon de bœuf", 37),
        PairingEntry("horseradish", null, 22),
        PairingEntry("onion", "oignon", 142),
        PairingEntry("provolone_cheese", null, 9),
        PairingEntry("bay", null, 24),
        PairingEntry("garlic", "ail", 124),
        PairingEntry("red_wine", "vin rouge", 23)
    )),
    "roquefort_cheese" to PairingsResult("roquefort_cheese", "roquefort", 23, listOf(
        PairingEntry("lettuce", "salade verte", 6),
        PairingEntry("mustard", "moutarde", 8),
        PairingEntry("vinegar", "vinaigre", 9),
        PairingEntry("black_pepper", "poivre noir", 9),
        PairingEntry("olive_oil", "huile d'olive", 9),
        PairingEntry("cream", "crème", 9),
        PairingEntry("tomato", "tomate", 5)
    )),
    "rosemary" to PairingsResult("rosemary", "romarin", 1890, listOf(
        PairingEntry("thyme", "thym", 1042),
        PairingEntry("basil", "basilic", 736),
        PairingEntry("oregano", "origan", 671),
        PairingEntry("sage", "sauge", 352),
        PairingEntry("marjoram", null, 258),
        PairingEntry("olive_oil", "huile d'olive", 884),
        PairingEntry("black_pepper", "poivre noir", 830),
        PairingEntry("garlic", "ail", 1205)
    )),
    "rum" to PairingsResult("rum", "rhum", 599, listOf(
        PairingEntry("coconut", "noix de coco", 111),
        PairingEntry("pineapple", "ananas", 102),
        PairingEntry("lime", "citron vert", 70),
        PairingEntry("banana", "banane", 60),
        PairingEntry("coffee", "café", 48),
        PairingEntry("vanilla", "vanille", 220),
        PairingEntry("gin", null, 14),
        PairingEntry("nutmeg", "muscade", 83)
    )),
    "saffron" to PairingsResult("saffron", "safran", 236, listOf(
        PairingEntry("white_wine", "vin blanc", 61),
        PairingEntry("olive_oil", "huile d'olive", 142),
        PairingEntry("chicken_broth", "bouillon de poulet", 75),
        PairingEntry("mussel", null, 16),
        PairingEntry("fennel", "fenouil", 31),
        PairingEntry("cardamom", "cardamome", 20),
        PairingEntry("rice", "riz", 61),
        PairingEntry("pea", "petit pois", 33)
    )),
    "sage" to PairingsResult("sage", "sauge", 903, listOf(
        PairingEntry("marjoram", null, 254),
        PairingEntry("rosemary", "romarin", 352),
        PairingEntry("thyme", "thym", 431),
        PairingEntry("celery", "céleri", 387),
        PairingEntry("chicken_broth", "bouillon de poulet", 266),
        PairingEntry("black_pepper", "poivre noir", 474),
        PairingEntry("turkey", "dinde", 108),
        PairingEntry("onion", "oignon", 545)
    )),
    "salmon" to PairingsResult("salmon", "saumon", 433, listOf(
        PairingEntry("dill", "aneth", 83),
        PairingEntry("lemon", "citron", 79),
        PairingEntry("lemon_juice", "jus de citron", 101),
        PairingEntry("olive_oil", "huile d'olive", 144),
        PairingEntry("white_wine", "vin blanc", 49),
        PairingEntry("black_pepper", "poivre noir", 140),
        PairingEntry("soy_sauce", "sauce soja", 62),
        PairingEntry("tarragon", "estragon", 18)
    )),
    "scallion" to PairingsResult("scallion", "ciboule", 4342, listOf(
        PairingEntry("sesame_oil", null, 791),
        PairingEntry("soy_sauce", "sauce soja", 1193),
        PairingEntry("cayenne", "cayenne", 1646),
        PairingEntry("roasted_sesame_seed", null, 351),
        PairingEntry("soybean", "soja", 454),
        PairingEntry("ginger", "gingembre", 887),
        PairingEntry("radish", "radis", 261),
        PairingEntry("rice", "riz", 763)
    )),
    "scallop" to PairingsResult("scallop", "coquille Saint-Jacques", 268, listOf(
        PairingEntry("shrimp", "crevette", 94),
        PairingEntry("white_wine", "vin blanc", 89),
        PairingEntry("clam", null, 45),
        PairingEntry("shallot", "échalote", 40),
        PairingEntry("mussel", null, 16),
        PairingEntry("olive_oil", "huile d'olive", 121),
        PairingEntry("lobster", "homard", 12),
        PairingEntry("parsley", "persil", 78)
    )),
    "seaweed" to PairingsResult("seaweed", "algue", 188, listOf(
        PairingEntry("wasabi", null, 34),
        PairingEntry("katsuobushi", null, 21),
        PairingEntry("rice", "riz", 101),
        PairingEntry("sesame_oil", null, 60),
        PairingEntry("radish", "radis", 37),
        PairingEntry("roasted_sesame_seed", null, 38),
        PairingEntry("soy_sauce", "sauce soja", 75),
        PairingEntry("fish", "poisson", 56)
    )),
    "sesame_seed" to PairingsResult("sesame_seed", "graines de sésame", 655, listOf(
        PairingEntry("sesame_oil", null, 163),
        PairingEntry("soy_sauce", "sauce soja", 233),
        PairingEntry("scallion", "ciboule", 175),
        PairingEntry("rice", "riz", 143),
        PairingEntry("soybean", "soja", 68),
        PairingEntry("mandarin_peel", null, 10),
        PairingEntry("wasabi", null, 21),
        PairingEntry("seaweed", "algue", 27)
    )),
    "shallot" to PairingsResult("shallot", "échalote", 1186, listOf(
        PairingEntry("olive_oil", "huile d'olive", 591),
        PairingEntry("white_wine", "vin blanc", 231),
        PairingEntry("tarragon", "estragon", 91),
        PairingEntry("thyme", "thym", 223),
        PairingEntry("chicken_broth", "bouillon de poulet", 219),
        PairingEntry("lemongrass", "citronnelle", 37),
        PairingEntry("sherry", null, 73),
        PairingEntry("fish", "poisson", 137)
    )),
    "shrimp" to PairingsResult("shrimp", "crevette", 1543, listOf(
        PairingEntry("chinese_cabbage", "chou chinois", 108),
        PairingEntry("fish", "poisson", 278),
        PairingEntry("scallion", "ciboule", 423),
        PairingEntry("squid", null, 92),
        PairingEntry("crab", "crabe", 134),
        PairingEntry("clam", null, 122),
        PairingEntry("scallop", "coquille Saint-Jacques", 94),
        PairingEntry("cayenne", "cayenne", 597)
    )),
    "smoked_salmon" to PairingsResult("smoked_salmon", "saumon fumé", 97, listOf(
        PairingEntry("dill", "aneth", 30),
        PairingEntry("salmon_roe", null, 5),
        PairingEntry("chive", "ciboulette", 21),
        PairingEntry("cream_cheese", "cream cheese", 24),
        PairingEntry("shallot", "échalote", 10),
        PairingEntry("lemon_juice", "jus de citron", 24),
        PairingEntry("tarragon", "estragon", 6),
        PairingEntry("cucumber", "concombre", 12)
    )),
    "soy_sauce" to PairingsResult("soy_sauce", "sauce soja", 3236, listOf(
        PairingEntry("sesame_oil", null, 947),
        PairingEntry("sake", null, 521),
        PairingEntry("ginger", "gingembre", 1180),
        PairingEntry("scallion", "ciboule", 1193),
        PairingEntry("roasted_sesame_seed", null, 348),
        PairingEntry("soybean", "soja", 431),
        PairingEntry("rice", "riz", 739),
        PairingEntry("garlic", "ail", 2090)
    )),
    "soybean" to PairingsResult("soybean", "soja", 1028, listOf(
        PairingEntry("sesame_oil", null, 327),
        PairingEntry("soy_sauce", "sauce soja", 431),
        PairingEntry("rice", "riz", 405),
        PairingEntry("scallion", "ciboule", 454),
        PairingEntry("roasted_sesame_seed", null, 167),
        PairingEntry("sake", null, 141),
        PairingEntry("vegetable", null, 221),
        PairingEntry("kelp", "varech", 71)
    )),
    "star_anise" to PairingsResult("star_anise", "anis étoilé", 91, listOf(
        PairingEntry("ginger", "gingembre", 42),
        PairingEntry("tangerine", null, 5),
        PairingEntry("cardamom", "cardamome", 10),
        PairingEntry("soy_sauce", "sauce soja", 25),
        PairingEntry("wine", null, 11),
        PairingEntry("cinnamon", "cannelle", 30),
        PairingEntry("cilantro", "coriandre", 16),
        PairingEntry("shallot", "échalote", 11)
    )),
    "strawberry" to PairingsResult("strawberry", "fraise", 1073, listOf(
        PairingEntry("blueberry", "myrtille", 130),
        PairingEntry("gelatin", null, 189),
        PairingEntry("kiwi", null, 48),
        PairingEntry("raspberry", "framboise", 109),
        PairingEntry("banana", "banane", 122),
        PairingEntry("cream", "crème", 471),
        PairingEntry("grape", "raisin", 66),
        PairingEntry("rhubarb", null, 38)
    )),
    "sweet_potato" to PairingsResult("sweet_potato", "patate douce", 518, listOf(
        PairingEntry("yam", null, 45),
        PairingEntry("nutmeg", "muscade", 102),
        PairingEntry("cane_molasses", null, 187),
        PairingEntry("cinnamon", "cannelle", 151),
        PairingEntry("pecan", "noix de pécan", 76),
        PairingEntry("turnip", null, 16),
        PairingEntry("orange_juice", "jus d'orange", 52),
        PairingEntry("parsnip", null, 12)
    )),
    "tamarind" to PairingsResult("tamarind", "tamarin", 1663, listOf(
        PairingEntry("cane_molasses", null, 1604),
        PairingEntry("vinegar", "vinaigre", 1603),
        PairingEntry("celery_oil", "huile de céleri", 337),
        PairingEntry("onion", "oignon", 1621),
        PairingEntry("tabasco_pepper", null, 261),
        PairingEntry("mustard", "moutarde", 507),
        PairingEntry("beef", "boeuf", 522),
        PairingEntry("garlic", "ail", 1030)
    )),
    "tarragon" to PairingsResult("tarragon", "estragon", 476, listOf(
        PairingEntry("shallot", "échalote", 91),
        PairingEntry("chervil", null, 19),
        PairingEntry("white_wine", "vin blanc", 93),
        PairingEntry("chive", "ciboulette", 61),
        PairingEntry("parsley", "persil", 138),
        PairingEntry("mustard", "moutarde", 94),
        PairingEntry("vinegar", "vinaigre", 145),
        PairingEntry("lemon_juice", "jus de citron", 106)
    )),
    "thyme" to PairingsResult("thyme", "thym", 3041, listOf(
        PairingEntry("rosemary", "romarin", 1042),
        PairingEntry("marjoram", null, 348),
        PairingEntry("sage", "sauge", 431),
        PairingEntry("oregano", "origan", 808),
        PairingEntry("bay", null, 523),
        PairingEntry("basil", "basilic", 872),
        PairingEntry("celery", "céleri", 753),
        PairingEntry("black_pepper", "poivre noir", 1298)
    )),
    "tomato" to PairingsResult("tomato", "tomate", 9905, listOf(
        PairingEntry("garlic", "ail", 6767),
        PairingEntry("onion", "oignon", 6806),
        PairingEntry("basil", "basilic", 2273),
        PairingEntry("celery_oil", "huile de céleri", 994),
        PairingEntry("oregano", "origan", 1778),
        PairingEntry("cayenne", "cayenne", 3273),
        PairingEntry("olive_oil", "huile d'olive", 3803),
        PairingEntry("green_bell_pepper", "poivron vert", 1461)
    )),
    "tuna" to PairingsResult("tuna", "thon", 443, listOf(
        PairingEntry("wasabi", null, 23),
        PairingEntry("olive", "olive", 59),
        PairingEntry("lettuce", "salade verte", 44),
        PairingEntry("cucumber", "concombre", 54),
        PairingEntry("vegetable_oil", "huile végétale", 176),
        PairingEntry("fish", "poisson", 54),
        PairingEntry("egg_noodle", null, 20),
        PairingEntry("vinegar", "vinaigre", 136)
    )),
    "turkey" to PairingsResult("turkey", "dinde", 891, listOf(
        PairingEntry("sage", "sauge", 108),
        PairingEntry("celery", "céleri", 220),
        PairingEntry("chicken_broth", "bouillon de poulet", 198),
        PairingEntry("meat", null, 92),
        PairingEntry("onion", "oignon", 572),
        PairingEntry("thyme", "thym", 166),
        PairingEntry("smoke", null, 50),
        PairingEntry("rosemary", "romarin", 107)
    )),
    "turmeric" to PairingsResult("turmeric", "curcuma", 1233, listOf(
        PairingEntry("fenugreek", null, 862),
        PairingEntry("coriander", null, 971),
        PairingEntry("cumin", "cumin", 1034),
        PairingEntry("pepper", "poivre", 899),
        PairingEntry("ginger", "gingembre", 362),
        PairingEntry("coconut", "noix de coco", 200),
        PairingEntry("chicken", "poulet", 383),
        PairingEntry("cilantro", "coriandre", 230)
    )),
    "vanilla" to PairingsResult("vanilla", "vanille", 9017, listOf(
        PairingEntry("cocoa", "cacao", 2830),
        PairingEntry("egg", "œuf", 6879),
        PairingEntry("wheat", "blé", 6808),
        PairingEntry("milk", "lait", 4576),
        PairingEntry("butter", "beurre", 6154),
        PairingEntry("walnut", "noix", 1281),
        PairingEntry("pecan", "noix de pécan", 1069),
        PairingEntry("cream", "crème", 3040)
    )),
    "vegetable_oil" to PairingsResult("vegetable_oil", "huile végétale", 10754, listOf(
        PairingEntry("egg", "œuf", 6145),
        PairingEntry("cucumber", "concombre", 937),
        PairingEntry("mustard", "moutarde", 1619),
        PairingEntry("chive", "ciboulette", 714),
        PairingEntry("onion", "oignon", 4743),
        PairingEntry("vinegar", "vinaigre", 2219),
        PairingEntry("soy_sauce", "sauce soja", 1068),
        PairingEntry("scallion", "ciboule", 1343)
    )),
    "vinegar" to PairingsResult("vinegar", "vinaigre", 7691, listOf(
        PairingEntry("tamarind", "tamarin", 1603),
        PairingEntry("celery_oil", "huile de céleri", 994),
        PairingEntry("cider", null, 968),
        PairingEntry("mustard", "moutarde", 1880),
        PairingEntry("red_wine", "vin rouge", 851),
        PairingEntry("onion", "oignon", 4405),
        PairingEntry("cane_molasses", null, 2267),
        PairingEntry("tomato", "tomate", 2609)
    )),
    "walnut" to PairingsResult("walnut", "noix", 2738, listOf(
        PairingEntry("vanilla", "vanille", 1281),
        PairingEntry("raisin", "raisin sec", 465),
        PairingEntry("cinnamon", "cannelle", 874),
        PairingEntry("wheat", "blé", 1978),
        PairingEntry("cocoa", "cacao", 670),
        PairingEntry("date", "datte", 156),
        PairingEntry("egg", "œuf", 1882),
        PairingEntry("cane_molasses", null, 875)
    )),
    "watermelon" to PairingsResult("watermelon", "pastèque", 107, listOf(
        PairingEntry("melon", "melon", 22),
        PairingEntry("lime_juice", "jus de citron vert", 30),
        PairingEntry("mint", "menthe", 19),
        PairingEntry("orange_juice", "jus d'orange", 20),
        PairingEntry("kiwi", null, 5),
        PairingEntry("grape", "raisin", 8),
        PairingEntry("feta_cheese", "feta", 9),
        PairingEntry("lime", "citron vert", 11)
    )),
    "wheat" to PairingsResult("wheat", "blé", 20769, listOf(
        PairingEntry("egg", "œuf", 13946),
        PairingEntry("butter", "beurre", 13075),
        PairingEntry("vanilla", "vanille", 6808),
        PairingEntry("yeast", "levure", 3326),
        PairingEntry("milk", "lait", 8542),
        PairingEntry("lard", "saindoux", 2772),
        PairingEntry("cocoa", "cacao", 3302),
        PairingEntry("cinnamon", "cannelle", 3643)
    )),
    "white_wine" to PairingsResult("white_wine", "vin blanc", 2165, listOf(
        PairingEntry("olive_oil", "huile d'olive", 1119),
        PairingEntry("parsley", "persil", 682),
        PairingEntry("shallot", "échalote", 231),
        PairingEntry("vinegar", "vinaigre", 726),
        PairingEntry("chicken_broth", "bouillon de poulet", 393),
        PairingEntry("mussel", null, 73),
        PairingEntry("scallop", "coquille Saint-Jacques", 89),
        PairingEntry("clam", null, 113)
    )),
    "yeast" to PairingsResult("yeast", "levure", 3397, listOf(
        PairingEntry("wheat", "blé", 3326),
        PairingEntry("milk", "lait", 2122),
        PairingEntry("egg", "œuf", 2329),
        PairingEntry("whole_grain_wheat_flour", null, 209),
        PairingEntry("butter", "beurre", 1948),
        PairingEntry("rye_flour", null, 73),
        PairingEntry("mozzarella_cheese", "mozzarella", 162),
        PairingEntry("cheddar_cheese", "cheddar", 306)
    )),
    "yogurt" to PairingsResult("yogurt", "yaourt", 1043, listOf(
        PairingEntry("turmeric", "curcuma", 118),
        PairingEntry("mint", "menthe", 98),
        PairingEntry("coriander", null, 128),
        PairingEntry("cumin", "cumin", 192),
        PairingEntry("strawberry", "fraise", 94),
        PairingEntry("fenugreek", null, 82),
        PairingEntry("banana", "banane", 85),
        PairingEntry("lemon_juice", "jus de citron", 218)
    )),
    "zucchini" to PairingsResult("zucchini", "courgette", 1093, listOf(
        PairingEntry("squash", null, 145),
        PairingEntry("basil", "basilic", 287),
        PairingEntry("tomato", "tomate", 463),
        PairingEntry("olive_oil", "huile d'olive", 449),
        PairingEntry("carrot", "carotte", 222),
        PairingEntry("parmesan_cheese", "parmesan", 192),
        PairingEntry("oregano", "origan", 186),
        PairingEntry("bell_pepper", "poivron", 274)
    ))
)

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