package fr.scanneat.domain.engine.planning

// ============================================================================
// PAIRINGS DATABASE — data shard: lettuce .. red_wine
// Split out of PairingsDb.kt (see that file for source/scoring notes).
// ============================================================================

internal val PAIRINGS_L_R: Map<String, PairingsResult> = mapOf(
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
)
