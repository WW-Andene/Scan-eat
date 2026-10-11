package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// FOOD DATABASE — cooking staples (flour, salt, baking aids, stock, cream,
// condiments, herbs...). Added so recipe ingredient lines imported from a link
// or a photo (IngredientLineParser) match a real entry instead of falling back
// to unstructured notes. Per 100 g; see FoodDb.kt for the CIQUAL provenance
// notice (hand-rounded CIQUAL 2020 values).
// ============================================================================

internal val FOOD_DB_COOKING_STAPLES: List<FoodEntry> = listOf(
    // Farines, amidons, levures
    FoodEntry("farine de blé", 352.0, 10.3, 73.6, 1.1, 3.0, saturatedFatG = 0.2, sugarsG = 1.0, aliases = listOf("farine", "farine T55", "farine T45", "flour", "wheat flour"), category = ProductCategory.GRAIN),
    FoodEntry("farine complète", 335.0, 12.0, 63.0, 2.0, 10.0, saturatedFatG = 0.3, sugarsG = 1.4, aliases = listOf("farine T150", "farine T110", "wholemeal flour"), category = ProductCategory.GRAIN),
    FoodEntry("fécule de maïs", 365.0, 0.3, 91.0, 0.1, 0.9, aliases = listOf("maïzena", "maizena", "cornstarch", "amidon de maïs"), category = ProductCategory.GRAIN),
    FoodEntry("levure chimique", 92.0, 0.1, 22.0, 0.1, 0.0, saltG = 26.0, aliases = listOf("poudre à lever", "baking powder"), category = ProductCategory.CONDIMENT),
    FoodEntry("levure boulangère", 325.0, 40.0, 41.0, 7.6, 27.0, saltG = 0.1, aliases = listOf("levure de boulanger", "yeast"), category = ProductCategory.CONDIMENT),
    FoodEntry("gélatine", 335.0, 86.0, 0.0, 0.1, 0.0, saltG = 0.5, aliases = listOf("gelatin", "feuilles de gélatine"), category = ProductCategory.CONDIMENT),

    // Sucres
    FoodEntry("sucre glace", 398.0, 0.0, 99.5, 0.0, 0.0, sugarsG = 97.0, aliases = listOf("icing sugar", "powdered sugar"), category = ProductCategory.SNACK_SWEET),
    FoodEntry("cassonade", 390.0, 0.1, 97.0, 0.0, 0.0, sugarsG = 97.0, aliases = listOf("sucre roux", "sucre brun", "brown sugar"), category = ProductCategory.SNACK_SWEET),
    FoodEntry("sucre vanillé", 398.0, 0.0, 99.5, 0.0, 0.0, sugarsG = 99.5, aliases = listOf("vanilla sugar"), category = ProductCategory.SNACK_SWEET),
    FoodEntry("cacao en poudre", 354.0, 20.0, 14.0, 21.0, 33.0, saturatedFatG = 13.0, sugarsG = 1.0, aliases = listOf("cacao", "cocoa powder", "cacao non sucré"), category = ProductCategory.SNACK_SWEET),
    FoodEntry("raisins secs", 300.0, 3.1, 69.0, 0.5, 4.0, sugarsG = 60.0, potassiumMg = 750.0, aliases = listOf("raisins de corinthe", "raisins secs blonds"), category = ProductCategory.FRESH_PRODUCE),

    // Crèmes et fromages de cuisine
    FoodEntry("crème liquide", 292.0, 2.3, 3.0, 30.0, 0.0, saturatedFatG = 19.5, sugarsG = 3.0, aliases = listOf("crème entière", "crème fleurette", "cream", "heavy cream", "crème liquide entière"), category = ProductCategory.CHEESE),

    // Charcuterie de cuisine
    FoodEntry("lardons", 300.0, 15.0, 0.5, 26.0, 0.0, saturatedFatG = 9.5, saltG = 2.5, aliases = listOf("lardons fumés", "bacon bits"), category = ProductCategory.PROCESSED_MEAT),

    // Condiments, liquides de cuisson
    FoodEntry("sel", 0.0, 0.0, 0.0, 0.0, 0.0, saltG = 100.0, aliases = listOf("sel fin", "gros sel", "fleur de sel", "salt"), category = ProductCategory.CONDIMENT),
    FoodEntry("poivre", 255.0, 10.0, 39.0, 3.3, 25.0, aliases = listOf("poivre noir", "pepper", "black pepper"), category = ProductCategory.CONDIMENT),
    FoodEntry("moutarde", 154.0, 7.0, 4.5, 11.0, 4.0, saturatedFatG = 0.7, sugarsG = 2.0, saltG = 6.0, aliases = listOf("moutarde de dijon", "mustard"), category = ProductCategory.CONDIMENT),
    FoodEntry("vinaigre", 19.0, 0.0, 0.6, 0.0, 0.0, aliases = listOf("vinaigre de vin", "vinaigre balsamique", "vinegar"), category = ProductCategory.CONDIMENT),
    FoodEntry("concentré de tomate", 82.0, 4.3, 15.0, 0.5, 4.1, sugarsG = 12.0, saltG = 0.15, aliases = listOf("double concentré de tomate", "tomato paste"), category = ProductCategory.CONDIMENT),
    FoodEntry("bouillon", 5.0, 0.4, 0.4, 0.2, 0.0, saltG = 0.9, aliases = listOf("bouillon de volaille", "bouillon de légumes", "fond de volaille", "stock", "broth"), category = ProductCategory.SOUP),
    FoodEntry("vin blanc", 70.0, 0.1, 2.6, 0.0, 0.0, sugarsG = 1.0, aliases = listOf("white wine"), category = ProductCategory.ALCOHOLIC_BEVERAGE),
    FoodEntry("vin rouge", 85.0, 0.1, 2.6, 0.0, 0.0, sugarsG = 0.6, aliases = listOf("red wine"), category = ProductCategory.ALCOHOLIC_BEVERAGE),

    // Aromates et épices
    FoodEntry("échalote", 72.0, 2.5, 15.0, 0.1, 3.2, sugarsG = 7.9, aliases = listOf("echalote", "shallot"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("persil", 43.0, 3.0, 5.0, 0.8, 4.0, vitCMg = 133.0, vitKUg = 1640.0, aliases = listOf("persil plat", "parsley"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("basilic", 23.0, 3.2, 2.7, 0.6, 1.6, aliases = listOf("basil"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("thym", 101.0, 5.6, 24.0, 1.7, 14.0, aliases = listOf("thyme"), category = ProductCategory.FRESH_PRODUCE),
    FoodEntry("cumin", 375.0, 18.0, 44.0, 22.0, 11.0, aliases = listOf("cumin moulu"), category = ProductCategory.CONDIMENT),
    FoodEntry("cannelle", 247.0, 4.0, 81.0, 1.2, 53.0, aliases = listOf("cinnamon"), category = ProductCategory.CONDIMENT),
    FoodEntry("curry", 325.0, 14.0, 56.0, 14.0, 53.0, aliases = listOf("curry en poudre", "curry powder"), category = ProductCategory.CONDIMENT),
).map { it.copy(cookingIngredient = true) }
