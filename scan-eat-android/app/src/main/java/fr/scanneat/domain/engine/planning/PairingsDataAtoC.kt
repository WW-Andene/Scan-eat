package fr.scanneat.domain.engine.planning

// ============================================================================
// PAIRINGS DATABASE — data shard: almond .. chickpea
// Split out of PairingsDb.kt (see that file for source/scoring notes).
// ============================================================================

internal val PAIRINGS_A_C: Map<String, PairingsResult> = mapOf(
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
)
