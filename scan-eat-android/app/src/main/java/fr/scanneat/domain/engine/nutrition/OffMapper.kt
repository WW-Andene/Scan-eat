package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.inferCategoryFromName
import fr.scanneat.domain.model.*

// ============================================================================
// OFF MAPPER — port of src/off.ts mapping, merge, and sparsity logic
// Network calls live in data/remote; this file is pure domain logic.
// ============================================================================

/** OFF API response shape (subset of fields we use). */
data class OffProductResponse(
    val productName: String?,
    val productNameFr: String?,
    val genericNameFr: String?,
    val brands: String?,
    val categoriesTags: List<String>?,
    val ingredientsTextFr: String?,
    val ingredientsText: String?,
    val novaGroup: Int?,
    val nutriments: Map<String, Any?>?,
    val labelsTags: List<String>?,
    val origins: String?,
    val countriesTags: List<String>?,
    val quantity: String?,
    val ecoscoreGrade: String?,
    val ecoscoreScore: Int?,
    val nutritionGrades: String?,
    val allergensTags: List<String>? = null,
    val additivesTags: List<String>? = null,
)

// ============================================================================
// Mapping helpers
// ============================================================================

private fun numOf(v: Any?): Double = when (v) {
    is Number -> v.toDouble().takeIf { it.isFinite() } ?: 0.0
    is String -> v.replace(",", ".").toDoubleOrNull() ?: 0.0
    else -> 0.0
}

private fun numOrNull(v: Any?): Double? = when (v) {
    null -> null
    is Number -> v.toDouble().takeIf { it.isFinite() }
    is String -> v.replace(",", ".").toDoubleOrNull()
    else -> null
}

private fun mapCategory(tags: List<String>?): ProductCategory {
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
        "ready-meal" in tag || "plat-prepare" in tag -> ProductCategory.READY_MEAL
        else -> ProductCategory.OTHER
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

// OFF's own curated additives_tags (e.g. "en:e322") - manufacturer-verified,
// catches additives OFF's own parser found even when the label doesn't spell
// out an explicit "E" prefix. Only adds a synthetic ingredient for a tag not
// already covered by the ingredient-text regex above, never duplicating or
// overriding what the label actually says.
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
    val m = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|cl|dl|ml|g|l)\b""", RegexOption.IGNORE_CASE).find(quantity)
    val v = m?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: return null
    return when (m.groupValues[2].lowercase()) {
        "kg", "l" -> v * 1000
        "cl"      -> v * 10
        "dl"      -> v * 100
        else      -> v
    }
}

/**
 * Map an OFF API product response to our domain Product model.
 * Pure function — no network, no side effects.
 */
fun mapOffProduct(off: OffProductResponse): Product? {
    // A missing/empty nutriments table doesn't mean the product wasn't found —
    // plenty of real, well-known OFF entries (this is what broke plain sodas
    // like Coca-Cola) only have name/brand filled in. Treat it as zero rather
    // than aborting the whole lookup; isOffSparse() below will flag it for the
    // photo/LLM fallback instead of forcing a false "product not found".
    val nm = off.nutriments ?: emptyMap()
    val name = (off.productNameFr ?: off.productName ?: off.genericNameFr ?: "").trim()
        .takeIf { it.isNotEmpty() } ?: return null

    val ingredientsText = off.ingredientsTextFr ?: off.ingredientsText
    val parsedIngredients = parseIngredients(ingredientsText)
    val ingredients = parsedIngredients + additiveTagsToIngredients(off.additivesTags, parsedIngredients)

    val labelTags = off.labelsTags ?: emptyList()
    val organic   = labelTags.any { "organic" in it || "bio" in it }

    val category = mapCategory(off.categoriesTags).let {
        if (it == ProductCategory.OTHER) inferCategoryFromName(name) else it
    }

    return Product(
        name      = name,
        category  = category,
        novaClass = NovaClass.fromInt(off.novaGroup ?: 4),
        ingredients = ingredients,
        nutrition = NutritionPer100g(
            energyKcal    = numOf(nm["energy-kcal_100g"] ?: nm["energy_100g"]?.let { (numOf(it) / 4.184) }),
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
            b6Mg          = numOrNull(nm["vitamin-b6_100g"])?.times(1000),
            omega3G       = numOrNull(nm["omega-3-fat_100g"]),
        ),
        weightG            = parseWeightG(off.quantity),
        origin             = off.origins?.takeIf { it.isNotBlank() },
        organic            = organic,
        ecoscoreGrade      = off.ecoscoreGrade?.lowercase()?.takeIf { it.matches(Regex("[a-e]")) },
        ecoscoreValue      = off.ecoscoreScore?.toDouble(),
        nutriscoreGrade    = off.nutritionGrades?.lowercase()?.firstOrNull()?.toString()?.takeIf { it.matches(Regex("[a-e]")) },
        declaredAllergenTags = off.allergensTags.orEmpty(),
    )
}

// ============================================================================
// Sparsity check
// ============================================================================

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

// ============================================================================
// Merge: OFF (authoritative) + LLM (fills gaps)
// ============================================================================

/**
 * Merge OFF record with LLM extraction.
 * OFF is the trusted baseline; LLM fills empty / zero fields.
 * Port of mergeOFFWithLLM from off.ts.
 */
fun mergeOffWithLlm(off: Product, llm: Product): Product {
    fun <T> prefer(offVal: T, llmVal: T, isEmpty: (T) -> Boolean): T =
        if (isEmpty(offVal)) llmVal else offVal

    val emptyStr: (Any?) -> Boolean = { it == null || (it is String && it.isBlank()) }
    val emptyNum: (Double) -> Boolean = { it == 0.0 }
    val emptyList: (List<*>) -> Boolean = { it.isEmpty() }

    fun mergeNutrition(o: NutritionPer100g, l: NutritionPer100g) = NutritionPer100g(
        energyKcal    = prefer(o.energyKcal,    l.energyKcal,    emptyNum),
        fatG          = prefer(o.fatG,          l.fatG,          emptyNum),
        saturatedFatG = prefer(o.saturatedFatG, l.saturatedFatG, emptyNum),
        carbsG        = prefer(o.carbsG,        l.carbsG,        emptyNum),
        sugarsG       = prefer(o.sugarsG,       l.sugarsG,       emptyNum),
        addedSugarsG  = o.addedSugarsG  ?: l.addedSugarsG,
        fiberG        = prefer(o.fiberG,        l.fiberG,        emptyNum),
        proteinG      = prefer(o.proteinG,      l.proteinG,      emptyNum),
        saltG         = prefer(o.saltG,         l.saltG,        emptyNum),
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
        b6Mg          = o.b6Mg          ?: l.b6Mg,
        b9Ug          = o.b9Ug          ?: l.b9Ug,
        omega3G       = o.omega3G       ?: l.omega3G,
        omega6G       = o.omega6G       ?: l.omega6G,
        cholesterolMg = o.cholesterolMg ?: l.cholesterolMg,
        polyunsaturatedFatG = o.polyunsaturatedFatG ?: l.polyunsaturatedFatG,
        monounsaturatedFatG = o.monounsaturatedFatG ?: l.monounsaturatedFatG,
    )

    return Product(
        name        = prefer(off.name, llm.name) { emptyStr(it) },
        category    = if (off.category != ProductCategory.OTHER) off.category else llm.category,
        novaClass   = if (off.novaClass.value > 0) off.novaClass else llm.novaClass,
        ingredients = prefer(off.ingredients, llm.ingredients) { emptyList(it) || it.size < 3 },
        nutrition   = mergeNutrition(off.nutrition, llm.nutrition),
        weightG             = off.weightG     ?: llm.weightG,
        origin              = off.origin      ?: llm.origin,
        organic             = off.organic     || llm.organic,
        hasHealthClaims     = off.hasHealthClaims || llm.hasHealthClaims,
        hasMisleadingMarketing = off.hasMisleadingMarketing || llm.hasMisleadingMarketing,
        namedOils           = off.namedOils   ?: llm.namedOils,
        originTransparent   = off.originTransparent || llm.originTransparent,
        wholeGrainPrimary   = off.wholeGrainPrimary || llm.wholeGrainPrimary,
        fermented           = off.fermented || llm.fermented,
        declaredMicronutrients = (off.declaredMicronutrients + llm.declaredMicronutrients).distinct(),
        ecoscoreGrade  = off.ecoscoreGrade,
        ecoscoreValue  = off.ecoscoreValue,
        nutriscoreGrade = off.nutriscoreGrade,
        declaredAllergenTags = off.declaredAllergenTags,
    )
}

data class SourceConflict(val field: String, val offValue: String, val llmValue: String)

fun detectSourceConflicts(off: Product, llm: Product): List<SourceConflict> {
    val conflicts = mutableListOf<SourceConflict>()
    val n = off.nutrition
    val l = llm.nutrition
    fun check(field: String, o: Double, lv: Double) {
        if (o > 0 && lv > 0 && kotlin.math.abs(o - lv) / maxOf(o, lv) > 0.3)
            conflicts += SourceConflict(field, "${o}g", "${lv}g")
    }
    check("protein_g", n.proteinG, l.proteinG)
    check("fat_g", n.fatG, l.fatG)
    check("carbs_g", n.carbsG, l.carbsG)
    check("sugars_g", n.sugarsG, l.sugarsG)
    return conflicts
}
