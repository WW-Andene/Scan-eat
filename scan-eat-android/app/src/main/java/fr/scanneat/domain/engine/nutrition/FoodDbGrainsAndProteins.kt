package fr.scanneat.domain.engine.nutrition

// ============================================================================
// FOOD DATABASE — grains/starches and animal proteins.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_GRAINS_AND_PROTEINS: List<FoodEntry> = listOf(
    // Céréales / féculents
    FoodEntry("riz blanc cuit",  130.0, 2.7, 28.0, 0.3, 0.4, aliases = listOf("riz cuit", "white rice")),
    FoodEntry("pâtes cuites",    140.0, 5.0, 28.0, 1.0, 1.8, aliases = listOf("pates", "pasta")),
    FoodEntry("pain blanc",      260.0, 8.0, 50.0, 2.5, 2.7, aliases = listOf("pain", "bread")),
    FoodEntry("pain complet",    240.0, 9.0, 45.0, 3.0, 6.5, ironMg = 2.5, aliases = listOf("whole wheat bread")),
    FoodEntry("baguette",        265.0, 8.0, 55.0, 1.0, 2.3),
    FoodEntry("croissant",       406.0, 8.0, 45.0, 21.0, 1.6),
    FoodEntry("avoine",          389.0, 17.0, 66.0, 7.0, 10.6, ironMg = 4.7, aliases = listOf("flocons d'avoine", "oats")),
    FoodEntry("quinoa cuit",     120.0, 4.4, 22.0, 1.9, 2.8, ironMg = 1.5),

    // Protéines animales
    FoodEntry("poulet rôti",    215.0, 30.0,  0.0, 10.0, 0.0, saltG = 0.2, b12Ug = 0.3),
    FoodEntry("boeuf haché 5%", 130.0, 22.0,  0.0,  5.0, 0.0, saltG = 0.1, ironMg = 2.6, b12Ug = 2.0, aliases = listOf("steak haché 5%")),
    FoodEntry("boeuf haché 15%",215.0, 20.0,  0.0, 15.0, 0.0, saltG = 0.1, ironMg = 2.7, b12Ug = 2.0),
    FoodEntry("saumon",         208.0, 20.0,  0.0, 13.0, 0.0, vitDUg = 8.0, b12Ug = 3.2, aliases = listOf("salmon")),
    FoodEntry("thon",           130.0, 29.0,  0.0,  1.0, 0.0, vitDUg = 2.3, b12Ug = 2.9, aliases = listOf("tuna")),
    FoodEntry("oeuf",           155.0, 13.0,  1.1, 11.0, 0.0, ironMg = 1.8, calciumMg = 50.0, vitDUg = 1.8, b12Ug = 1.1, aliases = listOf("œuf", "egg")),
    FoodEntry("jambon blanc",   115.0, 20.0,  1.0,  4.0, 0.0, saltG = 1.6, b12Ug = 0.6, aliases = listOf("ham")),

    // Céréales / féculents (suite)
    FoodEntry("riz complet cuit", 123.0, 2.7, 26.0, 1.0, 1.8, ironMg = 0.6, aliases = listOf("brown rice")),
    FoodEntry("semoule cuite",    112.0, 3.8, 23.0, 0.2, 1.5, aliases = listOf("couscous", "couscous cuit")),
    FoodEntry("boulgour cuit",     83.0, 3.1, 19.0, 0.2, 4.5, aliases = listOf("bulgur")),
    FoodEntry("sarrasin cuit",     92.0, 3.4, 20.0, 0.6, 2.7, aliases = listOf("buckwheat")),
    FoodEntry("pain de mie",      265.0, 8.5, 49.0, 3.3, 2.5, aliases = listOf("sandwich bread")),
    FoodEntry("tortilla de blé",  300.0, 8.0, 50.0, 7.0, 2.5, aliases = listOf("tortilla", "wrap")),

    // Protéines animales (suite)
    FoodEntry("dinde",          135.0, 29.0,  0.0,  1.5, 0.0, b12Ug = 0.3, aliases = listOf("blanc de dinde", "turkey")),
    FoodEntry("porc",           242.0, 27.0,  0.0, 14.0, 0.0, b12Ug = 0.7, aliases = listOf("filet de porc", "pork")),
    FoodEntry("agneau",         294.0, 25.0,  0.0, 21.0, 0.0, ironMg = 1.6, b12Ug = 2.3, aliases = listOf("lamb")),
    FoodEntry("canard",         337.0, 19.0,  0.0, 28.0, 0.0, ironMg = 2.7, aliases = listOf("duck")),
    FoodEntry("crevette",        99.0, 24.0,  0.2,  0.3, 0.0, b12Ug = 1.1, aliases = listOf("crevettes", "shrimp")),
    FoodEntry("moules",         172.0, 24.0,  7.0,  4.5, 0.0, ironMg = 6.7, b12Ug = 12.0, aliases = listOf("mussels")),
    FoodEntry("cabillaud",      105.0, 23.0,  0.0,  0.9, 0.0, vitDUg = 1.3, b12Ug = 1.0, aliases = listOf("cod")),
    FoodEntry("maquereau",      205.0, 19.0,  0.0, 14.0, 0.0, vitDUg = 8.9, b12Ug = 8.7, aliases = listOf("mackerel")),
    FoodEntry("sardine",        208.0, 25.0,  0.0, 11.0, 0.0, saltG = 0.7, calciumMg = 380.0, vitDUg = 4.8, b12Ug = 8.9),
    FoodEntry("tofu",            76.0,  8.0,  1.9,  4.8, 0.4, calciumMg = 350.0, ironMg = 1.6),
    FoodEntry("jambon cru",      195.0, 27.0,  0.5,  9.0, 0.0, saltG = 5.0, b12Ug = 1.0, aliases = listOf("prosciutto")),
    FoodEntry("saucisse",        300.0, 13.0,  3.0, 26.0, 0.0, saltG = 1.5, aliases = listOf("sausage")),
    FoodEntry("bacon",           400.0, 25.0,  1.0, 33.0, 0.0, saltG = 2.5),
)
