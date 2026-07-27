package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// OFF CATEGORY MAPPING — split out of OffMapper.kt: maps OFF's raw category
// tags to our domain ProductCategory, and separately flags non-food products.
// ============================================================================

internal fun mapCategory(tags: List<String>?): ProductCategory {
    if (tags.isNullOrEmpty()) return ProductCategory.OTHER
    // Scan the whole tag hierarchy, not just tags[0] — OFF often puts a generic
    // parent tag (e.g. "en:beverages") first, which was mis-bucketing plenty of
    // products before ever reaching their more specific tag further down the list.
    val tag = tags.joinToString(" ")
    return when {
        "yogurt" in tag || "yaourt" in tag || "skyr" in tag -> ProductCategory.YOGURT
        "cheese" in tag || "fromage" in tag -> ProductCategory.CHEESE
        "sandwich" in tag || "burger" in tag -> ProductCategory.SANDWICH
        "cereal" in tag || "cereale" in tag || "granola" in tag -> ProductCategory.BREAKFAST_CEREAL
        "bread" in tag || "pain" in tag -> ProductCategory.BREAD
        "processed-meat" in tag || "charcuterie" in tag || "saucisson" in tag -> ProductCategory.PROCESSED_MEAT
        "meat" in tag || "viande" in tag -> ProductCategory.FRESH_MEAT
        "fish" in tag || "seafood" in tag || "poisson" in tag -> ProductCategory.FISH
        "biscuit" in tag || "cookie" in tag || "chocolate" in tag || "snack" in tag && ("sweet" in tag || "sucre" in tag) -> ProductCategory.SNACK_SWEET
        "chips" in tag || "crisp" in tag || "snack" in tag -> ProductCategory.SNACK_SALTY
        "beverage" in tag && "juice" in tag -> ProductCategory.BEVERAGE_JUICE
        "beverage" in tag && ("water" in tag || "eau" in tag) -> ProductCategory.BEVERAGE_WATER
        "beverage" in tag || "soda" in tag || "boisson" in tag -> ProductCategory.BEVERAGE_SOFT
        "sauce" in tag || "condiment" in tag || "dressing" in tag -> ProductCategory.CONDIMENT
        "oil" in tag || "fat" in tag || "huile" in tag -> ProductCategory.OIL_FAT
        "soup" in tag || "soupe" in tag || "broth" in tag || "bouillon" in tag -> ProductCategory.SOUP
        "ready-meal" in tag || "plat-prepare" in tag -> ProductCategory.READY_MEAL
        else -> ProductCategory.OTHER
    }
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
 */
fun classifyNonFood(tags: List<String>?): String? {
    if (tags.isNullOrEmpty()) return null
    val tag = tags.joinToString(" ")
    // Checked before the generic "looks like food" safety net below - pet food
    // literally contains the substring "food" (pet-food, cat-food, dog-food),
    // which would otherwise always disqualify it from ever being flagged here.
    if ("pet-food" in tag || "animal-feed" in tag || "cat-food" in tag || "dog-food" in tag) return "PET_SUPPLY"
    val looksLikeFood = listOf(
        "food", "beverage", "drink", "supplement", "dietary-supplement",
        "medicine", "medication", "meal", "snack", "dairy", "cereal",
    ).any { it in tag }
    if (looksLikeFood) return null
    return when {
        "sex-toy" in tag || "lubricant" in tag -> "PERSONAL_CARE"
        "feminine-hygiene" in tag || "sanitary-protection" in tag ||
            "diaper" in tag || "baby-hygiene" in tag -> "HYGIENE_PRODUCT"
        "tobacco" in tag || "cigarette" in tag || "e-cigarette" in tag -> "TOBACCO"
        "battery" in tag || "batteries" in tag -> "BATTERY"
        "bleach" in tag || "javel" in tag -> "BLEACH"
        "laundry" in tag || "lessive" in tag -> "LAUNDRY"
        "cleaning-product" in tag || "detergent" in tag || "nettoyant" in tag -> "CLEANING_PRODUCT"
        "household-chemical" in tag || "solvent" in tag -> "HOUSEHOLD_CHEMICAL"
        "cosmetic" in tag || "beauty" in tag || "personal-care" in tag || "hygiene" in tag -> "PERSONAL_CARE"
        "non-food" in tag -> "OTHER"
        else -> null
    }
}
