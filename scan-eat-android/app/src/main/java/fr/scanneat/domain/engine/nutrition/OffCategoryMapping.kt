package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.inferCategoryFromName
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// OFF CATEGORY MAPPING — split out of OffMapper.kt: maps OFF's raw category
// tags to our domain ProductCategory, and separately flags non-food products.
// ============================================================================

/** The category group whose thresholds are most skewed by a wrong
 *  assignment - all four assume near-zero solid-food kcal (0-50 for soft
 *  drinks, up to ~280 for the widest, ALCOHOLIC_BEVERAGE), so a mistagged
 *  solid/semi-solid food landing here gets an energy/sugar/salt verdict that
 *  can be off by an order of magnitude. See [resolveCategory]'s own doc comment. */
private val BEVERAGE_CATEGORIES = setOf(
    ProductCategory.BEVERAGE_SOFT, ProductCategory.BEVERAGE_JUICE,
    ProductCategory.BEVERAGE_WATER, ProductCategory.ALCOHOLIC_BEVERAGE,
)

/**
 * User-reported: a mustard/celery condiment ("CELERI MOUTARDE") scanned via
 * barcode came back tagged into a beverage category on Open Food Facts
 * (crowd-sourced, error-prone category tags - this is a real, observed
 * upstream data issue, not a keyword-matching bug in [mapCategory] itself)
 * and was scored entirely against soda-shaped 0-50kcal/100g thresholds - a
 * completely wrong verdict for what the product's own name plainly says it
 * is. [mapCategory]'s tag-based result previously had zero cross-check
 * against the product's own name unless it landed in OTHER outright; a
 * confidently wrong non-OTHER category (like this one) passed straight
 * through untouched.
 *
 * Narrowly scoped rather than a general "name always wins" override (OFF's
 * structured tags are usually more reliable than a keyword regex run over a
 * free-text name, and second-guessing them broadly would trade this one bug
 * for a worse one): only overrides when OFF's own category is specifically
 * a beverage - the group most distorted by a wrong assignment - AND the
 * product's name confidently matches a real, different, non-beverage
 * category via the exact same [inferCategoryFromName] patterns already
 * trusted as the OTHER-fallback below. A product OFF genuinely tagged as a
 * beverage, or one whose name doesn't clearly say otherwise, is untouched.
 */
internal fun resolveCategory(tags: List<String>?, name: String): ProductCategory {
    val offCategory = mapCategory(tags)
    if (offCategory == ProductCategory.OTHER) return inferCategoryFromName(name)
    if (offCategory !in BEVERAGE_CATEGORIES) return offCategory
    val nameCategory = inferCategoryFromName(name)
    // A fruit named on a drink ("Twist Pêche", "Orange - Carotte") is a flavour,
    // not evidence that OFF mis-tagged a solid food as a beverage.
    val overridable = nameCategory != ProductCategory.OTHER && nameCategory !in BEVERAGE_CATEGORIES &&
        nameCategory != ProductCategory.FRESH_PRODUCE && nameCategory != ProductCategory.DRIED_FRUIT
    return if (overridable) nameCategory else offCategory
}

/**
 * Score-engine audit S1: OFF's `categories_tags` is the FULL hierarchy, from
 * the most general parent to the most specific leaf (e.g. bread =
 * "en:plant-based-foods-and-beverages", "en:cereals-and-potatoes", …,
 * "en:breads"). The previous implementation joined every tag into one string
 * and searched it for substrings in a fixed order, so generic parents decided
 * the category: "cereals-and-potatoes" made every bread/rice/pasta/flour/crisp
 * a BREAKFAST_CEREAL, "plant-based-foods-and-beverages" made olive oils,
 * margarines and canned vegetables a BEVERAGE_SOFT, "prepared-meats" made every
 * ham a FRESH_MEAT, "breaded-fish" made fish sticks a BREAD. Measured on the 10
 * most-scanned French products of 39 OFF categories (see Roadmap.md, S1).
 *
 * Now: walk the tags from the most specific to the most general, skip the
 * umbrella parents that say nothing about the product's nutrition, and return
 * the first tag that a rule recognizes. Each rule only ever looks at ONE tag,
 * matched on whole "-"-separated words, never on the concatenation.
 */
internal fun mapCategory(tags: List<String>?): ProductCategory {
    if (tags.isNullOrEmpty()) return ProductCategory.OTHER
    // Only normalized English or French taxonomy slugs: the tail of the list
    // often holds free-text or importer tags ("Vegetables - Prepared/Processed
    // (Shelf Stable)", "en:Pâtes à tartiner") that are neither normalized nor
    // reliable, and other languages would need their own keyword lists.
    val slugs = tags.asSequence()
        .filter { it.startsWith("en:") || it.startsWith("fr:") }
        .map { it.substring(3) }
        .filter { TAXONOMY_SLUG.matches(it) }
        .toList()
    // A "0.0"/alcohol-free beer or wine is tagged both "beers" and
    // "non-alcoholic-beers", in no reliable order - the absence of alcohol wins.
    if (slugs.any { "non-alcoholic-beer" in it || "non-alcoholic-wine" in it || "alcohol-free" in it }) return ProductCategory.BEVERAGE_SOFT
    // Same for breakfast cereals: their leaf tag is often only the shape or
    // flavour ("chocolate-wheat-shells") while "breakfast-cereals" is reliable.
    if ("breakfast-cereals" in slugs) return ProductCategory.BREAKFAST_CEREAL
    for (slug in slugs.asReversed()) {
        if (slug in UMBRELLA_CATEGORY_TAGS || ORIGIN_OR_PROPERTY_TAG.containsMatchIn(slug)) continue
        categoryForTag(slug)?.let { return it }
    }
    return ProductCategory.OTHER
}

private val TAXONOMY_SLUG = Regex("[a-z0-9]+(-[a-z0-9]+)*")

/** Origin ("french-fruits", "cheeses-from-england") and nutrition-claim
 *  ("products-with-reduced-salt", "light-…") tags describe a property, not
 *  what the product is - they would otherwise outrank the real leaf tag. */
private val ORIGIN_OR_PROPERTY_TAG = Regex(
    "^(french|italian|spanish|belgian|swiss|german|english|british|organic|light|products)-|-from-|reduced-|less-salt|with-less-|low-salt|no-added-|without-",
)

/** Parent or property tags shared by nutritionally unrelated products - never decisive. */
private val UMBRELLA_CATEGORY_TAGS = setOf(
    "plant-based-foods-and-beverages", "plant-based-foods", "beverages-and-beverages-preparations",
    "cereals-and-potatoes", "cereals-and-their-products", "fruits-and-vegetables-based-foods",
    "vegetable-based-foods-and-beverages", "fruit-based-foods-and-beverages", "snacks", "breakfasts",
    "fresh-foods", "refrigerated-foods", "frozen-foods", "canned-foods", "dried-products", "fermented-foods",
    "farming-products", "groceries", "foods", "spreads", "dairies", "animal-products", "meat-based-products",
    "fish-and-meat-and-eggs", "products-for-specific-diets", "dietary-supplements", "fruits-based-foods",
    "vegetables-based-foods", "legumes-and-their-products", "nuts-and-their-products", "seafood-products",
    "sweeteners", "plant-based-spreads", "salted-spreads", "sweetened-beverages", "unsweetened-beverages",
    "non-alcoholic-beverages", "beverage-preparations", "instant-beverages", "pasteurised-products",
    "desserts", "frozen-plant-based-foods", "canned-plant-based-foods", "dried-plant-based-foods",
    "freeze-dried-foods", "plant-based-beverages", "fruit-based-beverages", "carbonated-drinks",
)

/** True when [this] slug contains one of [words] as a whole "-"-separated word
 *  (plural "s"/"es" tolerated): word "bread" matches "sliced-breads" but not
 *  "breaded-fish". Multi-word entries ("ice-cream") are matched as a run. */
private fun String.hasWord(vararg words: String): Boolean {
    val padded = "-$this-"
    return words.any { w -> padded.contains("-$w-") || padded.contains("-${w}s-") || padded.contains("-${w}es-") }
}

private fun categoryForTag(t: String): ProductCategory? = when {
    // Substitutes first: they carry the word of the product they imitate.
    t.hasWord("tofu", "tempeh", "seitan", "cheese-substitute", "vegan-cheese", "meat-alternative", "meat-analogue",
        "meat-substitute", "fish-alternative", "fish-analogue") -> ProductCategory.PLANT_BASED_ALTERNATIVE
    // Peanut "butter" is a legume/oilseed purée, not a fat and not a legume dish.
    t.hasWord("peanut-butter", "nut-butter", "legume-butter", "oilseed-puree", "almond-butter", "nut-spread") -> ProductCategory.NUTS_SEEDS
    t.hasWord("sauce", "vinegar", "vinaigre", "moutarde", "epice", "dressing", "vinaigrette", "mayonnaise", "ketchup", "mustard", "pesto", "hummus",
        "tapenade", "condiment", "spice", "herb", "seasoning") &&
        !t.hasWord("with", "crisps", "chips", "cracker", "fish", "lentil", "bean", "pizza") ||
        t == "salts" || t == "table-salts" || t == "sea-salts" -> ProductCategory.CONDIMENT
    t.hasWord("sandwich", "burger", "wrap") && !t.hasWord("biscuit", "cookie", "wafer") -> ProductCategory.SANDWICH
    t.hasWord("pizza", "quiche", "lasagna", "lasagne", "meal", "dish", "gratin", "paella", "tartiflette", "moussaka",
        "prepared-salad", "ratatouille", "pie", "ravioli", "tortellini", "stuffed-pasta") && !t.hasWord("lasagna-sheet", "egg-pasta", "pizza-sauce", "apple-pie", "sweet-pie", "sweet-pastries-and-pie", "fruit-pie") -> ProductCategory.READY_MEAL
    t.hasWord("breakfast-cereal", "muesli", "granola", "porridge", "cereal-flake", "rolled-oat", "oat-flake",
        "puffed-cereal", "cornflake", "corn-flake", "extruded-cereal", "cereal-cluster") ||
        t.hasWord("breakfast") && t.hasWord("cereal") -> ProductCategory.BREAKFAST_CEREAL
    // Compotes "to drink" (gourdes) are still fruit purée, not a soft drink.
    t.hasWord("compote", "applesauce") -> ProductCategory.FRESH_PRODUCE
    t.hasWord("iced-tea", "ice-tea", "iced-coffee", "coffee-drink", "tea-based-beverage", "flavoured-water",
        "flavored-water", "coconut-water") -> ProductCategory.BEVERAGE_SOFT
    // Coffee/tea before milk: "instant-powder-for-coffee-with-milk" is coffee.
    t.hasWord("coffee", "tea", "herbal-tea", "cafe", "thes", "the-vert", "the-noir", "chicoree", "infusion", "tisane", "espresso", "chicory", "coffee-substitute") -> ProductCategory.COFFEE_TEA
    t.hasWord("ice-cream", "sorbet", "frozen-dessert", "frozen-yogurt", "ice-pop") -> ProductCategory.ICE_CREAM
    t.hasWord("yogurt", "yoghurt", "yaourt", "skyr", "kefir", "fromage-blanc", "fromages-blancs", "petit-suisse", "fermented-milk",
        "fermented-dairy-dessert", "dairy-dessert", "cream-dessert") -> ProductCategory.YOGURT
    t.hasWord("plant-based-milk-alternative", "plant-milk", "milk", "lait", "milk-powder", "based-drink", "dairy-drink") &&
        !t.hasWord("chocolate", "cocoa", "flavoured", "flavored", "fermented-milk", "milk-chocolate", "coconut", "bread", "roll",
            "wine", "beer", "alcoholic") -> ProductCategory.MILK
    t.hasWord("cheesecake") -> ProductCategory.SNACK_SWEET
    t.hasWord("cheese", "fromage") -> ProductCategory.CHEESE
    t.hasWord("soup", "broth", "bouillon", "gazpacho", "veloute") -> ProductCategory.SOUP
    t.hasWord("ham", "jambon", "saucisse", "sausage", "saucisson", "salami", "bacon", "chorizo", "charcuterie", "rillette", "terrine", "lardon",
        "merguez", "pepperoni", "prosciutto", "prepared-meat", "processed-meat", "cured-meat", "cooked-meat", "foie-gras",
        "dry-sausage", "meat-pate", "liver-pate", "pork-prepared", "prepared-pork-meat", "cooked-ham", "white-ham") || t == "pates" || t.endsWith("-pates") -> ProductCategory.PROCESSED_MEAT
    t.hasWord("fish", "seafood", "salmon", "tuna", "sardine", "mackerel", "herring", "cod", "shrimp", "prawn", "crustacean",
        "mollusc", "mussel", "oyster", "surimi", "anchovy", "anchovies", "fish-stick", "fish-finger") -> ProductCategory.FISH
    t.hasWord("egg") && !t.hasWord("egg-pasta", "egg-noodle", "chocolate", "easter") -> ProductCategory.EGG
    t.hasWord("meat", "beef", "pork", "veal", "lamb", "chicken", "poultry", "poultries", "turkey", "duck", "rabbit",
        "steak", "ground-beef", "minced-meat", "meat-patty", "meat-patties") -> ProductCategory.FRESH_MEAT
    t.hasWord("oil", "huile", "beurre", "margarine", "butter", "lard", "ghee", "vegetable-fat", "animal-fat", "spreadable-fat") &&
        !t.hasWord("cocoa", "shea", "peanut") ||
        t.hasWord("fat") && !t.hasWord("with", "low-fat", "fat-free", "fatty", "cocoa") -> ProductCategory.OIL_FAT
    t.hasWord("honey", "miel", "confiture", "jam", "marmalade", "fruit-preserve", "fruit-spread", "jelly-spread") -> ProductCategory.SPREAD_SWEET
    t.hasWord("biscuit", "cookie", "chocolate", "cake", "pastry", "pastries", "viennoiserie", "candy", "candies",
        "confectionery", "confectioneries", "confectionary", "gingerbread", "waffle", "wafer", "crepe", "muffin",
        "brownie", "cocoa", "sweet-snack", "dessert-cream") -> ProductCategory.SNACK_SWEET
    t.hasWord("chips", "crisps", "cracker", "popcorn", "pretzel", "olive", "appetizer", "salty-snack") &&
        !t.hasWord("crispbread") -> ProductCategory.SNACK_SALTY
    t.hasWord("lentil", "lentille", "pois-chiche", "chickpea", "legume", "pulse", "bean", "split-pea", "legume-seed") &&
        !t.hasWord("green-bean", "coffee-bean", "cocoa-bean", "jelly-bean", "vanilla-bean") -> ProductCategory.LEGUME
    t.hasWord("dried-fruit", "raisin", "prune", "dried-prune", "date", "dried-apricot", "dried-fig", "dried-cranberries") -> ProductCategory.DRIED_FRUIT
    t.hasWord("nut", "almond", "hazelnut", "walnut", "cashew", "cashew-nut", "pistachio", "peanut", "seed", "pecan",
        "macadamia", "shelled-nut") && !t.hasWord("doughnut", "coconut", "nutmeg") -> ProductCategory.NUTS_SEEDS
    t.hasWord("alcoholic-beverage", "beer", "biere", "vin", "cidre", "wine", "cider", "spirit", "liquor", "liqueur", "whisky", "whiskey",
        "whiskies", "vodka", "rum", "champagne", "gin", "vermouth", "ale", "lager") &&
        !t.hasWord("gum", "candy", "candies", "confectionery", "vinegar") -> ProductCategory.ALCOHOLIC_BEVERAGE
    t.hasWord("juice", "jus", "nectar", "smoothie") -> ProductCategory.BEVERAGE_JUICE
    t.hasWord("water", "eau") -> ProductCategory.BEVERAGE_WATER
    t.hasWord("soda", "cola", "lemonade", "soft-drink", "energy-drink", "syrup", "beverage", "drink") -> ProductCategory.BEVERAGE_SOFT
    t.hasWord("bread", "pain", "baguette", "brioche", "toast", "rusk", "crispbread", "bun", "pita", "tortilla") -> ProductCategory.BREAD
    t.hasWord("rice", "riz", "pasta", "pastas", "pates-alimentaire", "farine", "noodle", "spaghetti", "penne", "macaroni", "gnocchi", "quinoa", "couscous",
        "semolina", "bulgur", "flour", "grain", "polenta", "buckwheat", "millet", "tagliatelle", "fusilli") -> ProductCategory.GRAIN
    t.hasWord("compote", "applesauce", "fruit", "vegetable", "potato", "apple", "banana", "tomato", "salad", "mushroom",
        "carrot", "spinach", "spinachs", "berry", "berries", "citrus", "orange", "pear", "grape", "green-bean", "green-pea",
        "mixed-vegetable") -> ProductCategory.FRESH_PRODUCE
    else -> null
}

/**
 * Best-effort "this probably isn't a food/beverage/supplement/medicine product
 * at all" signal from OFF's own category tags. mapCategory() above only knows
 * how to recognize specific FOOD sub-categories and silently buckets anything
 * else - including a lubricant, shampoo, or cigarette pack - into
 * ProductCategory.OTHER, which then runs through the exact same nutrition-based
 * scoring as a genuine "other" food product, producing a meaningless grade.
 *
 * Returns a NonConsumableCategory key (as a plain string - this file has no
 * dependency on the Android-only nonconsumable-lookup package, and the server
 * mirrors this exact function with no such package at all) or null when
 * nothing non-food-like matched.
 *
 * Deliberately conservative, same rule NonConsumableLookupDb's own curated
 * OPF snapshot already applies: any tag that also plausibly indicates a real
 * food/beverage/supplement/medicine wins over a non-food match - a missed
 * obscure non-food item is a minor imprecision, wrongly telling someone their
 * actual food isn't food would not be.
 *
 * [productName]/[brand] are an optional last-resort fallback for when OFF's
 * own tags don't classify it at all (missing/sparse tags, or - the case that
 * exposed this gap - tagged only in a language whose taxonomy id doesn't
 * contain any of the English substrings above, e.g. "fr:lubrifiants" or
 * "nl:glijmiddelen" for a lubricant instead of "en:lubricants"). Checked only
 * when the tag-based pass above found nothing, and only against a short list
 * of brand/keyword strings that are unambiguous in ANY context - a real food
 * product would never legitimately match "durex" or "glijmiddel" in its name.
 */
fun classifyNonFood(tags: List<String>?, productName: String? = null, brand: String? = null): String? {
    val tag = tags?.joinToString(" ") ?: ""
    if (tag.isNotEmpty()) {
        // Checked before the generic "looks like food" safety net below - pet food
        // literally contains the substring "food" (pet-food, cat-food, dog-food),
        // which would otherwise always disqualify it from ever being flagged here.
        if ("pet-food" in tag || "animal-feed" in tag || "cat-food" in tag || "dog-food" in tag) return "PET_SUPPLY"
        // Same substring-match bug already fixed for "non-alcoholic-beverage"
        // and "meat-alternative": OPF's own "en:non-food-products" tag (1331+
        // real products - batteries, cigarettes, cleaning products, coffee
        // filters, confirmed via live OPF data) contains "food" as a raw
        // substring, so looksLikeFood was true for EVERY one of them and this
        // whole function returned null before ever reaching the specific
        // battery/tobacco/cleaning-product matches (or even the "non-food" ->
        // OTHER fallback below, which was unreachable dead code as a result) -
        // an explicit "this isn't food" tag from the data source itself was
        // the one signal this safety net couldn't recognize.
        val looksLikeFood = "non-food" !in tag && listOf(
            "food", "beverage", "drink", "supplement", "dietary-supplement",
            "medicine", "medication", "meal", "snack", "dairy", "cereal",
        ).any { it in tag }
        if (looksLikeFood) return null
        val fromTags = when {
            "sex-toy" in tag || "lubricant" in tag || "lubrifiant" in tag || "glijmiddel" in tag -> "PERSONAL_CARE"
            "feminine-hygiene" in tag || "sanitary-protection" in tag ||
                "diaper" in tag || "baby-hygiene" in tag -> "HYGIENE_PRODUCT"
            // Added 13/08/2026 alongside the other hygiene additions below -
            // toilet paper was explicitly requested as part of "hygiène
            // intime" scope but had no tag or name match at all, so it fell
            // straight through to "product not found" like shampoo/
            // toothpaste did before this same fix. Deliberately routed to
            // the existing generic HYGIENE_PRODUCT hints (NonConsumableHints.kt)
            // rather than a new per-ingredient score - no citable primary
            // source was found for toilet-paper-specific claims (see
            // IntimateHygieneScore.kt's own header on why that was left out
            // rather than asserted unsourced), so recognition alone is the
            // honest fix here, not a fabricated fact.
            // OTHER, not HYGIENE_PRODUCT - correctness fix 13/08/2026 caught
            // during a category-consistency audit: NonConsumableHints.kt's
            // HYGIENE_PRODUCT fact says "superabsorbent polymers", true for
            // diapers/tampons/pads but FALSE for plain toilet paper - would
            // have shown users an inaccurate claim about the very product
            // this fix was meant to correctly recognize.
            "toilet-paper" in tag || "toilet-tissue" in tag || "papier-toilette" in tag || "papier-hygienique" in tag -> "OTHER"
            "tobacco" in tag || "cigarette" in tag || "e-cigarette" in tag -> "TOBACCO"
            "battery" in tag || "batteries" in tag -> "BATTERY"
            "bleach" in tag || "javel" in tag -> "BLEACH"
            "laundry" in tag || "lessive" in tag -> "LAUNDRY"
            "cleaning-product" in tag || "detergent" in tag || "nettoyant" in tag -> "CLEANING_PRODUCT"
            "household-chemical" in tag || "solvent" in tag -> "HOUSEHOLD_CHEMICAL"
            // Added 13/08/2026: hair-care/shampoo, shower-gel/body-wash,
            // toothpaste/oral-care, and make-up tags - a real gap that made
            // shampoo, gel douche, dentifrice, and makeup barcodes fall
            // through to "product not found" even when OPF had the product,
            // because OPF's own category tags for these (e.g.
            // "en:hair-care-products", "en:shampoos", "en:toothpastes")
            // don't contain the generic "cosmetic"/"beauty"/"personal-care"/
            // "hygiene" substrings already matched below - reported directly
            // by a user hitting this on real L'Oréal shampoo and Elmex
            // toothpaste barcodes. This is exactly the per-category
            // functional-score series (ShampooQualityScore,
            // ShowerGelQualityScore, ToothpasteQualityScore,
            // CosmeticActivesScore, MakeupQualityScore) built this session -
            // none of those scores can ever run if the product is never
            // even classified as non-food in the first place.
            "hair-care" in tag || "shampoo" in tag || "shampooing" in tag -> "PERSONAL_CARE"
            "shower-gel" in tag || "gel-douche" in tag || "body-wash" in tag || "bath-and-shower" in tag -> "PERSONAL_CARE"
            "toothpaste" in tag || "dentifrice" in tag || "oral-hygiene" in tag || "oral-care" in tag -> "PERSONAL_CARE"
            "make-up" in tag || "makeup" in tag || "maquillage" in tag || "cosmetics" in tag -> "PERSONAL_CARE"
            // "creme" removed 13/08/2026 - real bug found on app-wide review:
            // OPF tags a genuine food product like "en:cremes-dessert" or
            // "fr:cremes-fraiches" (crème dessert, crème fraîche) contain
            // "creme" as a raw substring with no "dairy"/"food" token for the
            // looksLikeFood guard above to catch, so real food was silently
            // routed to PERSONAL_CARE and thrown as a NonFoodProductException.
            // "skin-care"/"moisturi" are safe (no food-product tag legitimately
            // contains either).
            "skin-care" in tag || "moisturi" in tag -> "PERSONAL_CARE"
            "cosmetic" in tag || "beauty" in tag || "personal-care" in tag || "hygiene" in tag -> "PERSONAL_CARE"
            "non-food" in tag -> "OTHER"
            else -> null
        }
        if (fromTags != null) return fromTags
    }
    val nameAndBrand = ((productName ?: "") + " " + (brand ?: "")).lowercase()
    if (nameAndBrand.isBlank()) return null
    return when {
        "durex" in nameAndBrand || "glijmiddel" in nameAndBrand || "lubrifiant" in nameAndBrand ||
            "lubricant" in nameAndBrand || "preservatif" in nameAndBrand || "préservatif" in nameAndBrand ||
            "condom" in nameAndBrand -> "PERSONAL_CARE"
        // Added 13/08/2026 alongside the tag-based additions above - same
        // "unambiguous in any context" bar every existing entry in this list
        // already holds to (a real food product would never legitimately
        // contain "shampooing" or "dentifrice" in its name), for the same
        // OPF-tags-are-sparse-or-missing case this fallback exists for.
        "shampooing" in nameAndBrand || "shampoo" in nameAndBrand ||
            "gel douche" in nameAndBrand || "gel de douche" in nameAndBrand || "shower gel" in nameAndBrand ||
            "dentifrice" in nameAndBrand || "toothpaste" in nameAndBrand ||
            // Kept in sync with MakeupQualityScore.isLikelyMakeup's own keyword
            // list (fixed 13/08/2026: fond-de-teint/mascara/eyeliner were
            // already here, but foundation/concealer/anti-cernes/lipstick/
            // fard-à-paupières/eyeshadow were not, so classifyNonFood and the
            // score gate it feeds could disagree on the same product).
            "mascara" in nameAndBrand || "rouge à lèvres" in nameAndBrand || "rouge a levres" in nameAndBrand ||
            "fond de teint" in nameAndBrand || "eyeliner" in nameAndBrand ||
            "lipstick" in nameAndBrand || "fard a paupieres" in nameAndBrand || "eyeshadow" in nameAndBrand ||
            "foundation" in nameAndBrand || "concealer" in nameAndBrand || "anti-cernes" in nameAndBrand -> "PERSONAL_CARE"
        // Added 13/08/2026 - general cosmetics/skincare (lotion/sérum, the
        // CosmeticActivesScore category) had no name-fallback either.
        // "creme"/"cream" deliberately excluded (see the tag-based match
        // above for why - the same real-food false-positive risk, and this
        // name fallback runs even more often since it's the last resort when
        // OPF tags are sparse or absent, e.g. "Crème fraîche 30%", "Ice
        // cream", "Cream cheese" would all have matched here). Skincare-only
        // brand names (SKINCARE_ONLY_BRANDS in isLikelyGeneralCosmetic) are
        // the intended, unambiguous signal for "crème" products instead.
        "lotion" in nameAndBrand || "serum" in nameAndBrand || "sérum" in nameAndBrand -> "PERSONAL_CARE"
        // Added 13/08/2026 - tampons/pads (IntimateHygieneScore's other half)
        // had no name fallback, only sparse OPF tags above. Kept in sync
        // with isLikelyAbsorbentHygieneProduct's own keyword list. Genuinely
        // HYGIENE_PRODUCT - these ARE superabsorbent-polymer disposables,
        // matching that category's fact text.
        "tampon" in nameAndBrand || "serviette hygienique" in nameAndBrand || "serviette hygiénique" in nameAndBrand ||
            "protege-slip" in nameAndBrand -> "HYGIENE_PRODUCT"
        // OTHER, not HYGIENE_PRODUCT - correctness fix caught in the same
        // category-consistency audit as toilet paper above: a menstrual cup
        // is reusable silicone and a wet wipe is a moistened tissue, neither
        // is a "superabsorbent polymer" disposable the way tampons/pads
        // genuinely are, so HYGIENE_PRODUCT's fact text would misdescribe
        // them (same false-claim issue as toilet paper).
        "coupe menstruelle" in nameAndBrand || "menstrual cup" in nameAndBrand ||
            "lingette intime" in nameAndBrand || "toilette intime" in nameAndBrand ||
            "intimate wipe" in nameAndBrand || "feminine wipe" in nameAndBrand -> "OTHER"
        // Added 13/08/2026 - toilet paper, see the tag-based addition above for why.
        "papier toilette" in nameAndBrand || "papier hygienique" in nameAndBrand || "papier hygiénique" in nameAndBrand ||
            "toilet paper" in nameAndBrand -> "OTHER"
        else -> null
    }
}
