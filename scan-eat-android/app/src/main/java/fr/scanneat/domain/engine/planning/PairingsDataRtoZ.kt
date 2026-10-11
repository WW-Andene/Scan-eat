package fr.scanneat.domain.engine.planning

// ============================================================================
// PAIRINGS DATABASE — data shard: rice .. zucchini
// Split out of PairingsDb.kt (see that file for source/scoring notes).
// ============================================================================

internal val PAIRINGS_R_Z: Map<String, PairingsResult> = mapOf(
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
