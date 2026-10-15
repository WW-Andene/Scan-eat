package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// FOOD DATABASE — composed dishes and cooking sauces, per 100 g as eaten.
// Same CIQUAL 2020 provenance notice as FoodDb.kt: rounded, hand-transcribed
// approximations of the matching ANSES CIQUAL "plats composés" / "sauces"
// entries (https://ciqual.anses.fr/) - check there before relying on a value.
// [typicalIngredients] lets allergen and diet checks run on a dish whose real
// recipe isn't known: only the components the dish is defined by.
// ============================================================================

internal val FOOD_DB_COMPOSED_DISHES: List<FoodEntry> = listOf(
    // Sauces (components for DishComposer)
    FoodEntry("sauce tomate",       50.0, 1.5,  6.5, 2.0, 1.5, saltG = 0.9, saturatedFatG = 0.3, sugarsG = 5.0,
        aliases = listOf("tomato sauce", "sauce tomates", "coulis de tomate"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("tomate", "oignon", "huile d'olive")),
    FoodEntry("sauce bolognaise",  110.0, 7.0,  5.0, 7.0, 1.2, saltG = 0.9, saturatedFatG = 2.5, sugarsG = 3.5,
        aliases = listOf("bolognaise", "bolognese", "sauce bolognese", "bolognese sauce"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("viande de boeuf hachée", "tomate", "oignon", "carotte", "huile d'olive")),
    FoodEntry("sauce carbonara",   230.0, 7.0,  4.0, 21.0, 0.2, saltG = 1.3, saturatedFatG = 11.0, sugarsG = 2.0,
        aliases = listOf("carbonara"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("crème", "lardons de porc", "oeuf", "fromage")),
    FoodEntry("sauce béchamel",    110.0, 3.5,  8.0, 7.0, 0.2, saltG = 0.6, saturatedFatG = 4.0, sugarsG = 4.0,
        aliases = listOf("bechamel", "béchamel", "white sauce"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("lait", "beurre", "farine de blé")),
    FoodEntry("sauce pesto",       450.0, 5.0,  6.0, 45.0, 2.0, saltG = 2.0, saturatedFatG = 7.0, sugarsG = 2.0,
        aliases = listOf("pesto"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("basilic", "huile d'olive", "parmesan", "pignons")),
    FoodEntry("sauce au fromage",  160.0, 6.0,  6.0, 12.0, 0.2, saltG = 1.0, saturatedFatG = 7.5, sugarsG = 3.5,
        aliases = listOf("sauce fromage", "cheese sauce", "4 fromages", "quatre fromages"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("crème", "fromage", "lait")),
    FoodEntry("sauce au curry",    120.0, 2.0,  9.0, 8.5, 1.5, saltG = 1.0, saturatedFatG = 5.0, sugarsG = 5.0,
        aliases = listOf("sauce curry", "curry sauce"), category = ProductCategory.CONDIMENT,
        typicalIngredients = listOf("lait de coco", "oignon", "épices")),

    // Composed dishes, as eaten
    FoodEntry("spaghetti bolognaise", 130.0, 6.5, 15.0, 4.5, 1.5, saltG = 0.7, saturatedFatG = 1.6, sugarsG = 2.5,
        aliases = listOf("pâtes à la bolognaise", "pâtes bolognaise", "spaghetti à la bolognaise", "spaghettis bolognaise",
            "spaghetti bolognese", "pasta bolognese", "tagliatelles bolognaise"),
        typicalPortionG = 350.0, category = ProductCategory.READY_MEAL,
        typicalIngredients = listOf("pâtes de blé dur", "viande de boeuf hachée", "tomate", "oignon")),
    FoodEntry("pâtes carbonara",   170.0, 7.0, 17.0, 8.5, 1.0, saltG = 0.9, saturatedFatG = 4.0, sugarsG = 1.5,
        aliases = listOf("pâtes à la carbonara", "spaghetti carbonara", "tagliatelles carbonara", "pasta carbonara"),
        typicalPortionG = 350.0, category = ProductCategory.READY_MEAL,
        typicalIngredients = listOf("pâtes de blé dur", "crème", "lardons de porc", "oeuf", "fromage")),
    FoodEntry("hachis parmentier", 120.0, 6.5, 10.0, 6.0, 1.2, saltG = 0.7, saturatedFatG = 2.8, sugarsG = 1.5,
        aliases = listOf("parmentier", "shepherd's pie", "cottage pie"), typicalPortionG = 300.0, category = ProductCategory.READY_MEAL,
        typicalIngredients = listOf("pomme de terre", "viande de boeuf hachée", "lait", "beurre")),
    FoodEntry("riz cantonais",     160.0, 5.0, 25.0, 4.5, 1.0, saltG = 0.8, saturatedFatG = 1.0, sugarsG = 1.0,
        aliases = listOf("riz frit", "fried rice"), typicalPortionG = 300.0, category = ProductCategory.READY_MEAL,
        typicalIngredients = listOf("riz", "oeuf", "jambon", "petits pois", "sauce soja")),
    FoodEntry("blanquette de veau",125.0, 10.0, 4.0, 7.5, 0.5, saltG = 0.8, saturatedFatG = 3.5, sugarsG = 1.0,
        aliases = listOf("blanquette"), typicalPortionG = 300.0, category = ProductCategory.READY_MEAL,
        typicalIngredients = listOf("veau", "crème", "champignons", "farine de blé")),
)
