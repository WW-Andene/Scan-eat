package app.scaneat

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

object ScoringEngine {
    const val ENGINE_VERSION = "kotlin-0.5.0-findings-complete"

    private val additiveDatabase = setOf(
        "E249", "E250", "E251", "E252", "E433", "E466", "E338", "E450", "E451", "E452",
        "E102", "E110", "E122", "E124", "E129", "E320", "E321", "E951", "E150C", "E150D",
        "E150", "E407", "E471", "E621", "E316", "E100", "E160C", "E171", "E220", "E221",
        "E223", "E224", "E385", "E211", "E212", "E950", "E955", "E954", "E952", "E104",
        "E127", "E173", "E339", "E340", "E341", "E1520", "E330", "E300", "E322", "E415",
        "E412", "E440", "E202", "E270", "E296", "E500", "E551", "E422", "E960", "E472",
        "E1422", "E1442", "E210", "E475", "E968", "E905", "E307", "E331", "E333", "E965",
        "E967", "E150A", "E150B", "E101", "E160A", "E163", "E170", "E260", "E325", "E327",
        "E332", "E460", "E509", "E575", "E627", "E631", "E635", "E901", "E920",
    )

    private val highConcernAdditives = setOf(
        "E102", "E110", "E122", "E124", "E129", "E171", "E249", "E250", "E251", "E252", "E320", "E321",
    )

    private val sugarWords = Regex("sucre|sirop|glucose|fructose|dextrose|maltodextrine", RegexOption.IGNORE_CASE)
    private val animalWords = Regex("lait|fromage|beurre|cr[eè]me|yaourt|œufs?|oeufs?|viande|poulet|porc|bœuf|boeuf|poisson|g[eé]latine|miel", RegexOption.IGNORE_CASE)
    private val porkWords = Regex("porc|jambon|lardon|bacon|saindoux|g[eé]latine", RegexOption.IGNORE_CASE)
    private val alcoholWords = Regex("alcool|vin|bi[eè]re|rhum|liqueur", RegexOption.IGNORE_CASE)
    private val wholeFoodWords = Regex("tomate|carotte|pomme|lentille|haricot|pois|riz|avoine|oeuf|œuf|poisson|poulet|lait|yaourt|eau", RegexOption.IGNORE_CASE)

    private val allergenPatterns = linkedMapOf(
        "gluten" to Regex("bl[eé]s?|wheat|\\borges?\\b|barley|seigle|rye|avoine|oats|épeautre|epeautre|kamut|gluten", RegexOption.IGNORE_CASE),
        "lait" to Regex("\\b(lait|laits|milk|lactose|cas[eé]ine|whey|petit[- ]lait|beurre|cr[eè]me|fromage|yaourts?)\\b", RegexOption.IGNORE_CASE),
        "arachide" to Regex("\\b(arachide|arachides|peanut|peanuts)\\b", RegexOption.IGNORE_CASE),
        "fruits à coque" to Regex("\\b(noix|amande|amandes|noisette|noisettes|cajou|pistache|pistaches|pecan|pécan|macadamia|nut|nuts)\\b", RegexOption.IGNORE_CASE),
        "œuf" to Regex("œufs?|oeufs?|\\beggs?\\b|albumine", RegexOption.IGNORE_CASE),
        "poisson" to Regex("\\b(poisson|poissons|fish|anchois|thon|saumon|cabillaud)s?\\b", RegexOption.IGNORE_CASE),
        "crustacés" to Regex("\\b(crustac[eé]|crustac[eé]s|crevette|crevettes|crabe|homard|langoustine|shrimp|crab|lobster)\\b", RegexOption.IGNORE_CASE),
        "mollusques" to Regex("\\b(mollusque|mollusques|moule|moules|hu[iî]tre|palourde|calamar|squid|oyster)\\b", RegexOption.IGNORE_CASE),
        "soja" to Regex("\\b(soja|soy|soya|lecithine de soja|l[eé]cithine de soja)\\b", RegexOption.IGNORE_CASE),
        "moutarde" to Regex("\\b(moutarde|moutardes|mustard)\\b", RegexOption.IGNORE_CASE),
        "céleri" to Regex("\\b(c[eé]leri|celeri|celery)\\b", RegexOption.IGNORE_CASE),
        "sésame" to Regex("\\b(s[eé]same|sesame)\\b", RegexOption.IGNORE_CASE),
        "lupin" to Regex("\\b(lupin|lupins)\\b", RegexOption.IGNORE_CASE),
        "sulfites" to Regex("\\b(sulfite|sulfites|sulphite|sulphites|so2|dioxyde de soufre|anhydride sulfureux)\\b", RegexOption.IGNORE_CASE),
    )

    fun score(product: ProductInput, preferences: UserPreferences = UserPreferences()): ScoreAudit {
        val processing = (24 - product.nova_class.coerceIn(1, 4) * 5).coerceIn(0, 20)
        val density = densityScore(product.nutrition, product.category)
        val negatives = negativeScore(product.nutrition, product.category)
        val additives = additiveScore(product.ingredients)
        val integrity = integrityScore(product)
        val pillars = linkedMapOf(
            "processing" to processing,
            "density" to density,
            "negative_nutrients" to negatives,
            "additives" to additives,
            "ingredient_integrity" to integrity,
        )

        val allergens = detectAllergens(product.ingredients)
        val personal = personalScore(product, allergens, preferences)
        val warnings = warnings(product, allergens, personal)
        val positives = positives(product)
        val rawTotal = pillars.values.sum().coerceIn(0, 100)
        val total = personal.scoreCap?.let { rawTotal.coerceAtMost(it) } ?: rawTotal

        return ScoreAudit(
            score = total,
            grade = grade(total),
            pillars = pillars,
            positives = positives,
            warnings = warnings,
            engineVersion = ENGINE_VERSION,
            allergens = allergens,
            scoringNote = "Kotlin fallback scorer with diet, allergen, additive, nutrition, source and validation guardrails",
            source = sourceFor(product),
            confidenceScore = confidenceScore(product),
            personalScore = personal,
        )
    }

    fun detectAllergens(ingredients: List<Ingredient>): List<String> = allergenPatterns
        .filterValues { pattern -> ingredients.any { pattern.containsMatchIn(it.name) } }
        .keys
        .toList()

    fun detectSourceConflicts(primary: ProductInput, secondary: ProductInput): List<String> {
        val warnings = mutableListOf<String>()
        compareNutrition("énergie", primary.nutrition.energy_kcal, secondary.nutrition.energy_kcal, "kcal", warnings)
        compareNutrition("sucres", primary.nutrition.sugars_g, secondary.nutrition.sugars_g, "g", warnings)
        compareNutrition("sel", primary.nutrition.salt_g, secondary.nutrition.salt_g, "g", warnings)
        compareNutrition("protéines", primary.nutrition.protein_g, secondary.nutrition.protein_g, "g", warnings)
        return warnings
    }

    private fun personalScore(product: ProductInput, allergens: List<String>, preferences: UserPreferences): PersonalScore {
        val ingredientText = product.ingredients.joinToString(" ") { it.name }
        val warnings = mutableListOf<String>()
        var cap: Int? = null

        for (diet in preferences.diets.map { it.lowercase() }) {
            when (diet) {
                "gluten_free", "sans_gluten" -> if ("gluten" in allergens) cap = capWith(warnings, cap, 40, "Incompatible avec le régime sans gluten")
                "vegan", "vegetalien", "végétalien" -> if (animalWords.containsMatchIn(ingredientText)) cap = capWith(warnings, cap, 40, "Incompatible avec le régime vegan")
                "vegetarian", "vegetarien", "végétarien" -> if (Regex("viande|poulet|porc|bœuf|boeuf|poisson|g[eé]latine", RegexOption.IGNORE_CASE).containsMatchIn(ingredientText)) cap = capWith(warnings, cap, 45, "Incompatible avec le régime végétarien")
                "halal" -> if (porkWords.containsMatchIn(ingredientText) || alcoholWords.containsMatchIn(ingredientText)) cap = capWith(warnings, cap, 40, "Incompatible avec le régime halal")
                "kosher", "casher" -> if (porkWords.containsMatchIn(ingredientText)) cap = capWith(warnings, cap, 40, "Incompatible avec le régime casher")
            }
        }

        val blockedAllergens = preferences.allergens.map { it.lowercase() }.toSet()
        val matchedBlocked = allergens.filter { it.lowercase() in blockedAllergens }
        if (matchedBlocked.isNotEmpty()) {
            cap = capWith(warnings, cap, 35, "Allergène utilisateur détecté: ${matchedBlocked.joinToString(", ")}")
        }

        return PersonalScore(scoreCap = cap, warnings = warnings, matchedAllergens = matchedBlocked)
    }

    private fun capWith(warnings: MutableList<String>, current: Int?, next: Int, warning: String): Int {
        warnings.add(warning)
        return minOf(current ?: next, next)
    }

    private fun warnings(product: ProductInput, allergens: List<String>, personal: PersonalScore): List<String> = buildList {
        addAll(nutritionWarnings(product.nutrition, product.category))
        if (product.ingredients.any { sugarWords.containsMatchIn(it.name) }) add("Sucre ou sirop dans la liste d'ingrédients")
        if (product.ingredients.any { normalizeENumber(it.e_number) in highConcernAdditives }) add("Additif à surveiller")
        if (allergens.isNotEmpty()) add("Allergènes détectés: ${allergens.joinToString(", ")}")
        addAll(validateNutrition(product.nutrition))
        addAll(personal.warnings)
    }

    private fun positives(product: ProductInput): List<String> = buildList {
        if ((product.nutrition.fiber_g ?: 0.0) >= 6.0) add("Riche en fibres")
        if ((product.nutrition.protein_g ?: 0.0) >= 12.0) add("Bonne teneur en protéines")
        if (product.organic) add("Bio")
        if (product.ingredients.any { wholeFoodWords.containsMatchIn(it.name) }) add("Ingrédients simples détectés")
    }

    private fun densityScore(n: NutritionPer100g, category: String): Int {
        var score = 12
        score += when {
            (n.fiber_g ?: 0.0) >= 6.0 -> 7
            (n.fiber_g ?: 0.0) >= 3.0 -> 4
            else -> 0
        }
        val proteinHigh = if (category in setOf("yogurt", "cheese", "fresh_meat", "fish")) 8.0 else 12.0
        score += when {
            (n.protein_g ?: 0.0) >= proteinHigh -> 6
            (n.protein_g ?: 0.0) >= 6.0 -> 3
            else -> 0
        }
        return score.coerceIn(0, 25)
    }

    private fun negativeScore(n: NutritionPer100g, category: String): Int {
        val sugarRed = if (category == "breakfast_cereal") 22.5 else if (category.startsWith("beverage")) 8.0 else 15.0
        val satRed = if (category in setOf("cheese", "oil_fat")) 12.0 else 5.0
        var score = 25
        score -= penalty(n.sugars_g, 5.0, sugarRed, 8)
        score -= penalty(n.saturated_fat_g, 1.5, satRed, 7)
        score -= penalty(n.salt_g, 0.3, 1.5, 7)
        return score.coerceIn(0, 25)
    }

    private fun additiveScore(ingredients: List<Ingredient>): Int {
        val additives = ingredients.count { normalizeENumber(it.e_number) in additiveDatabase || it.category == "additive" }
        val highConcern = ingredients.count { normalizeENumber(it.e_number) in highConcernAdditives }
        return (15 - additives * 2 - highConcern * 4).coerceIn(0, 15)
    }

    private fun integrityScore(product: ProductInput): Int {
        var score = 15
        if (product.ingredients.size > 12) score -= 4
        if (product.ingredients.firstOrNull()?.name?.let { sugarWords.containsMatchIn(it) } == true) score -= 4
        if (product.ingredients.isEmpty()) score -= 3
        if (product.organic) score += 1
        return score.coerceIn(0, 15)
    }

    private fun nutritionWarnings(n: NutritionPer100g, category: String) = buildList {
        if ((n.sugars_g ?: 0.0) > if (category.startsWith("beverage")) 8.0 else 15.0) add("Sucres élevés")
        if ((n.saturated_fat_g ?: 0.0) > if (category in setOf("cheese", "oil_fat")) 12.0 else 5.0) add("Graisses saturées élevées")
        if ((n.salt_g ?: 0.0) > 1.5) add("Sel élevé")
    }

    private fun validateNutrition(n: NutritionPer100g): List<String> {
        val kcal = n.energy_kcal ?: return emptyList()
        val macro = (n.protein_g ?: 0.0) * 4 + (n.carbs_g ?: 0.0) * 4 + (n.fat_g ?: 0.0) * 9
        if (macro <= 0.0) return emptyList()
        return if ((macro - kcal).absoluteValue > kcal * 0.10) {
            listOf("Énergie et macros divergent (${macro.roundToInt()} vs ${kcal.roundToInt()} kcal/100g). Vérifiez la source.")
        } else emptyList()
    }

    private fun compareNutrition(label: String, a: Double?, b: Double?, unit: String, warnings: MutableList<String>) {
        if (a == null || b == null) return
        val tolerance = maxOf(2.0, a.absoluteValue * 0.15)
        if ((a - b).absoluteValue > tolerance) warnings.add("Conflit de source sur $label: $a $unit vs $b $unit")
    }

    private fun sourceFor(product: ProductInput): ScoringSource = when {
        product.barcode != null && product.ingredients.isNotEmpty() -> ScoringSource.OFF_DATABASE
        product.ingredients.isNotEmpty() -> ScoringSource.MANUAL_ENTRY
        else -> ScoringSource.FALLBACK
    }

    private fun confidenceScore(product: ProductInput): Float = when {
        product.ingredients.isNotEmpty() && product.nutrition.energy_kcal != null -> 0.9f
        product.ingredients.isNotEmpty() || product.nutrition.energy_kcal != null -> 0.65f
        else -> 0.35f
    }

    private fun normalizeENumber(value: String?) = value?.uppercase()?.replace(" ", "")

    private fun penalty(value: Double?, green: Double, red: Double, max: Int): Int = when {
        value == null || value <= green -> 0
        value >= red -> max
        else -> (((value - green) / (red - green)) * max).roundToInt()
    }

    private fun grade(score: Int): String = when {
        score >= 85 -> "A+"
        score >= 70 -> "A"
        score >= 55 -> "B"
        score >= 40 -> "C"
        score >= 25 -> "D"
        else -> "F"
    }
}
