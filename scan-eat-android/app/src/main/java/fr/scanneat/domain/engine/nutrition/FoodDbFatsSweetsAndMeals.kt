package fr.scanneat.domain.engine.nutrition

// ============================================================================
// FOOD DATABASE — fats, sweets/snacks, beverages, and prepared meals.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_FATS_SWEETS_AND_MEALS: List<FoodEntry> = listOf(
    // Matières grasses
    FoodEntry("huile d'olive",  900.0, 0.0, 0.0, 100.0, 0.0, aliases = listOf("olive oil")),
    FoodEntry("beurre",         745.0, 0.7, 0.7,  82.0, 0.0, vitDUg = 0.76, aliases = listOf("butter")),

    // Sucreries / snacks
    FoodEntry("chocolat noir 70%", 580.0, 8.0, 46.0, 40.0, 10.9, ironMg = 11.0, aliases = listOf("chocolat", "dark chocolate")),
    FoodEntry("chocolat au lait",  540.0, 7.0, 58.0, 30.0,  1.5),
    FoodEntry("biscuit",           480.0, 6.0, 65.0, 21.0,  2.0),
    FoodEntry("miel",              304.0, 0.3, 82.0,  0.0,  0.2, aliases = listOf("honey")),

    // Boissons
    FoodEntry("café noir",      2.0,  0.3,  0.0,  0.0, 0.0, aliases = listOf("coffee", "café")),
    FoodEntry("thé",            1.0,  0.0,  0.0,  0.0, 0.0, aliases = listOf("tea")),
    FoodEntry("jus d'orange",  45.0,  0.7, 10.0,  0.2, 0.2),
    FoodEntry("coca-cola",     42.0,  0.0, 10.6,  0.0, 0.0),
    FoodEntry("bière",         43.0,  0.4,  3.6,  0.0, 0.0),
    FoodEntry("vin rouge",     83.0,  0.0,  2.6,  0.0, 0.0),
    FoodEntry("jus de pomme",  46.0,  0.1, 11.0,  0.1, 0.2, aliases = listOf("apple juice")),
    FoodEntry("eau gazeuse",    0.0,  0.0,  0.0,  0.0, 0.0, aliases = listOf("sparkling water")),
    FoodEntry("lait chocolaté", 83.0, 3.3, 11.0,  3.0, 0.5, calciumMg = 110.0, aliases = listOf("chocolate milk")),

    // Matières grasses (suite)
    FoodEntry("huile de colza",  900.0, 0.0, 0.0, 100.0, 0.0, aliases = listOf("rapeseed oil", "canola oil")),
    FoodEntry("huile de coco",   862.0, 0.0, 0.0, 100.0, 0.0, aliases = listOf("coconut oil")),
    FoodEntry("margarine",       717.0, 0.2, 0.9,  80.0, 0.0),
    FoodEntry("mayonnaise",      680.0, 1.1, 1.7,  75.0, 0.0),

    // Sucreries / snacks (suite)
    FoodEntry("pâte à tartiner", 539.0, 6.3, 57.0, 31.0, 3.4, aliases = listOf("chocolate spread")),
    FoodEntry("confiture",       278.0, 0.3, 69.0,  0.1, 1.0, aliases = listOf("jam")),
    FoodEntry("chips",           536.0, 6.6, 53.0, 34.0, 4.4, saltG = 1.5, aliases = listOf("potato chips")),
    FoodEntry("pop-corn",        387.0, 12.0, 78.0, 4.5, 14.5, aliases = listOf("popcorn")),
    FoodEntry("glace",           207.0, 3.5, 24.0, 11.0, 0.7, aliases = listOf("ice cream")),
    FoodEntry("crêpe nature",    250.0, 6.5, 30.0, 10.0, 1.0, aliases = listOf("pancake")),

    // Plats préparés
    FoodEntry("pizza margherita", 266.0, 11.0, 33.0, 10.0, 2.3, saltG = 1.4, aliases = listOf("pizza")),
    FoodEntry("hamburger",         250.0, 13.0, 30.0,  9.0, 1.5),
    FoodEntry("frites",            312.0,  3.4, 41.0, 15.0, 3.8, saltG = 0.6, aliases = listOf("french fries")),
    FoodEntry("sushi saumon",      150.0,  5.5, 28.0,  1.5, 1.0, aliases = listOf("sushi")),
    FoodEntry("houmous",           166.0,  8.0, 14.0,  9.6, 6.0, ironMg = 1.9, aliases = listOf("hummus")),
    FoodEntry("falafel",           333.0, 13.3, 32.0, 18.0, 8.0, ironMg = 3.4),
    FoodEntry("quiche lorraine",   300.0,  9.0, 18.0, 22.0, 1.0),
    FoodEntry("lasagne",           145.0,  8.0, 13.0,  7.0, 1.2, aliases = listOf("lasagnes", "lasagna")),
)
