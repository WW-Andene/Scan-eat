package fr.scanneat.shared

import fr.scanneat.service.OffProductRaw
import kotlinx.serialization.json.*

// ============================================================================
// SERVER OFF MAPPER
// Converts the raw OFF API response to the domain Product model.
// Mirrors OffMapper.kt from the Android project — kept in sync manually.
// ============================================================================

private fun numOf(v: JsonElement?): Double = when (v) {
    is JsonPrimitive -> v.doubleOrNull ?: 0.0
    else -> 0.0
}

private fun numOrNull(v: JsonElement?): Double? = when (v) {
    is JsonPrimitive -> v.doubleOrNull
    else -> null
}

private fun mapCategory(tags: List<String>?): ProductCategory {
    val tag = tags?.firstOrNull() ?: return ProductCategory.OTHER
    return when {
        "yogurt" in tag || "yaourt" in tag || "skyr" in tag -> ProductCategory.YOGURT
        "cheese" in tag || "fromage" in tag -> ProductCategory.CHEESE
        "sandwich" in tag || "burger" in tag -> ProductCategory.SANDWICH
        "cereal" in tag || "granola" in tag -> ProductCategory.BREAKFAST_CEREAL
        "bread" in tag || "pain" in tag -> ProductCategory.BREAD
        "processed-meat" in tag || "charcuterie" in tag -> ProductCategory.PROCESSED_MEAT
        "meat" in tag || "viande" in tag -> ProductCategory.FRESH_MEAT
        "fish" in tag || "poisson" in tag -> ProductCategory.FISH
        "biscuit" in tag || "chocolate" in tag || ("snack" in tag && "sweet" in tag) -> ProductCategory.SNACK_SWEET
        "chips" in tag || "snack" in tag -> ProductCategory.SNACK_SALTY
        "beverage" in tag && "juice" in tag -> ProductCategory.BEVERAGE_JUICE
        "beverage" in tag && "water" in tag -> ProductCategory.BEVERAGE_WATER
        "beverage" in tag || "soda" in tag || "boisson" in tag -> ProductCategory.BEVERAGE_SOFT
        "sauce" in tag || "condiment" in tag -> ProductCategory.CONDIMENT
        "oil" in tag || "fat" in tag || "huile" in tag -> ProductCategory.OIL_FAT
        "ready-meal" in tag || "plat-prepare" in tag -> ProductCategory.READY_MEAL
        else -> ProductCategory.OTHER
    }
}

private fun parseIngredients(text: String?): List<Ingredient> {
    if (text.isNullOrBlank()) return emptyList()
    return text.split(Regex("""[,;]\s*(?![^(]*\))"""))
        .mapNotNull { raw ->
            val name = raw.trim().trim('*', ' ')
            if (name.isBlank()) return@mapNotNull null
            val eNum = Regex("""[Ee](\d{3}[a-zA-Z]?)""").find(name)?.let { "E${it.groupValues[1]}" }
            Ingredient(name = name, eNumber = eNum)
        }
        .filter { it.name.length > 1 }
}

private fun parseWeightG(quantity: String?): Double? {
    if (quantity.isNullOrBlank()) return null
    val m = Regex("""(\d+(?:[.,]\d+)?)\s*(g|kg|ml|l)\b""", RegexOption.IGNORE_CASE).find(quantity) ?: return null
    val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
    return when (m.groupValues[2].lowercase()) { "kg", "l" -> v * 1000; else -> v }
}

fun mapOffProduct(raw: OffProductRaw): Product? {
    val nm = raw.nutriments ?: return null
    val name = (raw.productNameFr ?: raw.productName ?: raw.genericNameFr ?: "").trim()
        .takeIf { it.isNotEmpty() } ?: return null

    val ingredients = parseIngredients(raw.ingredientsTextFr ?: raw.ingredientsText)
    val organic = raw.labelsTags?.any { "organic" in it || "bio" in it } == true
    val category = mapCategory(raw.categoriesTags).let {
        if (it == ProductCategory.OTHER) inferCategoryFromName(name) else it
    }

    return Product(
        name        = name,
        category    = category,
        novaClass   = NovaClass.fromInt(raw.novaGroup ?: 4),
        ingredients = ingredients,
        nutrition   = NutritionPer100g(
            energyKcal    = numOf(nm["energy-kcal_100g"] ?: nm["energy_100g"]),
            fatG          = numOf(nm["fat_100g"]),
            saturatedFatG = numOf(nm["saturated-fat_100g"]),
            carbsG        = numOf(nm["carbohydrates_100g"]),
            sugarsG       = numOf(nm["sugars_100g"]),
            addedSugarsG  = numOrNull(nm["added-sugars_100g"]),
            fiberG        = numOf(nm["fiber_100g"]),
            proteinG      = numOf(nm["proteins_100g"]),
            saltG         = numOf(nm["salt_100g"]),
            transFatG     = numOrNull(nm["trans-fat_100g"]),
            ironMg        = numOrNull(nm["iron_100g"])?.times(1000),
            calciumMg     = numOrNull(nm["calcium_100g"])?.times(1000),
            magnesiumMg   = numOrNull(nm["magnesium_100g"])?.times(1000),
            potassiumMg   = numOrNull(nm["potassium_100g"])?.times(1000),
            zincMg        = numOrNull(nm["zinc_100g"])?.times(1000),
            vitDUg        = numOrNull(nm["vitamin-d_100g"])?.times(1_000_000),
            vitCMg        = numOrNull(nm["vitamin-c_100g"])?.times(1000),
            b12Ug         = numOrNull(nm["vitamin-b12_100g"])?.times(1_000_000),
        ),
        weightG         = parseWeightG(raw.quantity),
        origin          = raw.origins?.takeIf { it.isNotBlank() },
        organic         = organic,
        ecoscoreGrade   = raw.ecoscoreGrade?.lowercase()?.takeIf { it.matches(Regex("[a-e]")) },
        ecoscoreValue   = raw.ecoscoreScore?.toDouble(),
        nutriscoreGrade = raw.nutritionGrades?.lowercase()?.firstOrNull()?.toString(),
    )
}

fun isOffSparse(p: Product): Boolean {
    val n = p.nutrition
    val hasNutrition   = n.energyKcal > 0 || n.proteinG > 0 || n.carbsG > 0
    val hasIngredients = p.ingredients.size >= 3
    val hasCategory    = p.category != ProductCategory.OTHER
    val hasMicros      = listOfNotNull(n.ironMg, n.calciumMg, n.vitDUg, n.b12Ug).any { it > 0 }
    return !hasNutrition || !hasIngredients || !hasCategory || !hasMicros
}

fun mergeOffWithLlm(off: Product, llm: Product): Product {
    fun <T> prefer(o: T, l: T, empty: (T) -> Boolean): T = if (empty(o)) l else o
    val emptyNum: (Double) -> Boolean = { it == 0.0 }
    val emptyStr: (String) -> Boolean = { it.isBlank() }
    val o = off.nutrition; val l = llm.nutrition
    return Product(
        name        = prefer(off.name, llm.name, emptyStr),
        category    = if (off.category != ProductCategory.OTHER) off.category else llm.category,
        novaClass   = if (off.novaClass.value > 0) off.novaClass else llm.novaClass,
        ingredients = if (off.ingredients.size >= 3) off.ingredients else llm.ingredients,
        nutrition   = NutritionPer100g(
            energyKcal    = prefer(o.energyKcal, l.energyKcal, emptyNum),
            fatG          = prefer(o.fatG, l.fatG, emptyNum),
            saturatedFatG = prefer(o.saturatedFatG, l.saturatedFatG, emptyNum),
            carbsG        = prefer(o.carbsG, l.carbsG, emptyNum),
            sugarsG       = prefer(o.sugarsG, l.sugarsG, emptyNum),
            addedSugarsG  = o.addedSugarsG ?: l.addedSugarsG,
            fiberG        = prefer(o.fiberG, l.fiberG, emptyNum),
            proteinG      = prefer(o.proteinG, l.proteinG, emptyNum),
            saltG         = prefer(o.saltG, l.saltG, emptyNum),
            transFatG     = o.transFatG ?: l.transFatG,
            ironMg        = o.ironMg ?: l.ironMg,
            calciumMg     = o.calciumMg ?: l.calciumMg,
            magnesiumMg   = o.magnesiumMg ?: l.magnesiumMg,
            potassiumMg   = o.potassiumMg ?: l.potassiumMg,
            zincMg        = o.zincMg ?: l.zincMg,
            vitDUg        = o.vitDUg ?: l.vitDUg,
            vitCMg        = o.vitCMg ?: l.vitCMg,
            b12Ug         = o.b12Ug ?: l.b12Ug,
            omega3G       = o.omega3G ?: l.omega3G,
        ),
        weightG     = off.weightG ?: llm.weightG,
        origin      = off.origin ?: llm.origin,
        organic     = off.organic || llm.organic,
        declaredMicronutrients = (off.declaredMicronutrients + llm.declaredMicronutrients).distinct(),
        ecoscoreGrade   = off.ecoscoreGrade,
        ecoscoreValue   = off.ecoscoreValue,
        nutriscoreGrade = off.nutriscoreGrade,
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
    return conflicts
}
