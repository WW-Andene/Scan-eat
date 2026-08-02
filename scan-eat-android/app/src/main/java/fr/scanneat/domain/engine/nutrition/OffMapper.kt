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

private fun parseWeightG(quantity: String?): Double? {
    if (quantity.isNullOrBlank()) return null
    // cl/dl added - French OFF `quantity` strings overwhelmingly label beverages in
    // centiliters ("33 cl" cans, "75cl" wine) rather than ml/l; without them this
    // silently returned null for most beverages, falling back to a generic 100g
    // portion-size default instead of the real pack size.
    val m = Regex("""(\d+(?:[.,]\d+)?)\s*(kg|cl|dl|ml|g|l)\b""", RegexOption.IGNORE_CASE).find(quantity) ?: return null
    val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
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

    val nutrition = NutritionPer100g(
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
        // Previously never mapped from OFF at all, despite ProductHints.kt's own
        // benefit checks for all four - same "structurally dead for every real
        // scan" gap already fixed once here for declaredMicronutrients. OFF uses
        // "vitamin-pp" (vitamine PP, the French pharmacopoeia name) for niacin/B3.
        b1Mg          = numOrNull(nm["vitamin-b1_100g"])?.times(1000),
        b2Mg          = numOrNull(nm["vitamin-b2_100g"])?.times(1000),
        b3Mg          = numOrNull(nm["vitamin-pp_100g"])?.times(1000),
        b9Ug          = numOrNull(nm["vitamin-b9_100g"])?.times(1_000_000),
        omega3G       = numOrNull(nm["omega-3-fat_100g"]),
        // Previously never mapped from OFF at all, despite caffeine being a real
        // hypertension risk factor (see PersonalScoreEngine's checkHealthConditions)
        // — every caffeinated soda/energy drink read as "safe" for hypertension
        // purely because this app never had the data to check, not because the
        // product actually is low-caffeine. OFF stores it in grams like the other
        // minerals above.
        caffeineMg    = numOrNull(nm["caffeine_100g"])?.times(1000),
    )

    return Product(
        name      = name,
        category  = category,
        novaClass = NovaClass.fromInt(off.novaGroup ?: 4),
        ingredients = ingredients,
        nutrition = nutrition,
        weightG            = parseWeightG(off.quantity),
        origin             = off.origins?.takeIf { it.isNotBlank() },
        organic            = organic,
        ecoscoreGrade      = off.ecoscoreGrade?.lowercase()?.takeIf { it.matches(Regex("[a-e]")) },
        ecoscoreValue      = off.ecoscoreScore?.toDouble(),
        nutriscoreGrade    = off.nutritionGrades?.lowercase()?.firstOrNull()?.toString()?.takeIf { it.matches(Regex("[a-e]")) },
        declaredAllergenTags = off.allergensTags.orEmpty(),
        // Previously never populated by any real mapper (only mergeOffWithLlm()
        // forwarded it, from two already-empty lists) - the SEX/iron personal-
        // score bonus and ProductHints' "Declared micronutrients" line were both
        // structurally dead for every real scan. A non-null value here means OFF's
        // own nutriments table explicitly carried that micronutrient for this
        // product, i.e. it really is declared on the label, not merely absent.
        declaredMicronutrients = declaredMicronutrientsOf(nutrition),
    )
}

/** Which of [n]'s micronutrient fields OFF/LLM actually declared a value for. */
internal fun declaredMicronutrientsOf(n: NutritionPer100g): List<String> = buildList {
    if (n.ironMg != null) add("iron")
    if (n.calciumMg != null) add("calcium")
    if (n.magnesiumMg != null) add("magnesium")
    if (n.potassiumMg != null) add("potassium")
    if (n.zincMg != null) add("zinc")
    if (n.vitAUg != null) add("vitaminA")
    if (n.vitCMg != null) add("vitaminC")
    if (n.vitDUg != null) add("vitaminD")
    if (n.vitEMg != null) add("vitaminE")
    if (n.vitKUg != null) add("vitaminK")
    if (n.b12Ug != null) add("vitaminB12")
    if (n.b6Mg != null) add("vitaminB6")
    // b1/b2/b3/b9/omega3/caffeine were mapped from OFF (see mapOffProduct) but never
    // threaded through here - the same "structurally dead" gap this function's own
    // history already fixed once for iron/calcium/etc. A product declaring only these
    // fields reported an empty declaredMicronutrients list despite the data being
    // present and used elsewhere (ProductHintsBenefitsRisks' own benefit lines).
    if (n.b1Mg != null) add("vitaminB1")
    if (n.b2Mg != null) add("vitaminB2")
    if (n.b3Mg != null) add("vitaminB3")
    if (n.b9Ug != null) add("vitaminB9")
    if (n.omega3G != null) add("omega3")
    if (n.caffeineMg != null) add("caffeine")
}

// isOffSparse() moved to OffSparsityCheck.kt
// mergeOffWithLlm(), SourceConflict, detectSourceConflicts() moved to OffMerge.kt
