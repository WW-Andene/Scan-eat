package app.scaneat

import kotlin.math.absoluteValue
import kotlin.math.roundToInt

object ScoringEngine {
    const val ENGINE_VERSION = "kotlin-0.4.0-parity-guardrails"

    private val riskyAdditives = setOf("E249","E250","E251","E252","E433","E466","E338","E450","E451","E452","E102","E110","E122","E124","E129","E320","E321","E951","E150C","E150D","E150","E407","E471","E621","E316","E100","E160C","E171","E220","E221","E223","E224","E385","E211","E212","E950","E955","E954","E952","E104","E127","E173","E339","E340","E341","E1520","E330","E300","E322","E415","E412","E440","E202","E270","E296","E500","E551","E422","E960","E472","E1422","E1442","E210","E475","E968","E905","E307","E331","E333","E965","E967","E150A","E150B","E101","E160A","E163","E170","E260","E325","E327","E332","E460","E509","E575","E627","E631","E635","E901","E920")
    private val sugarWords = Regex("sucre|sirop|glucose|fructose|dextrose|maltodextrine", RegexOption.IGNORE_CASE)
    private val wholeFoodWords = Regex("tomate|carotte|pomme|lentille|haricot|pois|riz|avoine|oeuf|œuf|poisson|poulet|lait|yaourt|eau", RegexOption.IGNORE_CASE)
    private val allergenPatterns = linkedMapOf(
        "gluten" to Regex("\\b(bl[eé]|ble|wheat|orge|barley|seigle|rye|avoine|oats|épeautre|epeautre|kamut|gluten)s?\\b", RegexOption.IGNORE_CASE),
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

    fun score(product: ProductInput): ScoreAudit {
        val processing = (24 - product.nova_class.coerceIn(1, 4) * 5).coerceIn(0, 20)
        val density = densityScore(product.nutrition, product.category)
        val negatives = negativeScore(product.nutrition, product.category)
        val additives = additiveScore(product.ingredients)
        val integrity = integrityScore(product)
        val pillars = linkedMapOf("processing" to processing, "density" to density, "negative_nutrients" to negatives, "additives" to additives, "ingredient_integrity" to integrity)
        val allergens = detectAllergens(product.ingredients)
        val warnings = buildList {
            addAll(nutritionWarnings(product.nutrition, product.category))
            if (product.ingredients.any { sugarWords.containsMatchIn(it.name) }) add("Sucre ou sirop dans la liste d'ingrédients")
            if (product.ingredients.any { normalizeENumber(it.e_number) in riskyAdditives }) add("Additif à surveiller")
            if (allergens.isNotEmpty()) add("Allergènes détectés: ${allergens.joinToString(", ")}")
            addAll(validateNutrition(product.nutrition))
        }
        val positives = buildList {
            if ((product.nutrition.fiber_g ?: 0.0) >= 6.0) add("Riche en fibres")
            if ((product.nutrition.protein_g ?: 0.0) >= 12.0) add("Bonne teneur en protéines")
            if (product.organic) add("Bio")
            if (product.ingredients.any { wholeFoodWords.containsMatchIn(it.name) }) add("Ingrédients simples détectés")
        }
        val total = pillars.values.sum().coerceIn(0, 100)
        return ScoreAudit(total, grade(total), pillars, positives, warnings, ENGINE_VERSION, allergens, "Kotlin fallback scorer with allergen/additive/nutrition guardrails", ScoringSource.HYBRID, confidenceScore(product))
    }

    fun detectAllergens(ingredients: List<Ingredient>): List<String> = allergenPatterns.filterValues { r -> ingredients.any { r.containsMatchIn(it.name) } }.keys.toList()

    private fun densityScore(n: NutritionPer100g, category: String): Int { var s=12; s += if ((n.fiber_g?:0.0)>=6) 7 else if ((n.fiber_g?:0.0)>=3) 4 else 0; val proteinHigh=if(category in setOf("yogurt","cheese","fresh_meat","fish")) 8.0 else 12.0; s += if ((n.protein_g?:0.0)>=proteinHigh) 6 else if ((n.protein_g?:0.0)>=6) 3 else 0; return s.coerceIn(0,25) }
    private fun negativeScore(n: NutritionPer100g, category: String): Int { val sugarRed=if(category=="breakfast_cereal") 22.5 else if(category.startsWith("beverage")) 8.0 else 15.0; val satRed=if(category in setOf("cheese","oil_fat")) 12.0 else 5.0; var s=25; s-=penalty(n.sugars_g,5.0,sugarRed,8); s-=penalty(n.saturated_fat_g,1.5,satRed,7); s-=penalty(n.salt_g,0.3,1.5,7); return s.coerceIn(0,25) }
    private fun additiveScore(ingredients: List<Ingredient>): Int { val additives=ingredients.count{it.e_number!=null||it.category=="additive"}; val risky=ingredients.count{normalizeENumber(it.e_number) in riskyAdditives}; return (15-additives*2-risky*4).coerceIn(0,15) }
    private fun integrityScore(product: ProductInput): Int { var s=15; if(product.ingredients.size>12)s-=4; if(product.ingredients.firstOrNull()?.name?.let{ first -> sugarWords.containsMatchIn(first) }==true)s-=4; if(product.ingredients.isEmpty())s-=3; if(product.organic)s+=1; return s.coerceIn(0,15) }
    private fun nutritionWarnings(n: NutritionPer100g, category: String) = buildList { if((n.sugars_g?:0.0) > if(category.startsWith("beverage")) 8.0 else 15.0) add("Sucres élevés"); if((n.saturated_fat_g?:0.0)> if(category in setOf("cheese","oil_fat")) 12.0 else 5.0) add("Graisses saturées élevées"); if((n.salt_g?:0.0)>1.5) add("Sel élevé") }
    private fun validateNutrition(n: NutritionPer100g): List<String> { val kcal=n.energy_kcal ?: return emptyList(); val macro=(n.protein_g?:0.0)*4+(n.carbs_g?:0.0)*4+(n.fat_g?:0.0)*9; if(macro<=0.0) return emptyList(); return if((macro-kcal).absoluteValue > kcal*0.2) listOf("Énergie et macros divergent (${macro.roundToInt()} vs ${kcal.roundToInt()} kcal/100g). Vérifiez la source.") else emptyList() }
    private fun confidenceScore(product: ProductInput): Float = when { product.ingredients.isNotEmpty() && product.nutrition.energy_kcal != null -> 0.9f; product.ingredients.isNotEmpty() || product.nutrition.energy_kcal != null -> 0.65f; else -> 0.35f }
    private fun normalizeENumber(value: String?) = value?.uppercase()?.replace(" ", "")
    private fun penalty(value: Double?, green: Double, red: Double, max: Int): Int = when { value == null || value <= green -> 0; value >= red -> max; else -> (((value - green) / (red - green)) * max).roundToInt() }
    private fun grade(score: Int): String = when { score >= 85 -> "A+"; score >= 70 -> "A"; score >= 55 -> "B"; score >= 40 -> "C"; score >= 25 -> "D"; else -> "F" }
}
