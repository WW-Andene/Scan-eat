package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.domain.model.NovaClass
import java.text.Normalizer

// ============================================================================
// FOOD DATABASE — port of public/data/food-db.js
//
// ⚠️  PROVENANCE NOTICE:
//   Values are hand-transcribed approximations of CIQUAL 2020
//   (ANSES, https://ciqual.anses.fr/, DOI 10.5281/zenodo.4770600).
//   NOT a bit-for-bit export. Accurate to ±10 % for these ~130 foods
//   (expanded 2026-07-17 from the original ~54 - Quick Add search and
//   LLM reconciliation previously missed most common everyday foods
//   outside that initial set, e.g. peach, cauliflower, turkey, tofu,
//   hazelnut, hummus - falling back to the LLM's own rough macro guess
//   instead of a grounded CIQUAL-style value for anything not in it).
//   Do not use for clinical or research work without verifying against
//   the canonical ANSES XML distribution.
//
// Used for: Quick Add autocomplete, LLM-identify reconciliation.
// ============================================================================

data class FoodEntry(
    val name: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double = 0.0,
    val saltG: Double = 0.0,
    // Per-100g, approximate CIQUAL-style values like the macros above — only
    // set for foods that are a genuine, well-known source of that nutrient
    // (the "Close the gap" suggestion engine needs real density values to
    // suggest anything for iron/calcium/vitD/B12; 0.0 elsewhere means "not
    // a notable source", same convention as fiberG/saltG defaulting to 0.0).
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitDUg: Double = 0.0,
    val b12Ug: Double = 0.0,
    val aliases: List<String> = emptyList(),
)

val FOOD_DB: List<FoodEntry> = listOf(
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

// ============================================================================
// Search
// ============================================================================

// Was compiled fresh on every normalize() call — searchFoodDB() calls
// normalize() once per name+alias of every FOOD_DB entry, on every single
// keystroke of a food search, so this pattern got recompiled on the order of
// (search-box keystrokes) x (FOOD_DB entries) x (aliases per entry) times.
// Compiling it once at class-init time removes that entirely.
private val DIACRITICS_RE = Regex("[\\u0300-\\u036f]")

private fun normalize(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS_RE, "")

// FOOD_DB itself never changes at runtime, but searchFoodDB() previously
// re-normalized every entry's name + all its aliases from scratch on every
// call — full Normalizer.normalize() + regex pass per string, per keystroke,
// for a list that's identical every time. Precomputing it once here turns a
// repeated-every-search cost into a one-time cost at class-init, the same
// strategy AdditivesDb.kt's NORMALIZED_ADDITIVES already uses for its
// synonym table. extraFoods (the user's custom foods) is NOT precomputed
// here since it's caller-supplied and can change between calls.
private val NORMALIZED_FOOD_DB: List<Pair<FoodEntry, List<String>>> =
    FOOD_DB.map { f -> f to (listOf(f.name) + f.aliases).map(::normalize) }

/**
 * Find up to [limit] foods whose name or alias starts with / contains [query].
 * Case- and accent-insensitive. Custom foods from [extraFoods] win ties
 * (same ranking as the original JS implementation).
 *
 * Port of searchFoodDB() from food-db.js.
 */
fun searchFoodDB(
    query: String,
    limit: Int = 6,
    extraFoods: List<FoodEntry> = emptyList(),
): List<FoodEntry> {
    val q = normalize(query.trim())
    if (q.length < 2) return emptyList()

    data class Ranked(val food: FoodEntry, val score: Double)

    val matches = mutableListOf<Ranked>()

    fun scoreOf(normHay: List<String>, custom: Boolean): Double? {
        val prefixIdx = normHay.indexOfFirst { it.startsWith(q) }
        return when {
            prefixIdx >= 0 -> if (custom) -0.5 else 0.0
            normHay.any { it.contains(q) } -> if (custom) 0.5 else 1.0
            else -> null
        }
    }

    // Custom foods are caller-supplied and can change between calls, so they're
    // normalized live; FOOD_DB below reuses the precomputed table instead.
    for (f in extraFoods) {
        val normHay = (listOf(f.name) + f.aliases).map(::normalize)
        scoreOf(normHay, custom = true)?.let { matches += Ranked(f, it) }
    }
    for ((f, normHay) in NORMALIZED_FOOD_DB) {
        scoreOf(normHay, custom = false)?.let { matches += Ranked(f, it) }
    }

    matches.sortWith(compareBy({ it.score }, { it.food.name }))
    return matches.take(limit).map { it.food }
}

// ============================================================================
// LLM reconciliation
// ============================================================================

data class ReconcileResult(
    val name: String,
    val estimatedGrams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val source: String,            // "db" | "llm"
    val matchedName: String? = null,
)

/**
 * Match an LLM-identified food against the DB. On hit, replace per-100g
 * macros with CIQUAL values while keeping the LLM's gram estimate.
 * Port of reconcileWithFoodDB() from food-db.js.
 */
fun reconcileWithFoodDB(
    name: String,
    estimatedGrams: Double,
    llmKcal: Double,
    llmProteinG: Double,
    llmCarbsG: Double,
    llmFatG: Double,
    extraFoods: List<FoodEntry> = emptyList(),
): ReconcileResult {
    if (estimatedGrams <= 0) {
        return ReconcileResult(name, estimatedGrams, llmKcal, llmProteinG, llmCarbsG, llmFatG, "llm")
    }

    fun tryMatch(q: String): FoodEntry? = searchFoodDB(q, 1, extraFoods).firstOrNull()

    var match = tryMatch(name)
    if (match == null) {
        val firstToken = name.trim().split(Regex("\\s+")).firstOrNull() ?: ""
        if (firstToken.length >= 2 && firstToken != name) match = tryMatch(firstToken)
    }

    if (match == null) {
        return ReconcileResult(name, estimatedGrams, llmKcal, llmProteinG, llmCarbsG, llmFatG, "llm")
    }

    val f = estimatedGrams / 100.0
    return ReconcileResult(
        name           = match.name,
        estimatedGrams = estimatedGrams,
        kcal           = (match.kcal  * f * 10).toLong() / 10.0,
        proteinG       = (match.proteinG * f * 10).toLong() / 10.0,
        carbsG         = (match.carbsG  * f * 10).toLong() / 10.0,
        fatG           = (match.fatG   * f * 10).toLong() / 10.0,
        source         = "db",
        matchedName    = match.name,
    )
}

/** Convert a FoodEntry + portion to a domain Product (for scoring quick-add foods). */
fun FoodEntry.toProduct(portionG: Double = 100.0): Product = Product(
    name        = name,
    category    = ProductCategory.OTHER,
    novaClass   = NovaClass.UNPROCESSED,
    ingredients = emptyList(),
    nutrition   = NutritionPer100g(
        energyKcal    = kcal,
        fatG          = fatG,
        saturatedFatG = 0.0,
        carbsG        = carbsG,
        sugarsG       = 0.0,
        fiberG        = fiberG,
        proteinG      = proteinG,
        saltG         = saltG,
        ironMg        = ironMg,
        calciumMg     = calciumMg,
        vitDUg        = vitDUg,
        b12Ug         = b12Ug,
    ),
    weightG = portionG,
)
