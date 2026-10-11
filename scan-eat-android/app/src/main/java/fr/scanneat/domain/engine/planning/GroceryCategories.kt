package fr.scanneat.domain.engine.planning

import fr.scanneat.domain.engine.nutrition.wordBoundaryMatch

// ============================================================================
// GROCERY AISLE CATEGORIES — keyword-based classifier for GroceryScreen's
// "group by aisle" toggle. aggregateGroceryList() only ever produced one flat
// alphabetical/unsorted list with no produce/dairy/pantry sectioning at all.
//
// Purely organizational (not health/nutrition-relevant like OFF/CIQUAL data),
// so a keyword miss just means an item lands in OTHER instead of the wrong
// aisle - low cost to get approximately right, unlike fabricating nutrient
// content values.
// ============================================================================

enum class GroceryCategory { PRODUCE, DAIRY, MEAT_FISH, BAKERY, FROZEN, BEVERAGES, PANTRY, OTHER }

/** Checked in this order — first keyword match wins. Keys are matched against normalizeKey(name). */
private val CATEGORY_KEYWORDS: List<Pair<GroceryCategory, List<String>>> = listOf(
    GroceryCategory.PRODUCE to listOf(
        "pomme", "banane", "orange", "fraise", "myrtille", "framboise", "avocat", "kiwi", "poire",
        "tomate", "carotte", "oignon", "ail", "echalote", "poireau", "pomme de terre", "patate",
        "concombre", "courgette", "aubergine", "poivron", "chou", "brocoli", "epinard", "haricot vert",
        "salade", "laitue", "celeri", "champignon", "citron", "mandarine", "raisin", "peche", "abricot",
        "melon", "pasteque", "ananas", "mangue", "fenouil", "radis", "betterave", "artichaut", "asperge",
        "petit pois", "persil", "basilic", "menthe", "coriandre", "gingembre", "herbe",
        "apple", "banana", "strawberry", "blueberry", "avocado", "tomato", "carrot", "onion", "garlic",
        "leek", "potato", "cucumber", "zucchini", "eggplant", "pepper", "cabbage", "spinach", "broccoli",
        "lettuce", "celery", "mushroom", "lemon", "lime", "grape", "peach", "apricot", "melon", "mango",
        "radish", "beet", "artichoke", "asparagus", "parsley", "basil", "mint", "cilantro", "ginger",
    ),
    GroceryCategory.DAIRY to listOf(
        "lait", "creme", "beurre", "yaourt", "yogourt", "fromage", "mozzarella", "parmesan",
        "cheddar", "chevre", "camembert", "gruyere", "emmental", "feta", "ricotta",
        "milk", "cream", "butter", "yogurt", "cheese",
    ),
    GroceryCategory.MEAT_FISH to listOf(
        "boeuf", "veau", "porc", "agneau", "poulet", "canard", "dinde", "jambon", "bacon", "lardons",
        "saucisse", "saucisson", "steak", "viande", "oeuf", "œuf",
        "saumon", "thon", "crevette", "crabe", "homard", "anchois", "poisson", "coquille",
        "beef", "veal", "pork", "lamb", "chicken", "duck", "turkey", "ham", "sausage", "egg",
        "salmon", "tuna", "shrimp", "crab", "lobster", "anchovy", "fish",
    ),
    GroceryCategory.BAKERY to listOf(
        "pain", "baguette", "brioche", "croissant", "farine", "levure", "biscotte", "viennoiserie",
        "bread", "flour", "yeast", "pastry", "bun",
    ),
    GroceryCategory.FROZEN to listOf(
        "surgele", "surgelee", "glace", "sorbet",
        "frozen", "ice cream",
    ),
    GroceryCategory.BEVERAGES to listOf(
        // "the" (tea), not "the " with a trailing-space hack - that manual boundary
        // guard is now redundant with (and weaker than) wordBoundaryMatch() below,
        // which correctly treats end-of-string as a boundary too, unlike the old
        // hack (which required a literal trailing space, missing "the" as the very
        // last word in a product name).
        "eau", "jus", "vin", "biere", "cafe", "the", "soda", "limonade",
        "water", "juice", "wine", "beer", "coffee", "tea", "soda",
    ),
    GroceryCategory.PANTRY to listOf(
        "riz", "pate", "pates", "lentille", "pois chiche", "sucre", "sel", "huile", "vinaigre",
        "epice", "cereale", "avoine", "flocon", "miel", "confiture", "chocolat", "cacao",
        "conserve", "bouillon", "sauce soja", "moutarde",
        "rice", "pasta", "lentil", "chickpea", "sugar", "salt", "oil", "vinegar", "spice",
        "cereal", "oat", "honey", "jam", "chocolate", "cocoa", "broth", "mustard",
    ),
)

// First-match-wins on single keywords misfiles compound items whose *form* decides the aisle:
// "jus d'orange" matched "orange" (Produce), "sauce tomate" / "tomates pelees en boite" matched
// "tomate" (Produce), "beurre de cacahuete" matched "beurre" (Dairy). These whole-phrase
// overrides run before the keyword scan. Cooking juices ("jus de viande") are not drinks.
private val CATEGORY_OVERRIDES: List<Pair<GroceryCategory, Regex>> = listOf(
    GroceryCategory.BEVERAGES to Regex(
        """\b(?:jus(?!\s+(?:de\s+|d')?(?:viande|cuisson|boeuf|veau|volaille|poulet|roti))|juice|sirop|smoothie)\b""",
    ),
    GroceryCategory.PANTRY to Regex(
        """\b(?:beurre|butter)\s+d[e']\s*(?:cacahuete|arachide|amande|noisette|cajou|cacao|karite)""" +
            """|\b(?:peanut|almond|cashew|hazelnut|cocoa|shea)\s+butter""" +
            """|\b(?:sauce|coulis|concentre|ketchup|conserve|boite|dried|canned|tinned)\b""" +
            // "sec" only after a plant noun: "raisins secs" / "tomates sechees" are pantry items, but
            // "vin sec" (beverage) and "saucisson sec" (charcuterie) must keep their own aisle.
            """|\b(?:fruits?|abricots?|raisins?|tomates?|champignons?|figues?|dattes?|pruneaux|herbes?|legumes?|haricots?|poivrons?|piments?)\s+(?:secs?|seche[es]?s?)\b""",
    ),
)

// Word-boundary match, not a raw substring - unbounded key.contains(it) let an
// earlier-checked category's short keyword false-positive-match inside an unrelated
// word: "vinaigre" (vinegar, PANTRY) starts with "vin" (wine, BEVERAGES, checked
// first), and "veau" (veal) contains "eau" (water, BEVERAGES) - so vinegar and veal
// both landed in the Beverages aisle instead of Pantry/Meat & Fish. Same fix already
// applied to AllergenDetector/DietChecker/IngredientMatcher for this exact bug class.
fun groceryCategoryFor(name: String): GroceryCategory {
    val key = normalizeKey(name)
    for ((category, pattern) in CATEGORY_OVERRIDES) {
        if (pattern.containsMatchIn(key)) return category
    }
    for ((category, keywords) in CATEGORY_KEYWORDS) {
        // Plural forms too ("tomates", "oeufs", "choux") - the keyword list is singular.
        if (keywords.any { wordBoundaryMatch(key, it) || wordBoundaryMatch(key, it + "s") || wordBoundaryMatch(key, it + "x") }) return category
    }
    return GroceryCategory.OTHER
}
