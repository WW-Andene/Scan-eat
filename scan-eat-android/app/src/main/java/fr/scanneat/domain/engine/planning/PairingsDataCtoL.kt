package fr.scanneat.domain.engine.planning

// ============================================================================
// PAIRINGS DATABASE — data shard: chinese_cabbage .. lentil
// Split out of PairingsDb.kt (see that file for source/scoring notes).
// ============================================================================

internal val PAIRINGS_C_L: Map<String, PairingsResult> = mapOf(
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
)
