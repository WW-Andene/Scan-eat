package fr.scanneat.domain.engine.planning

// ============================================================================
// CULINARY FOOD GROUPS — a lightweight ingredient classifier used purely to
// diversify findPairings() results, not to score/estimate nutrition (that's
// FoodDb.kt/MicronutrientEstimator's job with real per-ingredient data).
//
// Basis: USDA MyPlate's five food groups (Fruits, Vegetables, Grains,
// Protein, Dairy — choosemyplate.gov) and the equivalent French PNNS
// "assiette équilibrée" visual-plate guide (Programme National Nutrition
// Santé), both widely-cited professional plate-balance references: a
// well-composed dish/meal draws from *different* groups rather than
// stacking two members of the same one. Oils/fats and herbs/spices are
// added as two extra groups since MyPlate treats them as separate from its
// five core ones, and they're common, distinct-enough flavor-pairing
// candidates in their own right.
//
// User-reported: flavor-network co-occurrence data (Ahn et al., already
// used for ranking) is real but category-blind - "riz" correctly pairs
// with "macaronis" in raw co-occurrence data (both show up in composite
// dishes together often enough), but suggesting a second starch for a
// recipe that already has one (riz) is bad culinary advice, the same way
// suggesting butter for a dish that already has yaourt (both dairy fat) is.
// This file's classifyFoodGroup() + PairingsDb.kt's diversifyByFoodGroup()
// let a caller that knows the whole dish deprioritize (not hard-remove -
// data is sparse enough that a same-group suggestion is still shown if
// nothing better ranks higher) suggestions from a group already present.
// ============================================================================

enum class FoodGroup { PROTEIN, GRAIN_STARCH, VEGETABLE, FRUIT, DAIRY_FAT, HERB_SPICE, SWEET, OTHER }

// Keyword-substring matching against the PAIRINGS dataset's own English keys
// (underscores replaced with spaces) rather than an exhaustive per-ingredient
// lookup table - the flavor-network dataset spans 300+ distinct ingredients
// across PairingsDataAtoC/CtoL/LtoR/RtoZ.kt, too many to hand-tag one by one
// at useful accuracy; common English food-word roots cover the large
// majority of real queries without that maintenance burden. Order matters -
// checked top to bottom, first match wins, so a more specific keyword
// (e.g. "peanut butter") should be listed before a more general one
// ("butter") if that ever becomes ambiguous.
private val GROUP_KEYWORDS: List<Pair<FoodGroup, List<String>>> = listOf(
    FoodGroup.PROTEIN to listOf(
        "chicken", "beef", "pork", "lamb", "veal", "turkey", "duck", "bacon", "ham", "sausage",
        "fish", "salmon", "tuna", "cod", "shrimp", "prawn", "crab", "lobster", "mussel", "oyster", "squid", "octopus",
        "egg", "tofu", "tempeh", "seitan", "lentil", "chickpea", "bean", "pea",
    ),
    FoodGroup.GRAIN_STARCH to listOf(
        "rice", "pasta", "spaghetti", "macaroni", "noodle", "bread", "toast", "baguette", "bun", "roll",
        "potato", "corn", "maize", "oat", "wheat", "flour", "cereal", "couscous", "quinoa", "barley",
        "polenta", "tortilla", "cracker", "pastry", "dough",
    ),
    FoodGroup.DAIRY_FAT to listOf(
        "milk", "cream", "butter", "cheese", "yogurt", "yoghurt", "custard", "ghee",
        "oil", "olive_oil", "margarine", "mayonnaise", "lard",
    ),
    FoodGroup.FRUIT to listOf(
        "apple", "pear", "banana", "orange", "lemon", "lime", "grapefruit", "berry", "strawberry",
        "raspberry", "blueberry", "blackberry", "cherry", "grape", "peach", "apricot", "plum", "mango",
        "pineapple", "melon", "watermelon", "fig", "date", "kiwi", "pomegranate", "coconut",
    ),
    FoodGroup.HERB_SPICE to listOf(
        "pepper", "chili", "chilli", "cinnamon", "clove", "nutmeg", "ginger", "garlic", "basil",
        "thyme", "rosemary", "oregano", "parsley", "mint", "cumin", "paprika", "saffron", "vanilla",
        "cardamom", "coriander", "cilantro", "dill", "sage", "bay_leaf", "mustard", "curry", "chive",
    ),
    FoodGroup.SWEET to listOf(
        "sugar", "honey", "chocolate", "cocoa", "caramel", "syrup", "jam", "marmalade", "candy",
    ),
    FoodGroup.VEGETABLE to listOf(
        "onion", "tomato", "carrot", "broccoli", "spinach", "cucumber", "zucchini", "courgette",
        "pepper_bell", "cabbage", "lettuce", "salad", "leek", "celery", "asparagus", "mushroom",
        "eggplant", "aubergine", "beet", "radish", "artichoke", "squash", "pumpkin", "avocado",
        "cauliflower", "sprout", "fennel", "turnip", "parsnip", "kale",
    ),
)

/**
 * Classifies a PAIRINGS-dataset English key (underscored, e.g. "beef_broth")
 * into a rough culinary food group via keyword match — [FoodGroup.OTHER] on
 * no match (sauces, condiments, prepared dishes, and anything this keyword
 * list doesn't cover fall here, which is the safe default: OTHER is never
 * treated as "already represented" by [PairingsDb.diversifyByFoodGroup], so
 * an unclassified ingredient is never wrongly deprioritized).
 */
fun classifyFoodGroup(englishKey: String): FoodGroup {
    val normalized = englishKey.lowercase().replace('_', ' ')
    for ((group, keywords) in GROUP_KEYWORDS) {
        if (keywords.any { normalized.contains(it.replace('_', ' ')) }) return group
    }
    return FoodGroup.OTHER
}
