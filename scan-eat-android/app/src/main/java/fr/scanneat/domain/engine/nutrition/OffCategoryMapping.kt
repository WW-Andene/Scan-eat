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
    return if (nameCategory != ProductCategory.OTHER && nameCategory !in BEVERAGE_CATEGORIES) nameCategory else offCategory
}

internal fun mapCategory(tags: List<String>?): ProductCategory {
    if (tags.isNullOrEmpty()) return ProductCategory.OTHER
    // Scan the whole tag hierarchy, not just tags[0] — OFF often puts a generic
    // parent tag (e.g. "en:beverages") first, which was mis-bucketing plenty of
    // products before ever reaching their more specific tag further down the list.
    val tag = tags.joinToString(" ")
    // sandwich/burger checked before cheese: OFF tags a grilled-cheese/
    // croque-monsieur-type product with BOTH "en:sandwiches" and
    // "en:cheese-sandwiches" (a real, reachable OFF tag combination), and
    // since every branch here matches against the whole joined tag string,
    // whichever branch came first previously won regardless of which tag was
    // actually the more specific one for that product - a cheese sandwich was
    // silently bucketed as CHEESE (a raw-ingredient category whose scoring
    // pillars don't fit a prepared sandwich) instead of SANDWICH.
    return when {
        "yogurt" in tag || "yaourt" in tag || "skyr" in tag -> ProductCategory.YOGURT
        "sandwich" in tag || "burger" in tag -> ProductCategory.SANDWICH
        // Same substring-match bug as meat/fish-alternatives below, and now
        // routed to its own PLANT_BASED_ALTERNATIVE category (own thresholds,
        // see CategoryThresholds.kt) instead of just excluded into OTHER's
        // generic bands: OFF tags vegan cheese as "en:cheese-substitutes"
        // (confirmed via live OFF data alongside "en:dairy-substitutes"/
        // "en:vegan-products", e.g. "Vegan feta cheese"), which contains
        // "cheese" as a raw substring - scored against real dairy cheese's
        // protein (15-25g/100g) and sat-fat (12-30g/100g) thresholds, tuned
        // for actual cheese, not a cashew/coconut-oil-based substitute.
        "cheese-substitute" in tag -> ProductCategory.PLANT_BASED_ALTERNATIVE
        "cheese" in tag || "fromage" in tag -> ProductCategory.CHEESE
        "cereal" in tag || "cereale" in tag || "granola" in tag -> ProductCategory.BREAKFAST_CEREAL
        "bread" in tag || "pain" in tag -> ProductCategory.BREAD
        "processed-meat" in tag || "charcuterie" in tag || "saucisson" in tag -> ProductCategory.PROCESSED_MEAT
        // Same substring-match bug ALCOHOLIC_BEVERAGE's own fix below
        // addresses for "non-alcoholic-beverage": OFF tags plant-based meat/
        // fish substitutes as "en:meat-alternatives"/"en:meat-analogues"/
        // "en:fish-alternatives"/"en:fish-analogues" (confirmed via live OFF
        // data - e.g. tofu, textured-pea-protein products), all of which
        // contain "meat"/"fish" as a raw substring - a soy-based product was
        // silently scored against FRESH_MEAT's protein/kcal norms instead of
        // routing to its own PLANT_BASED_ALTERNATIVE category.
        "meat-alternative" in tag || "meat-analogue" in tag || "fish-alternative" in tag || "fish-analogue" in tag -> ProductCategory.PLANT_BASED_ALTERNATIVE
        "meat" in tag || "viande" in tag -> ProductCategory.FRESH_MEAT
        "fish" in tag || "seafood" in tag || "poisson" in tag -> ProductCategory.FISH
        "biscuit" in tag || "cookie" in tag || "chocolate" in tag || "snack" in tag && ("sweet" in tag || "sucre" in tag) -> ProductCategory.SNACK_SWEET
        // Checked before the generic "snack" catch-all below - OFF tags nuts/
        // seeds as "en:nuts", "en:seeds", "en:dried-fruits-and-nuts" etc, and
        // often ALSO under its own "en:salty-snacks" parent tag, so without
        // this a nut/seed product's real, more specific tag was shadowed by
        // the generic snack branch and scored against chip-shaped thresholds
        // (see ProductCategory.NUTS_SEEDS's own doc comment).
        "nut" in tag || "seed" in tag || "graine" in tag || "amande" in tag || "noisette" in tag -> ProductCategory.NUTS_SEEDS
        "chips" in tag || "crisp" in tag || "snack" in tag -> ProductCategory.SNACK_SALTY
        // Checked before the generic "beverage" branches below - OFF tags beer/
        // wine/spirits as "en:beverages" too, so without this an alcoholic
        // drink fell through to BEVERAGE_SOFT and was scored against
        // soda-shaped sugar/kcal reference ranges (see ProductCategory.
        // ALCOHOLIC_BEVERAGE's own doc comment for the bug this fixes).
        // "non-alcoholic-beverage" (OFF's own tag for e.g. sparkling water) contains
        // "alcoholic-beverage" as a raw substring, so this branch was firing on
        // every non-alcoholic product too - a water scanned as an alcoholic drink,
        // inheriting its 30-280kcal/100g norm range instead of water's 0-5 one.
        ("alcoholic-beverage" in tag && "non-alcoholic-beverage" !in tag) || "beer" in tag || "biere" in tag || "wine" in tag || "vin" in tag ||
            "cider" in tag || "cidre" in tag || "spirit" in tag || "spiritueux" in tag || "liquor" in tag ||
            "liqueur" in tag || "whisky" in tag || "whiskey" in tag || "vodka" in tag || "rum" in tag ||
            "champagne" in tag -> ProductCategory.ALCOHOLIC_BEVERAGE
        "beverage" in tag && "juice" in tag -> ProductCategory.BEVERAGE_JUICE
        "beverage" in tag && ("water" in tag || "eau" in tag) -> ProductCategory.BEVERAGE_WATER
        "beverage" in tag || "soda" in tag || "boisson" in tag -> ProductCategory.BEVERAGE_SOFT
        // Checked before CONDIMENT below — OFF tags honey/jam as "en:honeys"/
        // "en:jams"/"en:spreads", none of which contain "sauce"/"condiment"/
        // "dressing", so this branch was entirely absent and every OFF-tagged
        // jar fell through to OTHER. The name-based inferCategoryFromName
        // fallback in OffMapper.kt only catches this when the product *name*
        // itself contains "miel"/"honey"/"confiture" — a private-label jar
        // named only by brand still landed in OTHER's generic thresholds,
        // reproducing the exact "honey scores as critical sugar" bug
        // ProductCategory.SPREAD_SWEET's own doc comment describes.
        "honey" in tag || "miel" in tag || "jam" in tag || "confiture" in tag || "marmalade" in tag ||
            "marmelade" in tag -> ProductCategory.SPREAD_SWEET
        "sauce" in tag || "condiment" in tag || "dressing" in tag -> ProductCategory.CONDIMENT
        "oil" in tag || "fat" in tag || "huile" in tag -> ProductCategory.OIL_FAT
        "soup" in tag || "soupe" in tag || "broth" in tag || "bouillon" in tag -> ProductCategory.SOUP
        "ready-meal" in tag || "plat-prepare" in tag || "pizza" in tag || "quiche" in tag -> ProductCategory.READY_MEAL
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
