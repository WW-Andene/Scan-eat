package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// FOOD DATABASE — grains/starches and animal proteins.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_GRAINS_AND_PROTEINS: List<FoodEntry> = listOf(
    // Céréales / féculents
    FoodEntry("riz blanc cuit",  130.0, 2.7, 28.0, 0.3, 0.4, saturatedFatG = 0.1, sugarsG = 0.1, aliases = listOf("riz cuit", "white rice"), category = ProductCategory.GRAIN),
    FoodEntry("pâtes cuites",    140.0, 5.0, 28.0, 1.0, 1.8, saturatedFatG = 0.2, sugarsG = 0.8, aliases = listOf("pates", "pasta"), category = ProductCategory.GRAIN),
    FoodEntry("pain blanc",      260.0, 8.0, 50.0, 2.5, 2.7, saturatedFatG = 0.6, sugarsG = 4.0, aliases = listOf("pain", "bread"), category = ProductCategory.GRAIN),
    FoodEntry("pain complet",    240.0, 9.0, 45.0, 3.0, 6.5, saturatedFatG = 0.6, sugarsG = 3.0, ironMg = 2.5, magnesiumMg = 76.0, aliases = listOf("whole wheat bread"), category = ProductCategory.GRAIN),
    FoodEntry("baguette",        265.0, 8.0, 55.0, 1.0, 2.3, saturatedFatG = 0.2, sugarsG = 3.0, category = ProductCategory.GRAIN),
    FoodEntry("croissant",       406.0, 8.0, 45.0, 21.0, 1.6, saturatedFatG = 12.0, typicalPortionG = 60.0, sugarsG = 8.0, category = ProductCategory.GRAIN),
    FoodEntry("avoine",          389.0, 17.0, 66.0, 7.0, 10.6, saturatedFatG = 1.2, sugarsG = 1.0, ironMg = 4.7, magnesiumMg = 138.0, zincMg = 4.0, aliases = listOf("flocons d'avoine", "oats"), category = ProductCategory.GRAIN),
    FoodEntry("quinoa cuit",     120.0, 4.4, 22.0, 1.9, 2.8, saturatedFatG = 0.2, sugarsG = 0.9, ironMg = 1.5, magnesiumMg = 64.0, zincMg = 1.1, category = ProductCategory.GRAIN),

    // Protéines animales
    FoodEntry("poulet rôti",    215.0, 30.0,  0.0, 10.0, 0.0, saturatedFatG = 2.7, saltG = 0.2, b12Ug = 0.3, zincMg = 1.0, b6Mg = 0.5, category = ProductCategory.FRESH_MEAT),
    FoodEntry("boeuf haché 5%", 130.0, 22.0,  0.0,  5.0, 0.0, saturatedFatG = 2.0, saltG = 0.1, ironMg = 2.6, b12Ug = 2.0, zincMg = 4.5, aliases = listOf("steak haché 5%"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("boeuf haché 15%",215.0, 20.0,  0.0, 15.0, 0.0, saturatedFatG = 6.0, saltG = 0.1, ironMg = 2.7, b12Ug = 2.0, zincMg = 4.5, category = ProductCategory.FRESH_MEAT),
    FoodEntry("saumon",         208.0, 20.0,  0.0, 13.0, 0.0, saturatedFatG = 2.5, vitDUg = 8.0, b12Ug = 3.2, b6Mg = 0.6, aliases = listOf("salmon"), category = ProductCategory.FISH),
    FoodEntry("thon",           130.0, 29.0,  0.0,  1.0, 0.0, saturatedFatG = 0.3, vitDUg = 2.3, b12Ug = 2.9, b6Mg = 0.9, aliases = listOf("tuna"), category = ProductCategory.FISH),
    FoodEntry("oeuf",           155.0, 13.0,  1.1, 11.0, 0.0, saturatedFatG = 3.1, typicalPortionG = 55.0, ironMg = 1.8, calciumMg = 50.0, vitDUg = 1.8, b12Ug = 1.1, aliases = listOf("œuf", "egg")),
    FoodEntry("jambon blanc",   115.0, 20.0,  1.0,  4.0, 0.0, saturatedFatG = 1.4, saltG = 1.6, b12Ug = 0.6, aliases = listOf("ham"), category = ProductCategory.PROCESSED_MEAT),

    // Céréales / féculents (suite)
    FoodEntry("riz complet cuit", 123.0, 2.7, 26.0, 1.0, 1.8, saturatedFatG = 0.2, sugarsG = 0.5, ironMg = 0.6, aliases = listOf("brown rice"), category = ProductCategory.GRAIN),
    FoodEntry("semoule cuite",    112.0, 3.8, 23.0, 0.2, 1.5, saturatedFatG = 0.1, sugarsG = 0.3, aliases = listOf("couscous", "couscous cuit"), category = ProductCategory.GRAIN),
    FoodEntry("boulgour cuit",     83.0, 3.1, 19.0, 0.2, 4.5, saturatedFatG = 0.1, sugarsG = 0.2, aliases = listOf("bulgur"), category = ProductCategory.GRAIN),
    FoodEntry("sarrasin cuit",     92.0, 3.4, 20.0, 0.6, 2.7, saturatedFatG = 0.1, sugarsG = 0.5, aliases = listOf("buckwheat"), category = ProductCategory.GRAIN),
    FoodEntry("pain de mie",      265.0, 8.5, 49.0, 3.3, 2.5, saturatedFatG = 0.8, sugarsG = 5.0, aliases = listOf("sandwich bread"), category = ProductCategory.GRAIN),
    FoodEntry("tortilla de blé",  300.0, 8.0, 50.0, 7.0, 2.5, saturatedFatG = 1.5, sugarsG = 2.0, aliases = listOf("tortilla", "wrap"), category = ProductCategory.GRAIN),

    // Protéines animales (suite)
    FoodEntry("dinde",          135.0, 29.0,  0.0,  1.5, 0.0, saturatedFatG = 0.4, b12Ug = 0.3, zincMg = 1.5, b6Mg = 0.5, aliases = listOf("blanc de dinde", "turkey"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("porc",           242.0, 27.0,  0.0, 14.0, 0.0, saturatedFatG = 5.0, b12Ug = 0.7, zincMg = 2.0, aliases = listOf("filet de porc", "pork"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("agneau",         294.0, 25.0,  0.0, 21.0, 0.0, saturatedFatG = 9.0, ironMg = 1.6, b12Ug = 2.3, zincMg = 4.0, aliases = listOf("lamb"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("canard",         337.0, 19.0,  0.0, 28.0, 0.0, saturatedFatG = 9.7, ironMg = 2.7, aliases = listOf("duck"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("crevette",        99.0, 24.0,  0.2,  0.3, 0.0, saturatedFatG = 0.1, b12Ug = 1.1, zincMg = 1.3, aliases = listOf("crevettes", "shrimp"), category = ProductCategory.FISH),
    FoodEntry("moules",         172.0, 24.0,  7.0,  4.5, 0.0, saturatedFatG = 0.9, ironMg = 6.7, b12Ug = 12.0, zincMg = 1.6, aliases = listOf("mussels"), category = ProductCategory.FISH),
    FoodEntry("cabillaud",      105.0, 23.0,  0.0,  0.9, 0.0, saturatedFatG = 0.2, vitDUg = 1.3, b12Ug = 1.0, aliases = listOf("cod"), category = ProductCategory.FISH),
    FoodEntry("maquereau",      205.0, 19.0,  0.0, 14.0, 0.0, saturatedFatG = 3.4, vitDUg = 8.9, b12Ug = 8.7, aliases = listOf("mackerel"), category = ProductCategory.FISH),
    FoodEntry("sardine",        208.0, 25.0,  0.0, 11.0, 0.0, saturatedFatG = 3.0, saltG = 0.7, calciumMg = 380.0, vitDUg = 4.8, b12Ug = 8.9, category = ProductCategory.FISH),
    FoodEntry("tofu",            76.0,  8.0,  1.9,  4.8, 0.4, saturatedFatG = 0.7, calciumMg = 350.0, ironMg = 1.6, category = ProductCategory.PLANT_BASED_ALTERNATIVE),
    FoodEntry("jambon cru",      195.0, 27.0,  0.5,  9.0, 0.0, saturatedFatG = 3.0, saltG = 5.0, b12Ug = 1.0, aliases = listOf("prosciutto"), category = ProductCategory.PROCESSED_MEAT),
    FoodEntry("saucisse",        300.0, 13.0,  3.0, 26.0, 0.0, saturatedFatG = 9.5, sugarsG = 1.0, saltG = 1.5, aliases = listOf("sausage"), category = ProductCategory.PROCESSED_MEAT),
    FoodEntry("bacon",           400.0, 25.0,  1.0, 33.0, 0.0, saturatedFatG = 11.5, saltG = 2.5, category = ProductCategory.PROCESSED_MEAT),

    // Céréales / féculents (extension 2026-08-03)
    FoodEntry("riz basmati cuit", 130.0, 2.7, 28.0,  0.3,  0.5, saturatedFatG = 0.1, sugarsG = 0.1, aliases = listOf("basmati rice"), category = ProductCategory.GRAIN),
    FoodEntry("pain au levain",   250.0, 8.0, 49.0,  1.2,  3.0, saturatedFatG = 0.2, sugarsG = 1.5, aliases = listOf("sourdough bread"), category = ProductCategory.GRAIN),
    FoodEntry("galette de sarrasin", 200.0, 6.0, 40.0, 1.5, 2.5, saturatedFatG = 0.3, sugarsG = 1.0, aliases = listOf("buckwheat galette"), category = ProductCategory.GRAIN),
    FoodEntry("muesli",           360.0, 9.0, 66.0,  6.0,  8.0, saturatedFatG = 1.0, sugarsG = 20.0, category = ProductCategory.GRAIN),
    FoodEntry("céréales petit-déjeuner", 380.0, 7.0, 84.0, 1.0, 3.0, saturatedFatG = 0.3, sugarsG = 30.0, aliases = listOf("corn flakes", "cereal"), category = ProductCategory.GRAIN),
    FoodEntry("pain pita",        275.0, 9.0, 55.0,  1.2,  2.5, saturatedFatG = 0.2, sugarsG = 2.5, aliases = listOf("pita bread"), category = ProductCategory.GRAIN),
    FoodEntry("polenta cuite",     70.0, 1.6, 15.0,  0.3,  1.0, saturatedFatG = 0.1, sugarsG = 0.2, aliases = listOf("cooked polenta"), category = ProductCategory.GRAIN),
    FoodEntry("millet cuit",      119.0, 3.5, 23.0,  1.0,  1.3, saturatedFatG = 0.2, sugarsG = 0.2, ironMg = 0.6, category = ProductCategory.GRAIN),

    // Protéines animales (extension)
    FoodEntry("steak de boeuf",   190.0, 26.0,  0.0,  9.0, 0.0, saturatedFatG = 3.6, ironMg = 2.5, b12Ug = 2.0, aliases = listOf("beef steak"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("escalope de veau", 172.0, 30.0,  0.0,  5.0, 0.0, saturatedFatG = 1.9, b12Ug = 1.5, aliases = listOf("veal cutlet"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("foie de veau",     140.0, 20.0,  4.0,  4.0, 0.0, saturatedFatG = 1.3, ironMg = 6.0, vitDUg = 0.5, b12Ug = 45.0, vitAUg = 9500.0, zincMg = 4.0, b9Ug = 290.0, aliases = listOf("veal liver"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("lapin",            173.0, 21.0,  0.0,  9.0, 0.0, saturatedFatG = 2.7, b12Ug = 5.0, aliases = listOf("rabbit"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("pintade",          158.0, 22.0,  0.0,  7.5, 0.0, saturatedFatG = 2.2, aliases = listOf("guinea fowl"), category = ProductCategory.FRESH_MEAT),
    FoodEntry("lieu noir",         87.0, 18.0,  0.0,  1.0, 0.0, saturatedFatG = 0.2, b12Ug = 2.0, aliases = listOf("pollock", "colin"), category = ProductCategory.FISH),
    FoodEntry("truite",           148.0, 20.0,  0.0,  7.0, 0.0, saturatedFatG = 1.5, vitDUg = 10.0, b12Ug = 4.0, aliases = listOf("trout"), category = ProductCategory.FISH),
    FoodEntry("merlan",            84.0, 18.0,  0.0,  1.0, 0.0, saturatedFatG = 0.2, b12Ug = 1.2, aliases = listOf("whiting"), category = ProductCategory.FISH),
    FoodEntry("calamar",           92.0, 16.0,  3.0,  1.4, 0.0, saturatedFatG = 0.3, aliases = listOf("calmar", "squid"), category = ProductCategory.FISH),
    FoodEntry("poulpe",            82.0, 15.0,  2.0,  1.0, 0.0, saturatedFatG = 0.2, ironMg = 5.3, b12Ug = 20.0, aliases = listOf("octopus"), category = ProductCategory.FISH),
    FoodEntry("seitan",           370.0, 75.0, 14.0,  1.9, 6.0, saturatedFatG = 0.3, sugarsG = 0.5, category = ProductCategory.PLANT_BASED_ALTERNATIVE),
    FoodEntry("oeuf de caille",   158.0, 13.0,  0.4, 11.0, 0.0, saturatedFatG = 3.1, typicalPortionG = 10.0, ironMg = 3.7, aliases = listOf("œuf de caille", "quail egg")),
)
