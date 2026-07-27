package fr.scanneat.domain.engine.nutrition

// ============================================================================
// FOOD DATABASE — fruits and vegetables.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_FRUITS_AND_VEGETABLES: List<FoodEntry> = listOf(
    // Fruits
    FoodEntry("pomme",        54.0,  0.3,  12.0,  0.2,  2.4,  aliases = listOf("apple")),
    FoodEntry("banane",       90.0,  1.1,  20.0,  0.3,  2.6,  aliases = listOf("banana")),
    FoodEntry("orange",       45.0,  0.9,   9.0,  0.2,  2.2),
    FoodEntry("fraise",       33.0,  0.7,   5.0,  0.3,  2.0,  aliases = listOf("fraises", "strawberry")),
    FoodEntry("myrtille",     57.0,  0.7,  10.0,  0.3,  2.4,  aliases = listOf("myrtilles", "blueberry")),
    FoodEntry("avocat",      160.0,  2.0,   2.0, 15.0,  6.7,  aliases = listOf("avocado")),
    FoodEntry("kiwi",         61.0,  1.1,  11.0,  0.5,  3.0),
    FoodEntry("raisin",       69.0,  0.7,  16.0,  0.2,  0.9,  aliases = listOf("raisins", "grape")),

    // Légumes
    FoodEntry("tomate",       18.0,  0.9,   3.0,  0.2,  1.2,  aliases = listOf("tomate cerise", "tomato")),
    FoodEntry("carotte",      36.0,  0.6,   7.0,  0.2,  2.8,  aliases = listOf("carrot")),
    FoodEntry("brocoli",      30.0,  2.8,   2.0,  0.4,  2.6,  calciumMg = 47.0, aliases = listOf("broccoli")),
    FoodEntry("épinard",      23.0,  2.9,   1.0,  0.4,  2.2,  ironMg = 2.7, calciumMg = 99.0, aliases = listOf("épinards", "spinach")),
    FoodEntry("concombre",    12.0,  0.6,   2.0,  0.1,  0.5,  aliases = listOf("cucumber")),
    FoodEntry("courgette",    15.0,  1.3,   2.0,  0.1,  1.1,  aliases = listOf("zucchini")),
    FoodEntry("poivron",      27.0,  0.9,   5.0,  0.2,  1.9,  aliases = listOf("pepper")),
    FoodEntry("oignon",       34.0,  1.2,   6.0,  0.1,  1.7,  aliases = listOf("onion")),
    FoodEntry("salade verte", 15.0,  1.3,   1.5,  0.2,  1.3,  aliases = listOf("salade", "laitue", "lettuce")),
    FoodEntry("pomme de terre", 80.0, 2.0, 17.0,  0.1,  1.8,  aliases = listOf("patate", "potato")),

    // Fruits (suite)
    FoodEntry("pêche",         39.0,  0.9,   9.0,  0.3,  1.5, aliases = listOf("peach")),
    FoodEntry("poire",         57.0,  0.4,  15.0,  0.1,  3.1, aliases = listOf("pear")),
    FoodEntry("ananas",        50.0,  0.5,  13.0,  0.1,  1.4, aliases = listOf("pineapple")),
    FoodEntry("mangue",        60.0,  0.8,  15.0,  0.4,  1.6, aliases = listOf("mango")),
    FoodEntry("pastèque",      30.0,  0.6,   8.0,  0.2,  0.4, aliases = listOf("watermelon")),
    FoodEntry("melon",         34.0,  0.8,   8.0,  0.2,  0.9),
    FoodEntry("cerise",        63.0,  1.1,  16.0,  0.2,  2.1, aliases = listOf("cerises", "cherry")),
    FoodEntry("framboise",     52.0,  1.2,  12.0,  0.7,  6.5, aliases = listOf("framboises", "raspberry")),
    FoodEntry("mûre",          43.0,  1.4,  10.0,  0.5,  5.3, aliases = listOf("mûres", "blackberry")),
    FoodEntry("abricot",       48.0,  1.4,  11.0,  0.4,  2.0, aliases = listOf("apricot")),
    FoodEntry("prune",         46.0,  0.7,  11.0,  0.3,  1.4, aliases = listOf("plum")),
    FoodEntry("pamplemousse",  42.0,  0.8,  11.0,  0.1,  1.6, aliases = listOf("grapefruit")),
    FoodEntry("citron",        29.0,  1.1,   9.0,  0.3,  2.8, aliases = listOf("lemon")),
    FoodEntry("clémentine",    47.0,  0.8,  12.0,  0.2,  1.7, aliases = listOf("mandarine", "clementine")),
    FoodEntry("figue",         74.0,  0.8,  19.0,  0.3,  2.9, aliases = listOf("figues", "fig")),
    FoodEntry("datte",         282.0, 2.5,  75.0,  0.4,  8.0, ironMg = 1.0, aliases = listOf("dattes", "date")),
    FoodEntry("noix de coco",  354.0, 3.3,   6.2, 33.5,  9.0, aliases = listOf("coconut")),

    // Légumes (suite)
    FoodEntry("chou-fleur",    25.0,  1.9,   5.0,  0.3,  2.0, aliases = listOf("cauliflower")),
    FoodEntry("chou",          25.0,  1.3,   6.0,  0.1,  2.5, aliases = listOf("cabbage")),
    FoodEntry("chou de bruxelles", 43.0, 3.4, 9.0,  0.3,  3.8, aliases = listOf("choux de bruxelles", "brussels sprouts")),
    FoodEntry("aubergine",     25.0,  1.0,   6.0,  0.2,  3.0, aliases = listOf("eggplant")),
    FoodEntry("haricot vert",  31.0,  1.8,   7.0,  0.1,  3.4, aliases = listOf("haricots verts", "green bean")),
    FoodEntry("petit pois",    81.0,  5.4,  14.0,  0.4,  5.1, ironMg = 1.5, aliases = listOf("petits pois", "green pea")),
    FoodEntry("asperge",       20.0,  2.2,   3.9,  0.1,  2.1, aliases = listOf("asperges", "asparagus")),
    FoodEntry("champignon",    22.0,  3.1,   3.3,  0.3,  1.0, aliases = listOf("champignon de paris", "mushroom")),
    FoodEntry("betterave",     43.0,  1.6,  10.0,  0.2,  2.8, aliases = listOf("beetroot")),
    FoodEntry("radis",         16.0,  0.7,   3.4,  0.1,  1.6, aliases = listOf("radish")),
    FoodEntry("céleri",        16.0,  0.7,   3.0,  0.2,  1.6, aliases = listOf("celery")),
    FoodEntry("poireau",       61.0,  1.5,  14.0,  0.3,  1.8, aliases = listOf("poireaux", "leek")),
    FoodEntry("artichaut",     47.0,  3.3,  10.0,  0.2,  5.4, aliases = listOf("artichoke")),
    FoodEntry("patate douce",  86.0,  1.6,  20.0,  0.1,  3.0, aliases = listOf("sweet potato")),
    FoodEntry("maïs",          86.0,  3.2,  19.0,  1.2,  2.7, aliases = listOf("mais", "corn", "sweetcorn")),
    FoodEntry("ail",          149.0,  6.4,  33.0,  0.5,  2.1, aliases = listOf("garlic")),
)
