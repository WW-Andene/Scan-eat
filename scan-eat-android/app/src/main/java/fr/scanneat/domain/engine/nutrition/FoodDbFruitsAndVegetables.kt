package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// FOOD DATABASE — fruits and vegetables.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_FRUITS_AND_VEGETABLES: List<FoodEntry> = listOf(
    // Fruits
    FoodEntry("pomme",        54.0,  0.3,  12.0,  0.2,  2.4,  sugarsG = 10.3, typicalPortionG = 180.0, aliases = listOf("apple"), category = ProductCategory.FRESH_PRODUCE),
    // User-reported (2nd round): "cover them" - banane is the textbook potassium/
    // B6 source and had neither, same class of gap vitCMg's own doc comment fixed.
    FoodEntry("banane",       90.0,  1.1,  20.0,  0.3,  2.6,  sugarsG = 15.0, typicalPortionG = 120.0, potassiumMg = 358.0, b6Mg = 0.4, aliases = listOf("banana"), category = ProductCategory.FRESH_PRODUCE),
    // User-reported: logging "orange" recorded calories but no vitamin C at all
    // in the dashboard/other tabs - vitCMg simply didn't exist on FoodEntry
    // before now (see its own doc comment), so even the single most obvious
    // vitamin C source in the whole database had no way to carry one.
    FoodEntry("orange",       45.0,  0.9,   9.0,  0.2,  2.2,  sugarsG = 8.0, typicalPortionG = 180.0, vitCMg = 53.2, potassiumMg = 181.0, b9Ug = 30.0, category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("fraise",       33.0,  0.7,   5.0,  0.3,  2.0,  sugarsG = 4.5, vitCMg = 58.8, aliases = listOf("fraises", "strawberry"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("myrtille",     57.0,  0.7,  10.0,  0.3,  2.4,  sugarsG = 7.5, aliases = listOf("myrtilles", "blueberry"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("avocat",      160.0,  2.0,   2.0, 15.0,  6.7,  saturatedFatG = 2.1, sugarsG = 0.7, typicalPortionG = 200.0, potassiumMg = 485.0, magnesiumMg = 29.0, vitEMg = 2.1, b6Mg = 0.3, b9Ug = 81.0, aliases = listOf("avocado"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("kiwi",         61.0,  1.1,  11.0,  0.5,  3.0,  sugarsG = 9.0, typicalPortionG = 75.0, vitCMg = 92.7, potassiumMg = 312.0, vitEMg = 1.5, vitKUg = 40.0, category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("raisin",       69.0,  0.7,  16.0,  0.2,  0.9,  sugarsG = 15.5, aliases = listOf("raisins", "grape"), category = ProductCategory.FRESH_PRODUCE),

    // Légumes
    FoodEntry("tomate",       18.0,  0.9,   3.0,  0.2,  1.2,  sugarsG = 2.6, typicalPortionG = 120.0, aliases = listOf("tomate cerise", "tomato"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("carotte",      36.0,  0.6,   7.0,  0.2,  2.8,  sugarsG = 4.7, typicalPortionG = 80.0, vitAUg = 835.0, aliases = listOf("carrot"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("brocoli",      30.0,  2.8,   2.0,  0.4,  2.6,  sugarsG = 1.7, calciumMg = 47.0, vitCMg = 89.2, vitKUg = 102.0, b9Ug = 63.0, potassiumMg = 316.0, aliases = listOf("broccoli"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("épinard",      23.0,  2.9,   1.0,  0.4,  2.2,  sugarsG = 0.4, ironMg = 2.7, calciumMg = 99.0, vitAUg = 469.0, vitKUg = 483.0, b9Ug = 194.0, magnesiumMg = 79.0, potassiumMg = 558.0, aliases = listOf("épinards", "spinach"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("concombre",    12.0,  0.6,   2.0,  0.1,  0.5,  sugarsG = 1.7, typicalPortionG = 300.0, aliases = listOf("cucumber"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("courgette",    15.0,  1.3,   2.0,  0.1,  1.1,  sugarsG = 2.5, typicalPortionG = 200.0, aliases = listOf("zucchini"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("poivron",      27.0,  0.9,   5.0,  0.2,  1.9,  sugarsG = 4.2, typicalPortionG = 120.0, vitCMg = 120.0, vitAUg = 157.0, potassiumMg = 211.0, aliases = listOf("pepper"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("oignon",       34.0,  1.2,   6.0,  0.1,  1.7,  sugarsG = 4.2, typicalPortionG = 100.0, aliases = listOf("onion"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("salade verte", 15.0,  1.3,   1.5,  0.2,  1.3,  sugarsG = 0.8, aliases = listOf("salade", "laitue", "lettuce"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("pomme de terre", 80.0, 2.0, 17.0,  0.1,  1.8,  sugarsG = 0.8, typicalPortionG = 150.0, potassiumMg = 425.0, magnesiumMg = 23.0, b6Mg = 0.3, aliases = listOf("patate", "potato"), category = ProductCategory.FRESH_PRODUCE),

    // Fruits (suite)
    FoodEntry("pêche",         39.0,  0.9,   9.0,  0.3,  1.5, sugarsG = 8.0, typicalPortionG = 150.0, aliases = listOf("peach"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("poire",         57.0,  0.4,  15.0,  0.1,  3.1, sugarsG = 9.8, typicalPortionG = 170.0, aliases = listOf("pear"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("ananas",        50.0,  0.5,  13.0,  0.1,  1.4, sugarsG = 9.9, aliases = listOf("pineapple"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("mangue",        60.0,  0.8,  15.0,  0.4,  1.6, sugarsG = 13.7, typicalPortionG = 200.0, aliases = listOf("mango"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("pastèque",      30.0,  0.6,   8.0,  0.2,  0.4, sugarsG = 6.2, aliases = listOf("watermelon"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("melon",         34.0,  0.8,   8.0,  0.2,  0.9, sugarsG = 8.0, category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("cerise",        63.0,  1.1,  16.0,  0.2,  2.1, sugarsG = 12.8, aliases = listOf("cerises", "cherry"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("framboise",     52.0,  1.2,  12.0,  0.7,  6.5, sugarsG = 4.4, aliases = listOf("framboises", "raspberry"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("mûre",          43.0,  1.4,  10.0,  0.5,  5.3, sugarsG = 4.9, aliases = listOf("mûres", "blackberry"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("abricot",       48.0,  1.4,  11.0,  0.4,  2.0, sugarsG = 9.2, typicalPortionG = 60.0, aliases = listOf("apricot"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("prune",         46.0,  0.7,  11.0,  0.3,  1.4, sugarsG = 9.9, typicalPortionG = 65.0, aliases = listOf("plum"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("pamplemousse",  42.0,  0.8,  11.0,  0.1,  1.6, sugarsG = 6.9, typicalPortionG = 250.0, vitCMg = 31.2, aliases = listOf("grapefruit"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("pomelo",        38.0,  0.8,   9.0,  0.1,  1.0, sugarsG = 7.3, typicalPortionG = 100.0, vitCMg = 61.0, aliases = listOf("pomélo"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("citron",        29.0,  1.1,   9.0,  0.3,  2.8, sugarsG = 2.5, typicalPortionG = 60.0, vitCMg = 53.0, aliases = listOf("lemon"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("clémentine",    47.0,  0.8,  12.0,  0.2,  1.7, sugarsG = 9.2, typicalPortionG = 70.0, vitCMg = 48.8, aliases = listOf("mandarine", "clementine"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("figue",         74.0,  0.8,  19.0,  0.3,  2.9, sugarsG = 16.3, typicalPortionG = 50.0, aliases = listOf("figues", "fig"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("datte",         282.0, 2.5,  75.0,  0.4,  8.0, sugarsG = 63.0, ironMg = 1.0, potassiumMg = 656.0, magnesiumMg = 54.0, aliases = listOf("dattes", "date"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("noix de coco",  354.0, 3.3,   6.2, 33.5,  9.0, saturatedFatG = 29.7, sugarsG = 6.2, aliases = listOf("coconut"), category = ProductCategory.FRESH_PRODUCE),

    // Légumes (suite)
    FoodEntry("chou-fleur",    25.0,  1.9,   5.0,  0.3,  2.0, sugarsG = 1.9, vitCMg = 48.2, vitKUg = 16.0, b9Ug = 57.0, aliases = listOf("cauliflower"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("chou",          25.0,  1.3,   6.0,  0.1,  2.5, sugarsG = 3.2, vitCMg = 36.6, vitKUg = 76.0, b9Ug = 43.0, aliases = listOf("cabbage"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("chou de bruxelles", 43.0, 3.4, 9.0,  0.3,  3.8, sugarsG = 2.2, vitCMg = 85.0, vitKUg = 177.0, b9Ug = 61.0, aliases = listOf("choux de bruxelles", "brussels sprouts"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("aubergine",     25.0,  1.0,   6.0,  0.2,  3.0, sugarsG = 3.2, typicalPortionG = 250.0, aliases = listOf("eggplant"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("haricot vert",  31.0,  1.8,   7.0,  0.1,  3.4, sugarsG = 3.3, aliases = listOf("haricots verts", "green bean"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("petit pois",    81.0,  5.4,  14.0,  0.4,  5.1, sugarsG = 5.7, ironMg = 1.5, zincMg = 1.2, magnesiumMg = 33.0, potassiumMg = 244.0, b9Ug = 65.0, aliases = listOf("petits pois", "green pea"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("asperge",       20.0,  2.2,   3.9,  0.1,  2.1, sugarsG = 1.9, potassiumMg = 202.0, b9Ug = 52.0, aliases = listOf("asperges", "asparagus"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("champignon",    22.0,  3.1,   3.3,  0.3,  1.0, sugarsG = 2.0, potassiumMg = 318.0, aliases = listOf("champignon de paris", "mushroom"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("betterave",     43.0,  1.6,  10.0,  0.2,  2.8, sugarsG = 6.8, typicalPortionG = 100.0, aliases = listOf("beetroot"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("radis",         16.0,  0.7,   3.4,  0.1,  1.6, sugarsG = 1.9, aliases = listOf("radish"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("céleri",        16.0,  0.7,   3.0,  0.2,  1.6, sugarsG = 1.8, aliases = listOf("celery"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("poireau",       61.0,  1.5,  14.0,  0.3,  1.8, sugarsG = 3.9, typicalPortionG = 150.0, aliases = listOf("poireaux", "leek"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("artichaut",     47.0,  3.3,  10.0,  0.2,  5.4, sugarsG = 1.0, typicalPortionG = 120.0, magnesiumMg = 60.0, potassiumMg = 370.0, b9Ug = 68.0, aliases = listOf("artichoke"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("patate douce",  86.0,  1.6,  20.0,  0.1,  3.0, sugarsG = 4.2, typicalPortionG = 130.0, vitAUg = 709.0, potassiumMg = 337.0, aliases = listOf("sweet potato"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("maïs",          86.0,  3.2,  19.0,  1.2,  2.7, sugarsG = 3.2, magnesiumMg = 37.0, potassiumMg = 270.0, aliases = listOf("mais", "corn", "sweetcorn"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("ail",          149.0,  6.4,  33.0,  0.5,  2.1, sugarsG = 1.0, typicalPortionG = 5.0, aliases = listOf("garlic"), category = ProductCategory.FRESH_PRODUCE),

    // Fruits (extension 2026-08-03 - user-reported: category rework surfaced how thin
    // the fruit/veg coverage was outside the original ~130-food set)
    FoodEntry("fruit de la passion", 68.0, 1.1, 12.0, 0.7, 3.3, sugarsG = 11.2, typicalPortionG = 40.0, aliases = listOf("passion fruit"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("litchi",        66.0,  0.8,  17.0,  0.4,  1.3, sugarsG = 15.2, aliases = listOf("litchis", "lychee"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("nectarine",     44.0,  1.1,  10.0,  0.3,  1.7, sugarsG = 8.4, typicalPortionG = 140.0, category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("rhubarbe",      21.0,  0.9,   4.5,  0.2,  1.8, sugarsG = 1.1, calciumMg = 86.0, aliases = listOf("rhubarb"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("groseille",     56.0,  1.4,  13.0,  0.2,  4.3, sugarsG = 7.4, aliases = listOf("groseilles", "redcurrant"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("goyave",        68.0,  2.6,  14.0,  1.0,  5.4, sugarsG = 8.9, typicalPortionG = 150.0, aliases = listOf("guava"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("papaye",        43.0,  0.5,  11.0,  0.3,  1.7, sugarsG = 7.8, typicalPortionG = 300.0, aliases = listOf("papaya"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("fruit du dragon", 60.0, 1.2, 13.0,  0.4,  3.0, sugarsG = 7.7, typicalPortionG = 300.0, aliases = listOf("pitaya", "dragon fruit"), category = ProductCategory.FRESH_PRODUCE),

    // Légumes (extension)
    FoodEntry("fenouil",       31.0,  1.2,   7.3,  0.2,  3.1, sugarsG = 3.9, aliases = listOf("fennel"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("panais",        75.0,  1.2,  18.0,  0.3,  4.9, sugarsG = 4.8, typicalPortionG = 130.0, aliases = listOf("parsnip"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("navet",         28.0,  0.9,   6.4,  0.1,  1.8, sugarsG = 3.8, typicalPortionG = 100.0, aliases = listOf("turnip"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("endive",        17.0,  1.3,   3.2,  0.1,  3.1, sugarsG = 0.5, typicalPortionG = 100.0, aliases = listOf("chicon"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("roquette",      25.0,  2.6,   3.7,  0.7,  1.6, sugarsG = 2.1, calciumMg = 160.0, ironMg = 1.5, aliases = listOf("arugula", "rocket"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("courge butternut", 45.0, 1.0, 12.0,  0.1,  2.0, sugarsG = 2.2, aliases = listOf("butternut squash"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("potiron",       26.0,  1.0,   6.5,  0.1,  0.5, sugarsG = 2.8, aliases = listOf("citrouille", "pumpkin"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("germe de soja", 30.0,  3.0,   6.0,  0.2,  1.8, sugarsG = 1.5, aliases = listOf("soja germé", "bean sprout"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("topinambour",   73.0,  2.0,  17.0,  0.0,  1.6, sugarsG = 9.6, typicalPortionG = 100.0, aliases = listOf("jerusalem artichoke"), category = ProductCategory.FRESH_PRODUCE),
)
