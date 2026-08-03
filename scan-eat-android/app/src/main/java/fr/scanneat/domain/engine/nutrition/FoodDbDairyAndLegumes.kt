package fr.scanneat.domain.engine.nutrition

// ============================================================================
// FOOD DATABASE — dairy products, legumes, and nuts/seeds.
// Split out of FoodDb.kt; concatenated into FOOD_DB there.
// See FoodDb.kt for the CIQUAL provenance notice covering all entries.
// ============================================================================

internal val FOOD_DB_DAIRY_AND_LEGUMES: List<FoodEntry> = listOf(
    // Produits laitiers
    FoodEntry("lait demi-écrémé",  46.0,  3.2,  4.7,  1.6, 0.0, calciumMg = 120.0, b12Ug = 0.4, aliases = listOf("lait", "milk")),
    FoodEntry("yaourt nature",      60.0,  3.5,  4.7,  3.0, 0.0, calciumMg = 140.0, b12Ug = 0.4, aliases = listOf("yaourt", "yogurt")),
    FoodEntry("skyr",               60.0, 10.0,  4.0,  0.2, 0.0, calciumMg = 110.0, b12Ug = 0.5),
    FoodEntry("fromage blanc 0%",   45.0,  7.5,  4.0,  0.1, 0.0, calciumMg = 95.0, b12Ug = 0.3, aliases = listOf("fromage blanc")),
    FoodEntry("emmental",          380.0, 29.0,  0.0, 30.0, 0.0, saltG = 0.8, calciumMg = 880.0, vitDUg = 0.4, b12Ug = 1.9, aliases = listOf("gruyère")),
    FoodEntry("camembert",         300.0, 20.0,  0.5, 24.0, 0.0, saltG = 1.4, calciumMg = 400.0, vitDUg = 0.35, b12Ug = 1.3),

    // Légumineuses / oléagineux
    FoodEntry("lentille cuite",   115.0,  9.0, 20.0,  0.4, 3.8, ironMg = 3.3, aliases = listOf("lentilles", "lentils")),
    FoodEntry("pois chiche cuit", 165.0,  9.0, 27.0,  2.6, 4.5, ironMg = 2.9, aliases = listOf("pois chiches", "chickpea")),
    FoodEntry("amandes",          620.0, 21.0, 20.0, 51.0, 12.5, ironMg = 3.7, calciumMg = 260.0, aliases = listOf("amande", "almonds")),
    FoodEntry("noix",             655.0, 15.0, 14.0, 65.0,  6.7, ironMg = 2.9),

    // Produits laitiers (suite)
    FoodEntry("fromage de chèvre", 364.0, 22.0, 2.0, 29.0, 0.0, calciumMg = 140.0, aliases = listOf("goat cheese")),
    FoodEntry("mozzarella",        280.0, 22.0, 2.2, 21.0, 0.0, saltG = 0.6, calciumMg = 515.0),
    FoodEntry("feta",              264.0, 14.0, 4.1, 21.0, 0.0, saltG = 3.0, calciumMg = 493.0),
    FoodEntry("parmesan",          392.0, 35.0, 3.2, 26.0, 0.0, saltG = 1.6, calciumMg = 1180.0, b12Ug = 1.5),
    FoodEntry("lait entier",        64.0,  3.2, 4.8,  3.6, 0.0, calciumMg = 118.0, aliases = listOf("whole milk")),
    FoodEntry("crème fraîche",     292.0,  2.2, 3.4, 30.0, 0.0, calciumMg = 80.0),
    FoodEntry("lait de soja",       33.0,  3.0, 1.0,  1.8, 0.4, calciumMg = 120.0, aliases = listOf("soy milk")),
    FoodEntry("lait d'amande",      15.0,  0.5, 0.3,  1.2, 0.3, calciumMg = 120.0, aliases = listOf("almond milk")),

    // Légumineuses / oléagineux (suite)
    FoodEntry("haricot rouge cuit", 127.0, 8.7, 23.0, 0.5,  6.4, ironMg = 2.2, aliases = listOf("kidney bean")),
    FoodEntry("haricot blanc cuit", 139.0, 9.7, 25.0, 0.5,  6.3, ironMg = 2.5, aliases = listOf("white bean")),
    FoodEntry("edamame",             122.0, 11.0, 10.0, 5.2, 5.0, ironMg = 2.3),
    FoodEntry("noisette",            628.0, 15.0, 17.0, 61.0, 9.7, calciumMg = 114.0, aliases = listOf("noisettes", "hazelnut")),
    FoodEntry("noix de cajou",       553.0, 18.0, 30.0, 44.0, 3.3, ironMg = 6.7, aliases = listOf("cashew")),
    FoodEntry("pistache",            562.0, 20.0, 28.0, 45.0, 10.0, ironMg = 3.9, aliases = listOf("pistaches", "pistachio")),
    FoodEntry("graine de chia",      486.0, 17.0, 42.0, 31.0, 34.4, calciumMg = 631.0, ironMg = 7.7, aliases = listOf("chia seed")),
    FoodEntry("graine de lin",       534.0, 18.0, 29.0, 42.0, 27.3, ironMg = 5.7, aliases = listOf("flaxseed")),
    FoodEntry("beurre de cacahuète", 588.0, 25.0, 20.0, 50.0, 6.0, aliases = listOf("peanut butter")),
    FoodEntry("cacahuète",           567.0, 26.0, 16.0, 49.0, 8.5, aliases = listOf("cacahuètes", "peanut")),

    // Produits laitiers (extension 2026-08-03)
    FoodEntry("fromage cottage",  98.0, 11.0,  3.4,  4.3, 0.0, calciumMg = 61.0, aliases = listOf("cottage cheese")),
    FoodEntry("ricotta",         174.0, 11.0,  3.0, 13.0, 0.0, calciumMg = 207.0),
    FoodEntry("fromage à raclette", 380.0, 25.0, 0.5, 31.0, 0.0, saltG = 1.5, calciumMg = 750.0, aliases = listOf("raclette cheese")),
    FoodEntry("kéfir",            41.0,  3.3,  4.0,  1.0, 0.0, calciumMg = 120.0, b12Ug = 0.4, aliases = listOf("kefir")),
    FoodEntry("lait d'avoine",    47.0,  1.0,  6.7,  1.5, 0.8, calciumMg = 120.0, aliases = listOf("oat milk")),
    FoodEntry("yaourt grec",      97.0,  9.0,  4.0,  5.0, 0.0, calciumMg = 110.0, b12Ug = 0.5, aliases = listOf("greek yogurt")),

    // Légumineuses / oléagineux (extension)
    FoodEntry("fève cuite",       88.0,  7.6, 17.0,  0.5,  6.9, ironMg = 1.5, aliases = listOf("fèves cuites", "fava bean")),
    FoodEntry("soja cuit",       173.0, 16.6,  9.9,  9.0,  6.0, ironMg = 5.1, calciumMg = 102.0, aliases = listOf("soybean")),
    FoodEntry("noix du brésil",  656.0, 14.0, 12.0, 66.0,  7.5, aliases = listOf("brazil nut")),
    FoodEntry("noix de pécan",   691.0,  9.2, 14.0, 72.0,  9.6, aliases = listOf("pecan")),
    FoodEntry("graine de tournesol", 584.0, 21.0, 20.0, 51.0, 8.6, ironMg = 5.0, aliases = listOf("sunflower seed")),
    FoodEntry("graine de courge",  559.0, 30.0, 11.0, 49.0,  6.0, ironMg = 8.8, aliases = listOf("pumpkin seed")),
    FoodEntry("beurre d'amande",  614.0, 21.0, 19.0, 56.0, 10.3, calciumMg = 270.0, aliases = listOf("almond butter")),
)
