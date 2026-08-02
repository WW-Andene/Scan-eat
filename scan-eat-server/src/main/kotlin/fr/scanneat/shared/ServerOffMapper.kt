package fr.scanneat.shared

import fr.scanneat.service.OffProductRaw
import kotlinx.serialization.json.*

// ============================================================================
// SERVER OFF MAPPER
// Converts the raw OFF API response to the domain Product model.
// Mirrors OffMapper.kt from the Android project — kept in sync manually.
// ============================================================================

private fun numOf(v: JsonElement?): Double = when (v) {
    is JsonPrimitive -> v.doubleOrNull
        ?: v.contentOrNull?.replace(",", ".")?.toDoubleOrNull()
        ?: 0.0
    else -> 0.0
}

private fun numOrNull(v: JsonElement?): Double? = when (v) {
    is JsonPrimitive -> v.doubleOrNull
        ?: v.contentOrNull?.replace(",", ".")?.toDoubleOrNull()
    else -> null
}

private fun mapCategory(tags: List<String>?): ProductCategory {
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
        "cheese" in tag || "fromage" in tag -> ProductCategory.CHEESE
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

// Mirrors OffMapper.kt's classifyNonFood on the Android project — kept in sync
// manually, same as mapCategory above. See that copy's own doc comment for
// the full rationale, including why [productName]/[brand] exist as a
// last-resort fallback for tags that are missing, sparse, or localized in a
// taxonomy whose id doesn't contain the English substring being matched
// (e.g. "fr:lubrifiants" or "nl:glijmiddelen" instead of "en:lubricants").
fun classifyNonFood(tags: List<String>?, productName: String? = null, brand: String? = null): String? {
    val tag = tags?.joinToString(" ") ?: ""
    if (tag.isNotEmpty()) {
        // Checked before the generic "looks like food" safety net below - pet food
        // literally contains the substring "food" (pet-food, cat-food, dog-food),
        // which would otherwise always disqualify it from ever being flagged here.
        if ("pet-food" in tag || "animal-feed" in tag || "cat-food" in tag || "dog-food" in tag) return "PET_SUPPLY"
        val looksLikeFood = listOf(
            "food", "beverage", "drink", "supplement", "dietary-supplement",
            "medicine", "medication", "meal", "snack", "dairy", "cereal",
        ).any { it in tag }
        if (looksLikeFood) return null
        val fromTags = when {
            "sex-toy" in tag || "lubricant" in tag || "lubrifiant" in tag || "glijmiddel" in tag -> "PERSONAL_CARE"
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
        if (fromTags != null) return fromTags
    }
    val nameAndBrand = ((productName ?: "") + " " + (brand ?: "")).lowercase()
    if (nameAndBrand.isBlank()) return null
    return when {
        "durex" in nameAndBrand || "glijmiddel" in nameAndBrand || "lubrifiant" in nameAndBrand ||
            "lubricant" in nameAndBrand || "preservatif" in nameAndBrand || "préservatif" in nameAndBrand ||
            "condom" in nameAndBrand -> "PERSONAL_CARE"
        else -> null
    }
}

private fun parseIngredients(text: String?): List<Ingredient> {
    if (text.isNullOrBlank()) return emptyList()
    // Split on commas and semicolons that are not inside parentheses
    return text.split(Regex("""[,;]\s*(?![^(]*\))"""))
        .mapNotNull { raw ->
            val name = raw.trim().trim('*', ' ')
            if (name.isBlank()) return@mapNotNull null
            val eNumber = Regex("""[Ee](\d{3}[a-zA-Z]?)""").find(name)?.let { "E${it.groupValues[1]}" }
            Ingredient(name = name, eNumber = eNumber)
        }
        .filter { it.name.length > 1 }
}

// OFF's own curated additives_tags (e.g. "en:e322", "en:e150d") - verified
// against the manufacturer's declaration. Ingredient-text regex already
// extracts E-numbers when the label spells them out, but OFF's own parser
// catches additives named without an explicit "E" prefix (e.g. "lécithines"
// tagged en:e322 even though the label just says "lécithines"). Only adds a
// synthetic ingredient for a tag not already covered by a real one, so this
// never duplicates or overrides what the label actually says.
private fun additiveTagsToIngredients(tags: List<String>?, existing: List<Ingredient>): List<Ingredient> {
    if (tags.isNullOrEmpty()) return emptyList()
    val existingENumbers = existing.mapNotNull { it.eNumber }.toSet()
    return tags.mapNotNull { tag ->
        val m = Regex("""en:e(\d{3}[a-z]?)""").find(tag) ?: return@mapNotNull null
        val eNum = "E${m.groupValues[1].uppercase()}"
        if (eNum in existingENumbers) return@mapNotNull null
        Ingredient(name = eNum, eNumber = eNum)
    }.distinctBy { it.eNumber }
}

private fun parseWeightG(quantity: String?): Double? {
    if (quantity.isNullOrBlank()) return null
    // cl/dl added - French OFF `quantity` strings overwhelmingly label beverages in
    // centiliters ("33 cl" cans, "75cl" wine) rather than ml/l; without them this
    // silently returned null for most beverages, falling back to a generic 100g
    // portion-size default instead of the real pack size.
    // Mirrors OffMapper.kt's parseWeightG on the Android project.
    val m = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|cl|dl|ml|g|l)\b""", RegexOption.IGNORE_CASE).find(quantity) ?: return null
    val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
    return when (m.groupValues[2].lowercase()) {
        "kg", "l" -> v * 1000
        "cl"      -> v * 10
        "dl"      -> v * 100
        else      -> v
    }
}

fun mapOffProduct(raw: OffProductRaw): Product? {
    // A missing/empty nutriments table doesn't mean the product wasn't found —
    // plenty of real, well-known OFF entries (this is what broke plain sodas
    // like Coca-Cola) only have name/brand filled in. Treat it as zero rather
    // than aborting the whole lookup; isOffSparse() below will flag it for the
    // photo/LLM fallback instead of forcing a false "product not found".
    val nm = raw.nutriments ?: JsonObject(emptyMap())
    val name = (raw.productNameFr ?: raw.productName ?: raw.genericNameFr ?: "").trim()
        .takeIf { it.isNotEmpty() } ?: return null

    val parsedIngredients = parseIngredients(raw.ingredientsTextFr ?: raw.ingredientsText)
    val ingredients = parsedIngredients + additiveTagsToIngredients(raw.additivesTags, parsedIngredients)
    val organic = raw.labelsTags?.any { "organic" in it || "bio" in it } == true
    val category = mapCategory(raw.categoriesTags).let {
        if (it == ProductCategory.OTHER) inferCategoryFromName(name) else it
    }

    val nutrition = NutritionPer100g(
        // OFF's energy_100g fallback is in kJ, not kcal — convert
        energyKcal    = numOrNull(nm["energy-kcal_100g"]) ?: (numOf(nm["energy_100g"]) / 4.184),
        fatG          = numOf(nm["fat_100g"]),
        saturatedFatG = numOf(nm["saturated-fat_100g"]),
        carbsG        = numOf(nm["carbohydrates_100g"]),
        sugarsG       = numOf(nm["sugars_100g"]),
        addedSugarsG  = numOrNull(nm["added-sugars_100g"]),
        fiberG        = numOf(nm["fiber_100g"]),
        proteinG      = numOf(nm["proteins_100g"]),
        // Some OFF records carry sodium_100g but not salt_100g - without a fallback
        // those products silently scored saltG=0 for the negative-nutrients pillar
        // even though a salt value is derivable from data already fetched. 2.5 is
        // the standard sodium→salt conversion factor (NaCl molar mass ratio).
        saltG         = numOrNull(nm["salt_100g"]) ?: (numOrNull(nm["sodium_100g"])?.times(2.5) ?: 0.0),
        transFatG     = numOrNull(nm["trans-fat_100g"]),
        ironMg        = numOrNull(nm["iron_100g"])?.times(1000),     // OFF in g → mg
        calciumMg     = numOrNull(nm["calcium_100g"])?.times(1000),
        magnesiumMg   = numOrNull(nm["magnesium_100g"])?.times(1000),
        potassiumMg   = numOrNull(nm["potassium_100g"])?.times(1000),
        zincMg        = numOrNull(nm["zinc_100g"])?.times(1000),
        sodiumMg      = numOrNull(nm["sodium_100g"])?.times(1000),
        vitAUg        = numOrNull(nm["vitamin-a_100g"])?.times(1_000_000),
        vitCMg        = numOrNull(nm["vitamin-c_100g"])?.times(1000),
        vitDUg        = numOrNull(nm["vitamin-d_100g"])?.times(1_000_000),
        vitEMg        = numOrNull(nm["vitamin-e_100g"])?.times(1000),
        vitKUg        = numOrNull(nm["vitamin-k_100g"])?.times(1_000_000),
        b12Ug         = numOrNull(nm["vitamin-b12_100g"])?.times(1_000_000),
        // b1/b2/b3/b9 were missing here even though NutritionPer100g already carries
        // all of them (and OffMapper.kt on Android — "kept in sync manually" per this
        // file's own header — already maps them) - Server-mode users could never see
        // these four vitamins populated, only Direct-mode ones could. "vitamin-pp" is
        // OFF's French-pharmacopoeia name for niacin/B3.
        b1Mg          = numOrNull(nm["vitamin-b1_100g"])?.times(1000),
        b2Mg          = numOrNull(nm["vitamin-b2_100g"])?.times(1000),
        b3Mg          = numOrNull(nm["vitamin-pp_100g"])?.times(1000),
        b6Mg          = numOrNull(nm["vitamin-b6_100g"])?.times(1000),
        b9Ug          = numOrNull(nm["vitamin-b9_100g"])?.times(1_000_000),
        omega3G       = numOrNull(nm["omega-3-fat_100g"]),
        // Mirrors OffMapper.kt on Android — caffeine is a real hypertension risk
        // factor (see PersonalScoreEngine's checkHealthConditions) that was never
        // mapped from OFF on either side before. OFF stores it in grams like the
        // other minerals above.
        caffeineMg    = numOrNull(nm["caffeine_100g"])?.times(1000),
    )

    return Product(
        name        = name,
        category    = category,
        novaClass   = NovaClass.fromInt(raw.novaGroup ?: 4),
        ingredients = ingredients,
        nutrition   = nutrition,
        weightG         = parseWeightG(raw.quantity),
        origin          = raw.origins?.takeIf { it.isNotBlank() },
        organic         = organic,
        ecoscoreGrade   = raw.ecoscoreGrade?.lowercase()?.takeIf { it.matches(Regex("[a-e]")) },
        ecoscoreValue   = raw.ecoscoreScore?.toDouble(),
        nutriscoreGrade = raw.nutritionGrades?.lowercase()?.firstOrNull()?.toString()?.takeIf { it.matches(Regex("[a-e]")) },
        declaredAllergenTags = raw.allergensTags.orEmpty(),
        declaredMicronutrients = declaredMicronutrientsOf(nutrition),
    )
}

/**
 * True when an OFF-sourced product is missing enough data that LLM
 * augmentation is worth attempting.
 */
fun isOffSparse(p: Product): Boolean {
    val n = p.nutrition
    val hasNutrition   = n.energyKcal > 0 || n.proteinG > 0 || n.carbsG > 0
    // A genuinely single/dual-ingredient product (water, salt, single-origin oil)
    // isn't sparse data — only a fully empty ingredients list is a real gap.
    val hasIngredients = p.ingredients.isNotEmpty()
    val hasCategory    = p.category != ProductCategory.OTHER
    // Micronutrients are legitimately absent from most nutrition-facts panels
    // (a can of soda reporting zero vitamins isn't "sparse data", it's correct)
    // so their absence no longer counts against a product — this was flagging
    // almost every packaged product as sparse and forcing needless LLM merges.
    return !hasNutrition || !hasIngredients || !hasCategory
}

/**
 * Merge OFF record with LLM extraction.
 * OFF is the trusted baseline; LLM fills empty / zero fields.
 */
fun mergeOffWithLlm(off: Product, llm: Product): Product {
    fun <T> prefer(offVal: T, llmVal: T, isEmpty: (T) -> Boolean): T =
        if (isEmpty(offVal)) llmVal else offVal

    val emptyStr: (String) -> Boolean = { it.isBlank() }
    val emptyNum: (Double) -> Boolean = { it == 0.0 }

    fun mergeNutrition(o: NutritionPer100g, l: NutritionPer100g) = NutritionPer100g(
        energyKcal    = prefer(o.energyKcal,    l.energyKcal,    emptyNum),
        fatG          = prefer(o.fatG,          l.fatG,          emptyNum),
        saturatedFatG = prefer(o.saturatedFatG, l.saturatedFatG, emptyNum),
        carbsG        = prefer(o.carbsG,        l.carbsG,        emptyNum),
        sugarsG       = prefer(o.sugarsG,       l.sugarsG,       emptyNum),
        addedSugarsG  = o.addedSugarsG  ?: l.addedSugarsG,
        fiberG        = prefer(o.fiberG,        l.fiberG,        emptyNum),
        proteinG      = prefer(o.proteinG,      l.proteinG,      emptyNum),
        saltG         = prefer(o.saltG,         l.saltG,         emptyNum),
        transFatG     = o.transFatG     ?: l.transFatG,
        ironMg        = o.ironMg        ?: l.ironMg,
        calciumMg     = o.calciumMg     ?: l.calciumMg,
        magnesiumMg   = o.magnesiumMg   ?: l.magnesiumMg,
        potassiumMg   = o.potassiumMg   ?: l.potassiumMg,
        zincMg        = o.zincMg        ?: l.zincMg,
        sodiumMg      = o.sodiumMg      ?: l.sodiumMg,
        vitAUg        = o.vitAUg        ?: l.vitAUg,
        vitCMg        = o.vitCMg        ?: l.vitCMg,
        vitDUg        = o.vitDUg        ?: l.vitDUg,
        vitEMg        = o.vitEMg        ?: l.vitEMg,
        vitKUg        = o.vitKUg        ?: l.vitKUg,
        b12Ug         = o.b12Ug         ?: l.b12Ug,
        // b1Mg/b2Mg/b3Mg were missing here even though NutritionPer100g carries all
        // three and mapOffProduct() above already maps them from OFF - any product
        // that also went through LLM merge (isOffSparse true for some unrelated
        // field, e.g. missing category) silently dropped OFF's own B1/B2/B3 values
        // back to null. Mirrors OffMerge.kt's identical fix on the Android project.
        b1Mg          = o.b1Mg          ?: l.b1Mg,
        b2Mg          = o.b2Mg          ?: l.b2Mg,
        b3Mg          = o.b3Mg          ?: l.b3Mg,
        b6Mg          = o.b6Mg          ?: l.b6Mg,
        b9Ug          = o.b9Ug          ?: l.b9Ug,
        omega3G       = o.omega3G       ?: l.omega3G,
        omega6G       = o.omega6G       ?: l.omega6G,
        cholesterolMg = o.cholesterolMg ?: l.cholesterolMg,
        caffeineMg    = o.caffeineMg    ?: l.caffeineMg,
        polyunsaturatedFatG = o.polyunsaturatedFatG ?: l.polyunsaturatedFatG,
        monounsaturatedFatG = o.monounsaturatedFatG ?: l.monounsaturatedFatG,
    )

    return Product(
        name        = prefer(off.name, llm.name, emptyStr),
        category    = if (off.category != ProductCategory.OTHER) off.category else llm.category,
        novaClass   = if (off.novaClass.value > 0) off.novaClass else llm.novaClass,
        // Threshold matches isOffSparse's own documented rule above: a genuine
        // 1-2 ingredient product (water, salt, single-origin oil) is real,
        // correct OFF data, not a gap to paper over with an LLM guess.
        ingredients = prefer(off.ingredients, llm.ingredients) { it.isEmpty() },
        nutrition   = mergeNutrition(off.nutrition, llm.nutrition),
        weightG             = off.weightG     ?: llm.weightG,
        origin              = off.origin      ?: llm.origin,
        organic             = off.organic     || llm.organic,
        hasHealthClaims     = off.hasHealthClaims || llm.hasHealthClaims,
        hasMisleadingMarketing = off.hasMisleadingMarketing || llm.hasMisleadingMarketing,
        namedOils           = off.namedOils   ?: llm.namedOils,
        originTransparent   = off.originTransparent || llm.originTransparent,
        // wholeGrainPrimary/fermented were missing from this Product(...) call, so
        // they silently fell back to the Product default (false) on every merged
        // product regardless of what OFF or the LLM actually detected — dropping
        // the +3/+2 global scoring bonuses (computeGlobalBonuses in ScoringEngine.kt)
        // for whole-grain / fermented products scored via the OFF+LLM merge path.
        // OffMapper.kt on Android (kept "in sync manually" per this file's header)
        // already carries both fields through; this brings the server back in sync.
        wholeGrainPrimary   = off.wholeGrainPrimary || llm.wholeGrainPrimary,
        fermented           = off.fermented || llm.fermented,
        declaredMicronutrients = (off.declaredMicronutrients + llm.declaredMicronutrients).distinct(),
        ecoscoreGrade   = off.ecoscoreGrade,
        ecoscoreValue   = off.ecoscoreValue,
        nutriscoreGrade = off.nutriscoreGrade,
        // Previously kept only off.declaredAllergenTags, discarding the LLM's own -
        // LlmLabelParser.mapToProduct() reads the packaging's printed allergen box
        // into declaredAllergenTags just like OFF's allergens_tags, but OFF frequently
        // has none even for a well-populated record, silently losing a real, LLM-read
        // allergen declaration on merge. Mirrors declaredMicronutrients' union above.
        declaredAllergenTags = (off.declaredAllergenTags + llm.declaredAllergenTags).distinct(),
    )
}

data class SourceConflict(val field: String, val offValue: String, val llmValue: String)

fun detectSourceConflicts(off: Product, llm: Product): List<SourceConflict> {
    val conflicts = mutableListOf<SourceConflict>()
    fun check(field: String, o: Double, l: Double) {
        if (o > 0 && l > 0 && kotlin.math.abs(o - l) / maxOf(o, l) > 0.3)
            conflicts += SourceConflict(field, "${o}g", "${l}g")
    }
    check("protein_g", off.nutrition.proteinG, llm.nutrition.proteinG)
    check("fat_g",     off.nutrition.fatG,     llm.nutrition.fatG)
    check("carbs_g",   off.nutrition.carbsG,   llm.nutrition.carbsG)
    check("sugars_g",  off.nutrition.sugarsG,  llm.nutrition.sugarsG)
    return conflicts
}
